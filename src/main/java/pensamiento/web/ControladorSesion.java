package pensamiento.web;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import pensamiento.nucleo.Familia;
import pensamiento.nucleo.Tecnica;
import pensamiento.nucleo.puertos.RepositorioInstituciones;
import pensamiento.nucleo.puertos.RepositorioTecnica;
import pensamiento.web.seguridad.ContextoRls;
import pensamiento.web.seguridad.RespuestasSesion;
import pensamiento.web.seguridad.RespuestasSesion.ModeloEntrada;
import pensamiento.web.seguridad.RutasReservadas;
import pensamiento.web.seguridad.UsuarioSesion;

/**
 * Entrada por institución (hitos 8 y 9): /<ruta> es la pantalla "Entrar" de esa institución, con un solo formulario
 * (nombre, contraseña, "Entrar") y sin lista de nombres. Una ruta que no existe o de una institución suspendida responde
 * 404 sin cuerpo. Un intento rechazado se pinta aquí mismo (/sesion/rechazo) con su estado (401 o 429), el nombre
 * conservado y el eco. /<ruta>/sin-cuenta explica a quién pedir la cuenta, con el catálogo a la vista (RF-18).
 * /bloqueo queda como atajo a la última entrada visitada.
 */
@Controller
public class ControladorSesion {

    /** Lo que la pantalla de canje de código deja para la entrada: el nombre de quien ya puede entrar. */
    public static final String ATRIBUTO_LISTO = "entrada.lista";

    /** Lo que pinta "¿No tienes cuenta?": a quién pedirla, la ruta y el catálogo para mirar mientras tanto. */
    public record VistaSinCuenta(String ruta, String quienDaCuentas, List<Familia> familias, List<Tecnica> tecnicas) {

        public List<Tecnica> deFamilia(String codigo) {
            return tecnicas.stream().filter(t -> t.familia().equals(codigo)).toList();
        }
    }

    private final RepositorioInstituciones instituciones;
    private final RepositorioTecnica tecnicas;
    private final Pagina.Fabrica paginas;
    private final TransactionTemplate tx;
    private final pensamiento.web.aula.ServicioAula aula;

    public ControladorSesion(RepositorioInstituciones instituciones, RepositorioTecnica tecnicas, Pagina.Fabrica paginas,
                             PlatformTransactionManager gestor, pensamiento.web.aula.ServicioAula aula) {
        this.aula = aula;
        this.instituciones = instituciones;
        this.tecnicas = tecnicas;
        this.paginas = paginas;
        this.tx = new TransactionTemplate(gestor);
        this.tx.setReadOnly(true);
    }

    @GetMapping("/{ruta:[a-z0-9][a-z0-9-]{1,38}[a-z0-9]}")
    public String entrada(@PathVariable String ruta, HttpServletRequest request, HttpServletResponse response, Model modelo) {
        if (institucion(ruta).isEmpty()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return null;
        }
        Optional<UsuarioSesion> yo = ContextoRls.usuarioDeSesion();
        if (yo.isPresent()) {
            return "redirect:" + yo.get().etapa().pantalla();   // ya entró a esta institución (otra ruta la rechaza FiltroInstitucionDeSesion)
        }
        RespuestasSesion.recordarRuta(request, response, ruta);
        HttpSession sesion = request.getSession(false);
        Object listo = sesion == null ? null : sesion.getAttribute(ATRIBUTO_LISTO);
        if (sesion != null) {
            sesion.removeAttribute(ATRIBUTO_LISTO);
        }
        Optional<String> aviso = listo instanceof String nombre
                ? Optional.of("Listo, " + nombre + ": tu contraseña quedó guardada. Entra con tu nombre y tu contraseña.")
                : request.getParameter("bienvenida") != null
                ? Optional.of("Tu cuenta de administrador está lista: entra con tu nombre y tu contraseña.") : Optional.empty();
        Optional<RespuestasSesion.ClaseEnEntrada> clase = claseEnEntrada(institucion(ruta).orElseThrow(), request);
        String nombre = listo instanceof String n ? n : clase.filter(RespuestasSesion.ClaseEnEntrada::conLista)
                .map(c -> request.getParameter("nombre")).orElse("");
        return pintar(request, modelo, ruta, nombre, Optional.empty(), aviso, clase);
    }

    /**
     * Hito 10: /<ruta>?clase=CODIGO (el QR de la clase). Si la clase está abierta, la entrada dice de qué grupo es y, solo si su
     * docente la activó, muestra los nombres de ese grupo; tocar el propio vuelve aquí con ?nombre= y lista=si. Un código que
     * no sirve deja la entrada de siempre, sin lista.
     */
    private Optional<RespuestasSesion.ClaseEnEntrada> claseEnEntrada(UUID institucion, HttpServletRequest request) {
        String codigo = request.getParameter("clase");
        if (codigo == null || codigo.isBlank()) {
            return Optional.empty();
        }
        return aula.clasePorCodigo(institucion, codigo).map(c -> {
            List<String> lista = aula.listaDeClase(institucion, codigo);
            boolean conLista = "si".equals(request.getParameter("lista")) && lista.contains(request.getParameter("nombre"));
            return new RespuestasSesion.ClaseEnEntrada(codigo.strip().toUpperCase(java.util.Locale.ROOT), c.grupo(), lista, conLista);
        });
    }

    /** El rechazo de un intento (reenviado por RespuestasSesion): la entrada de nuevo, con el error, el estado y el nombre. */
    @org.springframework.web.bind.annotation.PostMapping(RespuestasSesion.RUTA_RECHAZO)
    public String rechazoDeUnIntento(HttpServletRequest request, HttpServletResponse response, Model modelo) {
        return rechazo(request, response, modelo);
    }

    /** El reenvío de una sesión cerrada desde afuera llega por GET (la petición original era una navegación). */
    @GetMapping(RespuestasSesion.RUTA_RECHAZO)
    public String rechazo(HttpServletRequest request, HttpServletResponse response, Model modelo) {
        String ruta = RutasReservadas.deInstitucion(request.getParameter("institucion")).or(() -> RespuestasSesion.rutaRecordada(request)).orElse("");
        if (ruta.isEmpty() || institucion(ruta).isEmpty()) {
            return "redirect:/";
        }
        Object estado = request.getAttribute(RespuestasSesion.ATRIBUTO_ESTADO);
        response.setStatus(estado instanceof Integer codigo ? codigo : HttpServletResponse.SC_UNAUTHORIZED);
        Optional<String> error = Optional.ofNullable(request.getAttribute(RespuestasSesion.ATRIBUTO_ERROR)).map(Object::toString);
        String nombre = request.getParameter("nombre") == null ? "" : request.getParameter("nombre");
        return pintar(request, modelo, ruta, nombre, error, Optional.empty(), institucion(ruta).flatMap(i -> claseEnEntrada(i, request)));
    }

    @GetMapping("/{ruta:[a-z0-9][a-z0-9-]{1,38}[a-z0-9]}/sin-cuenta")
    public String sinCuenta(@PathVariable String ruta, HttpServletRequest request, HttpServletResponse response, Model modelo) {
        Optional<UUID> institucion = institucion(ruta);
        Optional<String> quien = institucion.flatMap(id -> ContextoRls.con(Optional.empty(), Optional.empty(),
                () -> tx.execute(e -> instituciones.quienDaCuentas(id))));
        if (quien.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return null;
        }
        modelo.addAttribute("pagina", paginas.crear("¿No tienes cuenta?", request));
        modelo.addAttribute("v", new VistaSinCuenta(ruta, quien.get(), tecnicas.familias(), tecnicas.todas()));
        return "sin-cuenta";
    }

    /** Atajo de las versiones anteriores: lleva a la entrada de la última institución visitada, o a la raíz. */
    @GetMapping("/bloqueo")
    public String bloqueo(HttpServletRequest request) {
        return RespuestasSesion.rutaRecordada(request)
                .map(r -> "redirect:/" + r + (request.getParameter("volver") == null ? ""
                        : "?volver=" + URLEncoder.encode(RespuestasSesion.destinoSeguro(request.getParameter("volver")), StandardCharsets.UTF_8)))
                .orElse("redirect:/");
    }

    /** El formulario de la raíz: lleva a /<ruta> si tiene forma de ruta; si no, vuelve a la raíz con el aviso. */
    @GetMapping("/ir")
    public String ir(@RequestParam(name = "institucion", required = false) String escrita) {
        String ruta = escrita == null ? "" : escrita.strip().toLowerCase(java.util.Locale.ROOT);
        return RutasReservadas.deInstitucion(ruta).map(r -> "redirect:/" + r).orElse("redirect:/?ruta-invalida");
    }

    private String pintar(HttpServletRequest request, Model modelo, String ruta, String nombre, Optional<String> error, Optional<String> aviso,
                          Optional<RespuestasSesion.ClaseEnEntrada> clase) {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        String volver = RespuestasSesion.destinoSeguro(request.getParameter("volver"));
        modelo.addAttribute("pagina", paginas.crear("Entrar", request));
        modelo.addAttribute("entrada", new ModeloEntrada(token == null ? "" : token.getToken(), ruta, "/".equals(volver) ? "" : volver,
                nombre, error, aviso, false, Optional.empty(), clase));
        return "bloqueo";
    }

    private Optional<UUID> institucion(String ruta) {
        return RutasReservadas.deInstitucion(ruta).flatMap(r ->
                ContextoRls.con(Optional.empty(), Optional.empty(), () -> tx.execute(e -> instituciones.activaPorRuta(r))));
    }
}
