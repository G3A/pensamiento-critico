package pensamiento.web.diario;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import gg.jte.Content;
import io.github.wimdeblauwe.htmx.spring.boot.mvc.HtmxRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import pensamiento.expediente.ServicioExpedientes;
import pensamiento.flujos.DiarioDeDecisiones;
import pensamiento.nucleo.Ejecucion;
import pensamiento.nucleo.Expediente;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Prediccion;
import pensamiento.nucleo.Tecnica;
import pensamiento.nucleo.puertos.Reloj;
import pensamiento.nucleo.puertos.RepositorioTecnica;
import pensamiento.web.ObjetoNoEncontrado;
import pensamiento.web.Pagina;
import pensamiento.web.formulario.LectorFormulario;
import pensamiento.web.patrones.Modo;
import pensamiento.web.patrones.RenderizadorCalibracion;
import pensamiento.web.patrones.Renderizadores;
import pensamiento.web.patrones.V08;
import pensamiento.web.seguridad.UsuarioSesion;
import pensamiento.web.tecnicas.ControladorTecnicas;
import pensamiento.web.tecnicas.MotorTecnicas;
import pensamiento.web.tecnicas.VistaFormulario;

/**
 * Flujo D · Diario de decisiones y calibración (P17 y P18). El tablero muestra al entrar las revisiones que ya vencieron,
 * las decisiones abiertas con lo que dice tu historial, la curva de calibración de R05 y las resueltas. Una decisión
 * nueva es un expediente: el asistente pinta en cada paso el formulario de la técnica con lo que ya se escribió antes, y
 * "Guardar en historial" lo deja en ese expediente. Revisar fija el resultado (R05) y recalcula el tablero.
 */
@Controller
public class ControladorDiario {

    /** Lo que pinta el tablero. */
    public record VistaTablero(DiarioDeDecisiones.Tablero tablero, Optional<V08> calibracion, String error, String titulo) {
    }

    /** Un paso del asistente con sus técnicas: nombre, si ya se guardó, si se puede abrir y si es obligatoria. */
    public record PasoVista(int numero, String nombre, boolean habilitado, boolean actual, List<TecnicaVista> tecnicas) {
    }

    public record TecnicaVista(Tecnica tecnica, boolean hecha, boolean habilitada, boolean obligatoria, boolean actual, String url) {
    }

    /**
     * Lo que pinta el asistente: el paso y la técnica elegidos, su formulario y lo último guardado de esa técnica en el
     * expediente.
     *
     * @param bloqueo por qué la técnica no se puede abrir todavía; nulo si se puede
     */
    public record VistaAsistente(Expediente expediente, String titulo, List<PasoVista> pasos, PasoVista paso, Tecnica tecnica,
                                 VistaFormulario formulario, Optional<Content> ultimo, Optional<Ejecucion> ultima, String bloqueo,
                                 boolean problemaDefinido, boolean registrada, Optional<String> siguiente) {
    }

    private final DiarioDeDecisiones diario;
    private final ServicioExpedientes expedientes;
    private final RepositorioTecnica tecnicas;
    private final MotorTecnicas motor;
    private final Renderizadores renderizadores;
    private final Reloj reloj;
    private final Pagina.Fabrica paginas;

    public ControladorDiario(DiarioDeDecisiones diario, ServicioExpedientes expedientes, RepositorioTecnica tecnicas, MotorTecnicas motor,
                             Renderizadores renderizadores, Reloj reloj, Pagina.Fabrica paginas) {
        this.diario = diario;
        this.expedientes = expedientes;
        this.tecnicas = tecnicas;
        this.motor = motor;
        this.renderizadores = renderizadores;
        this.reloj = reloj;
        this.paginas = paginas;
    }

    // ---------------------------------------------------------------------------------------------
    // P17 · Tablero
    // ---------------------------------------------------------------------------------------------

    @GetMapping("/diario")
    @Transactional(readOnly = true)
    public String tablero(HttpServletRequest request, Model modelo) {
        return pintarTablero("", "", request, modelo);
    }

    private String pintarTablero(String error, String titulo, HttpServletRequest request, Model modelo) {
        UsuarioSesion yo = paginas.usuarioActual();
        DiarioDeDecisiones.Tablero t = diario.tablero(yo.id());
        Optional<V08> calibracion = t.calibracion().resueltas() == 0 ? Optional.empty()
                : Optional.of(RenderizadorCalibracion.vista(Optional.empty(), "diario", t.calibracion(), Modo.COMPLETO, "Tu calibración"));
        modelo.addAttribute("pagina", paginas.crear("Diario", request));
        modelo.addAttribute("v", new VistaTablero(t, calibracion, error, titulo));
        return "diario";
    }

    /** Nueva decisión: un expediente "Decisión: …" y el asistente en el paso 0. */
    @PostMapping("/diario/decisiones")
    @Transactional
    public Object nueva(@RequestParam(required = false) String titulo, HtmxRequest htmx, HttpServletRequest request, HttpServletResponse respuesta,
                        Model modelo) {
        UsuarioSesion yo = paginas.usuarioActual();
        if (titulo == null || titulo.isBlank()) {
            respuesta.setStatus(422);
            return pintarTablero("Escribe en una línea qué tienes que decidir.", "", request, modelo);
        }
        try {
            Expediente x = expedientes.crear(yo.id(), yo.institucionId(), DiarioDeDecisiones.nombreDeExpediente(titulo));
            return redirigir("/diario/decisiones/" + x.id(), htmx, respuesta);
        } catch (ServicioExpedientes.NombreInvalido e) {
            respuesta.setStatus(422);
            return pintarTablero("La decisión es demasiado larga: escríbela en una línea.", titulo, request, modelo);
        }
    }

    /** Revisar (R05): el resultado queda fijo; una segunda vez responde 409 y no cambia nada. */
    @PostMapping("/diario/predicciones/{id}/resolucion")
    @Transactional
    public Object resolver(@PathVariable UUID id, @RequestParam(required = false) String resultado, HtmxRequest htmx, HttpServletRequest request,
                           HttpServletResponse respuesta, Model modelo) {
        UsuarioSesion yo = paginas.usuarioActual();
        if (!"se_cumplio".equals(resultado) && !"no_se_cumplio".equals(resultado)) {
            respuesta.setStatus(422);
            return pintarTablero("Elige si la predicción se cumplió o no.", "", request, modelo);
        }
        try {
            diario.resolver(yo.id(), id, "se_cumplio".equals(resultado));
        } catch (DiarioDeDecisiones.NoEncontrada e) {
            throw new ObjetoNoEncontrado("predicción");
        } catch (Prediccion.YaResuelta e) {
            respuesta.setStatus(409);
            return pintarTablero(e.getMessage(), "", request, modelo);
        }
        respuesta.setHeader("HX-Trigger", "ejecucion-guardada");
        return redirigir("/diario", htmx, respuesta);
    }

    private static ResponseEntity<Void> redirigir(String destino, HtmxRequest htmx, HttpServletResponse respuesta) {
        if (htmx.isHtmxRequest()) {
            respuesta.setHeader("HX-Redirect", destino);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.status(303).location(URI.create(destino)).build();
    }

    // ---------------------------------------------------------------------------------------------
    // P18 y los cuatro pasos · Asistente
    // ---------------------------------------------------------------------------------------------

    @GetMapping("/diario/decisiones/{id}")
    @Transactional(readOnly = true)
    public String asistente(@PathVariable UUID id, @RequestParam(required = false) Integer paso, @RequestParam(required = false) String tecnica,
                            HtmxRequest htmx, HttpServletRequest request, Model modelo) {
        UsuarioSesion yo = paginas.usuarioActual();
        DiarioDeDecisiones.Asistente a = diario.asistente(yo.id(), id).orElseThrow(() -> new ObjetoNoEncontrado("decisión"));
        int numero = paso == null ? primerPasoPendiente(a) : Math.max(0, Math.min(4, paso));
        DiarioDeDecisiones.Asistente.Paso elegido = DiarioDeDecisiones.Asistente.PASOS.get(numero);
        IdTecnica t = elegida(elegido, tecnica, a);
        modelo.addAttribute("pagina", paginas.crear("Diario", request));
        modelo.addAttribute("a", vista(yo, a, elegido, t));
        return "diario-decision";
    }

    /** Los pasos con su estado, para refrescarlos cuando se guarda una técnica (evento ejecucion-guardada). */
    @GetMapping("/diario/decisiones/{id}/pasos")
    @Transactional(readOnly = true)
    public String pasos(@PathVariable UUID id, @RequestParam(required = false) Integer paso, @RequestParam(required = false) String tecnica, Model modelo) {
        UsuarioSesion yo = paginas.usuarioActual();
        DiarioDeDecisiones.Asistente a = diario.asistente(yo.id(), id).orElseThrow(() -> new ObjetoNoEncontrado("decisión"));
        int numero = paso == null ? 0 : Math.max(0, Math.min(4, paso));
        DiarioDeDecisiones.Asistente.Paso elegido = DiarioDeDecisiones.Asistente.PASOS.get(numero);
        modelo.addAttribute("a", vista(yo, a, elegido, elegida(elegido, tecnica, a)));
        return "fragmentos/diario/pasos";
    }

    private static int primerPasoPendiente(DiarioDeDecisiones.Asistente a) {
        return a.problemaDefinido() ? (a.registrada() ? 4 : 1) : 0;
    }

    private static IdTecnica elegida(DiarioDeDecisiones.Asistente.Paso paso, String tecnica, DiarioDeDecisiones.Asistente a) {
        if (tecnica != null) {
            try {
                IdTecnica t = IdTecnica.de(tecnica);
                if (paso.tecnicas().contains(t)) {
                    return t;
                }
            } catch (IllegalArgumentException e) {
                // Un identificador mal escrito abre la primera del paso.
            }
        }
        return paso.tecnicas().stream().filter(t -> !a.hecha(t) && a.tecnicaHabilitada(t)).findFirst().orElse(paso.tecnicas().getFirst());
    }

    private VistaAsistente vista(UsuarioSesion yo, DiarioDeDecisiones.Asistente a, DiarioDeDecisiones.Asistente.Paso paso, IdTecnica id) {
        String base = "/diario/decisiones/" + a.expediente().id();
        List<PasoVista> pasos = new ArrayList<>();
        PasoVista actual = null;
        for (DiarioDeDecisiones.Asistente.Paso p : DiarioDeDecisiones.Asistente.PASOS) {
            List<TecnicaVista> ts = new ArrayList<>();
            for (IdTecnica t : p.tecnicas()) {
                ts.add(new TecnicaVista(tecnica(t), a.hecha(t), a.tecnicaHabilitada(t), p.obligatorias().contains(t), p == paso && t.equals(id),
                        base + "?paso=" + p.numero() + "&tecnica=" + t));
            }
            PasoVista pv = new PasoVista(p.numero(), p.nombre(), a.pasoHabilitado(p.numero()), p == paso, ts);
            pasos.add(pv);
            if (p == paso) {
                actual = pv;
            }
        }
        Tecnica t = tecnica(id);
        String bloqueo = null;
        if (!a.pasoHabilitado(paso.numero())) {
            bloqueo = "Primero define el problema: elige una reformulación (T40 · Definición del problema) y escribe certezas y supuestos "
                    + "(T41 · Primeros principios).";
        } else if (!a.tecnicaHabilitada(id)) {
            bloqueo = "Antes de registrar la decisión, pasa la lista de T16 · Lista de verificación antes de decidir.";
        }
        Map<String, Object> config = motor.configDeUsuario(yo.id(), t);
        Map<String, Object> valores = new LinkedHashMap<>(ControladorTecnicas.vacio(motor.camposEntrada(t)));
        valores.putAll(a.valoresIniciales(id, reloj.hoy()));
        LectorFormulario.igualarCeldas(motor.camposEntrada(t), valores);
        VistaFormulario formulario = VistaFormulario.de(t, motor.camposConfig(t), motor.camposEntrada(t), config, valores, Map.of(),
                UUID.randomUUID().toString(), "tu configuración").conExpediente(Optional.of(a.expediente().id()));
        Optional<Ejecucion> ultima = a.ultima(id);
        Optional<Content> ultimo = ultima.map(e -> renderizadores.render(t, Optional.of(e.id()), "", motor.valorDe(e), Modo.LECTURA));
        Optional<String> siguiente = paso.numero() < 4 && a.pasoHabilitado(paso.numero() + 1)
                ? Optional.of(base + "?paso=" + (paso.numero() + 1)) : Optional.empty();
        return new VistaAsistente(a.expediente(), a.titulo(), pasos, actual, t, formulario, ultimo, ultima, bloqueo, a.problemaDefinido(),
                a.registrada(), siguiente);
    }

    private Tecnica tecnica(IdTecnica id) {
        return tecnicas.porId(id).orElseThrow(() -> new IllegalStateException(id + " no está en el catálogo"));
    }
}
