package pensamiento.web.seguridad;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.webauthn.api.AuthenticatorAssertionResponse;
import org.springframework.security.web.webauthn.api.PublicKeyCredential;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialCreationOptions;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRequestOptions;
import org.springframework.security.web.webauthn.jackson.WebauthnJacksonModule;
import org.springframework.security.web.webauthn.management.RelyingPartyPublicKey;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import pensamiento.nucleo.LlaveDeAcceso;
import pensamiento.nucleo.puertos.RegistroAuditoria;
import pensamiento.nucleo.puertos.Reloj;
import pensamiento.web.Pagina;

/**
 * Llave de acceso (hito 9): un adulto que administra (administrador, docente) y el operador del servicio confirman con su
 * llave después de la contraseña; si todavía no tienen, al entrar solo pueden registrarla, y al registrarla ven una sola
 * vez 10 códigos de recuperación. Mismo controlador en /llave (cuentas) y /operador/llave (operador). El JavaScript es
 * /llave.js (archivo propio: la CSP no admite script en línea). Las opciones de cada ceremonia viven en la sesión.
 */
@Controller
public class ControladorLlave {

    private static final String OPCIONES_REGISTRO = "llave.opciones.registro";
    private static final String OPCIONES_CONFIRMACION = "llave.opciones.confirmacion";
    private static final String CODIGOS = "llave.codigos";
    private static final String FALLOS_RECUPERACION = "llave.fallos.recuperacion";
    private static final int MAXIMO_FALLOS_RECUPERACION = 5;

    /** Lo que pinta /llave: la etapa, la base de las rutas, las llaves registradas, un error y un aviso. */
    public record VistaLlave(String base, String etapa, String nombre, List<LlaveDeAcceso> llaves, Optional<String> error, Optional<String> aviso,
                             String inicio, java.time.ZoneId zona) {

        public String fecha(java.time.Instant instante) {
            return pensamiento.web.tecnicas.Fechas.corta(instante, zona);
        }
    }

    private final ServicioLlaves servicio;
    private final Pagina.Fabrica paginas;
    private final TransactionTemplate tx;
    private final RegistroAuditoria auditoria;
    private final Reloj reloj;
    private final JsonMapper json = JsonMapper.builder().addModule(new WebauthnJacksonModule()).build();

    public ControladorLlave(ServicioLlaves servicio, Pagina.Fabrica paginas, PlatformTransactionManager gestor, RegistroAuditoria auditoria,
                            Reloj reloj) {
        this.servicio = servicio;
        this.paginas = paginas;
        this.tx = new TransactionTemplate(gestor);
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    @GetMapping({"/llave", "/operador/llave"})
    public String pagina(@RequestParam(required = false) String aviso, HttpServletRequest request, Model modelo) {
        TitularDeSesion yo = yo();
        return pintar(request, modelo, yo, Optional.empty(), Optional.ofNullable(aviso).map(a -> switch (a) {
            case "recuperacion" -> "Entraste con un código de recuperación, que ya no sirve más. Si perdiste tu llave, registra una nueva aquí.";
            case "registrada" -> "Tu llave nueva quedó registrada.";
            default -> "";
        }).filter(a -> !a.isEmpty()));
    }

    @PostMapping(value = {"/llave/registro/opciones", "/operador/llave/registro/opciones"}, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> opcionesDeRegistro(HttpServletRequest request) {
        TitularDeSesion yo = yo();
        if (!yo.lista() && !yo.debeRegistrarLlave()) {
            return ResponseEntity.status(403).build();
        }
        PublicKeyCredentialCreationOptions opciones = tx.execute(e -> servicio.opcionesDeRegistro(yo.titular(), yo.nombreVisible()));
        request.getSession().setAttribute(OPCIONES_REGISTRO, opciones);
        return ResponseEntity.ok(json.writeValueAsString(opciones));
    }

    @PostMapping(value = {"/llave/registro", "/operador/llave/registro"}, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> registrar(@RequestBody String cuerpo, HttpServletRequest request, HttpServletResponse response) {
        TitularDeSesion yo = yo();
        HttpSession sesion = request.getSession();
        Object opciones = sesion.getAttribute(OPCIONES_REGISTRO);
        sesion.removeAttribute(OPCIONES_REGISTRO);
        if (!(opciones instanceof PublicKeyCredentialCreationOptions creacion) || (!yo.lista() && !yo.debeRegistrarLlave())) {
            return ResponseEntity.status(400).body(Map.of("error", "Empieza de nuevo: toca «Registrar mi llave»."));
        }
        List<String> codigos;
        try {
            RelyingPartyPublicKey llave = json.readValue(cuerpo, RelyingPartyPublicKey.class);
            codigos = tx.execute(e -> {
                List<String> nuevos = servicio.registrar(yo.titular(), yo.nombreVisible(), creacion, llave);
                auditar(yo, RegistroAuditoria.Accion.REGISTRAR_LLAVE);
                return nuevos;
            });
        } catch (RuntimeException e) {
            return ResponseEntity.status(400).body(Map.of("error", "No se pudo registrar la llave. Vuelve a intentarlo."));
        }
        sesion.setAttribute(CODIGOS, codigos);
        if (!yo.lista()) {
            CambioDeEtapa.aplicar(request, response, yo.lista(true));
        }
        return ResponseEntity.ok(Map.of("ir", yo.baseDeLlave() + "/codigos"));
    }

    @GetMapping({"/llave/codigos", "/operador/llave/codigos"})
    public String codigos(HttpServletRequest request, Model modelo) {
        TitularDeSesion yo = yo();
        HttpSession sesion = request.getSession();
        Object codigos = sesion.getAttribute(CODIGOS);
        sesion.removeAttribute(CODIGOS);
        if (!(codigos instanceof List<?> lista)) {
            return "redirect:" + yo.inicio();
        }
        modelo.addAttribute("pagina", paginas.crear("Códigos de recuperación", request));
        modelo.addAttribute("codigos", lista.stream().map(Object::toString).toList());
        modelo.addAttribute("inicio", yo.inicio());
        return "llave-codigos";
    }

    @PostMapping(value = {"/llave/confirmacion/opciones", "/operador/llave/confirmacion/opciones"}, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> opcionesDeConfirmacion(HttpServletRequest request) {
        TitularDeSesion yo = yo();
        PublicKeyCredentialRequestOptions opciones = tx.execute(e -> servicio.opcionesDeConfirmacion(yo.titular(), yo.nombreVisible()));
        request.getSession().setAttribute(OPCIONES_CONFIRMACION, opciones);
        return ResponseEntity.ok(json.writeValueAsString(opciones));
    }

    @PostMapping(value = {"/llave/confirmacion", "/operador/llave/confirmacion"}, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> confirmar(@RequestBody String cuerpo, @RequestParam(defaultValue = "") String volver,
                                                         HttpServletRequest request, HttpServletResponse response) {
        TitularDeSesion yo = yo();
        HttpSession sesion = request.getSession();
        Object opciones = sesion.getAttribute(OPCIONES_CONFIRMACION);
        sesion.removeAttribute(OPCIONES_CONFIRMACION);
        if (!(opciones instanceof PublicKeyCredentialRequestOptions pedido)) {
            return ResponseEntity.status(400).body(Map.of("error", "Empieza de nuevo: toca «Confirmar con mi llave»."));
        }
        boolean valida;
        try {
            PublicKeyCredential<AuthenticatorAssertionResponse> firma = json.readValue(cuerpo,
                    new TypeReference<PublicKeyCredential<AuthenticatorAssertionResponse>>() { });
            valida = Boolean.TRUE.equals(tx.execute(e -> servicio.confirmar(yo.titular(), yo.nombreVisible(), pedido, firma)));
        } catch (RuntimeException e) {
            valida = false;
        }
        if (!valida) {
            return ResponseEntity.status(401).body(Map.of("error", "Esa llave no es la tuya o no se pudo verificar. Prueba otra vez o usa un código de recuperación."));
        }
        if (!yo.lista()) {
            CambioDeEtapa.aplicar(request, response, yo.lista(true));
        }
        return ResponseEntity.ok(Map.of("ir", yo.inicio()));
    }

    @PostMapping({"/llave/recuperacion", "/operador/llave/recuperacion"})
    public String recuperacion(@RequestParam(defaultValue = "") String codigo, HttpServletRequest request, HttpServletResponse response, Model modelo) {
        TitularDeSesion yo = yo();
        if (yo.lista() || yo.debeRegistrarLlave()) {
            return "redirect:" + yo.baseDeLlave();
        }
        boolean usado = Boolean.TRUE.equals(tx.execute(e -> {
            boolean ok = servicio.usarRecuperacion(yo.titular(), codigo);
            if (ok) {
                auditar(yo, RegistroAuditoria.Accion.USAR_RECUPERACION);
            }
            return ok;
        }));
        if (usado) {
            CambioDeEtapa.aplicar(request, response, yo.lista(false));
            return "redirect:" + yo.baseDeLlave() + "?aviso=recuperacion";
        }
        HttpSession sesion = request.getSession();
        int fallos = (sesion.getAttribute(FALLOS_RECUPERACION) instanceof Integer n ? n : 0) + 1;
        sesion.setAttribute(FALLOS_RECUPERACION, fallos);
        if (fallos >= MAXIMO_FALLOS_RECUPERACION) {
            sesion.invalidate();
            SecurityContextHolder.clearContext();
            return "redirect:/";
        }
        response.setStatus(422);
        return pintar(request, modelo, yo, Optional.of("Ese código de recuperación no sirve: puede estar mal escrito o ya se usó."), Optional.empty());
    }

    private String pintar(HttpServletRequest request, Model modelo, TitularDeSesion yo, Optional<String> error, Optional<String> aviso) {
        String etapa = yo.lista() ? "lista" : yo.debeRegistrarLlave() ? "registrar" : "confirmar";
        List<LlaveDeAcceso> llaves = yo.lista() ? tx.execute(e -> servicio.llaves(yo.titular())) : List.of();
        modelo.addAttribute("pagina", paginas.crear("Llave de acceso", request));
        modelo.addAttribute("v", new VistaLlave(yo.baseDeLlave(), etapa, yo.nombreVisible(), llaves, error, aviso, yo.inicio(), reloj.zona()));
        return "llave";
    }

    private void auditar(TitularDeSesion yo, RegistroAuditoria.Accion accion) {
        if (yo instanceof UsuarioSesion u) {
            auditoria.registrar(new RegistroAuditoria.Evento(Optional.of(u.id()), u.institucionId(), accion, "usuario", Optional.of(u.id()), reloj.ahora()));
        }
    }

    private static TitularDeSesion yo() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof TitularDeSesion titular) {
            return titular;
        }
        throw new IllegalStateException("Sin sesión");
    }
}
