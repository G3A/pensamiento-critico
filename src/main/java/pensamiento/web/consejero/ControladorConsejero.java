package pensamiento.web.consejero;

import java.net.URI;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;
import io.github.wimdeblauwe.htmx.spring.boot.mvc.HtmxRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import pensamiento.catalogo.MapeadorJson;
import pensamiento.flujos.Consejero;
import pensamiento.nucleo.Contexto;
import pensamiento.nucleo.Ejecucion;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Json;
import pensamiento.nucleo.SesionConsejero;
import pensamiento.nucleo.Tecnica;
import pensamiento.nucleo.TurnoConsejero;
import pensamiento.nucleo.puertos.Clasificacion;
import pensamiento.nucleo.puertos.Ia;
import pensamiento.nucleo.puertos.RepositorioEjecucion;
import pensamiento.nucleo.puertos.RepositorioExpediente;
import pensamiento.nucleo.puertos.RepositorioSesiones;
import pensamiento.nucleo.puertos.RepositorioTecnica;
import pensamiento.tecnicas.comun.Textos;
import pensamiento.tecnicas.f6.ResultadoSteelman;
import pensamiento.web.ObjetoNoEncontrado;
import pensamiento.tecnicas.f8.EjecutorReflexion;
import pensamiento.web.Pagina;
import pensamiento.web.formulario.LectorFormulario;
import pensamiento.web.patrones.Modo;
import pensamiento.web.patrones.Renderizadores;
import pensamiento.web.seguridad.ContextoRls;
import pensamiento.web.seguridad.UsuarioSesion;
import pensamiento.web.tecnicas.ControladorTecnicas;
import pensamiento.web.tecnicas.MotorTecnicas;
import pensamiento.web.tecnicas.TurnosIa;
import pensamiento.web.tecnicas.VistaFormulario;

/**
 * Flujo C · Consejero socrático (P15 y P16). La sesión es un diálogo: cada respuesta de la persona se guarda y el motor
 * elige el turno siguiente; si la sesión usa el modelo y está disponible, el turno del Consejero llega por SSE (una
 * conexión por turno, texto provisional y un evento final con la versión validada) y, si no, sale del banco en la misma
 * respuesta. El modelo se llama fuera de toda transacción; lo que deja se guarda después, dentro del contexto RLS de la
 * persona. Cerrar guarda la ejecución de la técnica del modo en el expediente de la sesión.
 */
@Controller
public class ControladorConsejero {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final List<String> MODOS = List.of("ensayo", "decision", "escalera", "sombreros", "debate");
    private static final List<String> APOYOS = List.of("no_se", "lo_dijo_alguien", "un_caso", "comparacion", "causa", "consecuencia", "mayoria", "senal",
            "definicion");

    private final Consejero consejero;
    private final RepositorioSesiones sesiones;
    private final MotorTecnicas motor;
    private final RepositorioTecnica tecnicas;
    private final RepositorioExpediente expedientes;
    private final RepositorioEjecucion ejecuciones;
    private final TurnosIa turnos;
    private final TemplateEngine plantillas;
    private final Pagina.Fabrica paginas;
    private final Renderizadores renderizadores;
    private final TransactionTemplate tx;

    public ControladorConsejero(Consejero consejero, RepositorioSesiones sesiones, MotorTecnicas motor, RepositorioTecnica tecnicas,
                                RepositorioExpediente expedientes, RepositorioEjecucion ejecuciones, TurnosIa turnos, TemplateEngine plantillas,
                                Pagina.Fabrica paginas, Renderizadores renderizadores, PlatformTransactionManager transacciones) {
        this.consejero = consejero;
        this.sesiones = sesiones;
        this.motor = motor;
        this.tecnicas = tecnicas;
        this.expedientes = expedientes;
        this.ejecuciones = ejecuciones;
        this.turnos = turnos;
        this.plantillas = plantillas;
        this.paginas = paginas;
        this.renderizadores = renderizadores;
        this.tx = new TransactionTemplate(transacciones);
    }

    // ---------------------------------------------------------------------------------------------
    // P15 al entrar: historial y sesión nueva
    // ---------------------------------------------------------------------------------------------

    @GetMapping("/consejero")
    @Transactional(readOnly = true)
    public String inicio(@RequestParam(required = false) String modo, @RequestParam(required = false) String postura, HttpServletRequest request,
                         Model modelo) {
        // Desde las posturas sin revisar de P20 se llega con el modo debate y la postura ya escritos.
        VistaConsejero.Valores vacios = VistaConsejero.Valores.vacios();
        String modoInicial = modo != null && List.of("ensayo", "decision", "escalera", "sombreros", "debate").contains(modo) ? modo : vacios.modo();
        String posturaInicial = postura == null ? "" : postura.strip().substring(0, Math.min(postura.strip().length(), 300));
        return pintarInicio("", new VistaConsejero.Valores(modoInicial, posturaInicial, vacios.razones(), vacios.apoyos(), vacios.confianza(),
                vacios.expediente(), vacios.usaModelo()), request, modelo);
    }

    private String pintarInicio(String error, VistaConsejero.Valores valores, HttpServletRequest request, Model modelo) {
        UsuarioSesion yo = paginas.usuarioActual();
        ZoneId zona = ZoneId.systemDefault();
        List<VistaConsejero.Resumen> resumen = consejero.historial(yo.id()).stream().map(s -> new VistaConsejero.Resumen(s.id(), s.postura(),
                VistaConsejero.modo(s.modo()), s.cerrada() ? "cerrada" : "abierta", FECHA.format(s.creadaEn().atZone(zona)))).toList();
        modelo.addAttribute("pagina", paginas.crear("Consejero", request));
        modelo.addAttribute("v", new VistaConsejero.Inicio(resumen, expedientes.deUsuario(yo.id()), motor.modeloDisponible(), error, valores));
        return "consejero";
    }

    @PostMapping("/consejero/sesiones")
    @Transactional
    public Object nueva(@RequestParam MultiValueMap<String, String> p, HtmxRequest htmx, HttpServletRequest request, HttpServletResponse respuesta,
                        Model modelo) {
        UsuarioSesion yo = paginas.usuarioActual();
        String modoTexto = Optional.ofNullable(p.getFirst("modo")).orElse("");
        List<String> razones = new ArrayList<>();
        List<String> apoyos = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            razones.add(Optional.ofNullable(p.getFirst("razon" + i)).orElse(""));
            apoyos.add(Optional.ofNullable(p.getFirst("apoyo" + i)).filter(APOYOS::contains).orElse("no_se"));
        }
        VistaConsejero.Valores valores = new VistaConsejero.Valores(modoTexto, Optional.ofNullable(p.getFirst("postura")).orElse(""), razones, apoyos,
                Optional.ofNullable(p.getFirst("confianza")).orElse(""), Optional.ofNullable(p.getFirst("expediente")).orElse(""),
                "on".equals(p.getFirst("usaModelo")) || "true".equals(p.getFirst("usaModelo")));
        if (!MODOS.contains(modoTexto)) {
            respuesta.setStatus(422);
            return pintarInicio("Elige el modo de la sesión.", valores, request, modelo);
        }
        Optional<Integer> confianza;
        try {
            confianza = Textos.vacio(valores.confianza()) ? Optional.empty() : Optional.of(Integer.parseInt(valores.confianza().strip()));
        } catch (NumberFormatException e) {
            respuesta.setStatus(422);
            return pintarInicio("La confianza es un número de 0 a 100.", valores, request, modelo);
        }
        Optional<UUID> expediente = Optional.empty();
        if (!Textos.vacio(valores.expediente())) {
            try {
                expediente = Optional.of(UUID.fromString(valores.expediente()));
            } catch (IllegalArgumentException e) {
                throw new ObjetoNoEncontrado("expediente");
            }
            if (expedientes.porId(yo.id(), expediente.get()).isEmpty()) {
                throw new ObjetoNoEncontrado("expediente");
            }
        }
        SesionConsejero.Modo modo = SesionConsejero.Modo.de(modoTexto);
        Tecnica t = tecnica(modo.tecnica());
        Json config = MapeadorJson.escribir(motor.configDeUsuario(yo.id(), t));
        List<SesionConsejero.Razon> rs = new ArrayList<>();
        if (modo == SesionConsejero.Modo.DEBATE) {
            for (int i = 0; i < 3; i++) {
                if (!Textos.vacio(razones.get(i))) {
                    rs.add(new SesionConsejero.Razon(razones.get(i).strip(), apoyos.get(i)));
                }
            }
        }
        boolean redactara = valores.usaModelo() && motor.modeloDisponible();
        try {
            Consejero.Iniciada iniciada = consejero.iniciar(yo.id(), yo.institucionId(), modo, valores.postura(), rs, config, valores.usaModelo(), confianza,
                    expediente, redactara);
            if (iniciada.primero().turno().estado() == TurnoConsejero.Estado.REDACTANDO) {
                abrirRedaccion(yo, iniciada.sesion(), iniciada.primero());
            }
            return redirigir("/consejero/sesiones/" + iniciada.sesion().id(), htmx, respuesta);
        } catch (Consejero.NoPermitido e) {
            respuesta.setStatus(422);
            return pintarInicio(e.getMessage(), valores, request, modelo);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // La sesión
    // ---------------------------------------------------------------------------------------------

    @GetMapping("/consejero/sesiones/{id}")
    @Transactional(readOnly = true)
    public String sesion(@PathVariable UUID id, HttpServletRequest request, Model modelo) {
        UsuarioSesion yo = paginas.usuarioActual();
        modelo.addAttribute("pagina", paginas.crear("Consejero", request));
        modelo.addAttribute("v", vista(yo, id, ""));
        return "consejero-sesion";
    }

    private VistaConsejero.Sesion vista(UsuarioSesion yo, UUID id, String error) {
        return vista(yo, id, error, java.util.Set.of());
    }

    /** @param aAbrir turnos cuyo trabajo con el modelo se abre al confirmar la transacción: se pintan ya con su SSE */
    private VistaConsejero.Sesion vista(UsuarioSesion yo, UUID id, String error, java.util.Set<UUID> aAbrir) {
        SesionConsejero s = sesionDe(yo, id);
        List<TurnoConsejero> lista = sesiones.turnos(yo.id(), id);
        int cola = turnos.enCursoDeOtros(yo.id());
        java.util.function.Predicate<TurnoConsejero> vivo = t -> aAbrir.contains(t.id()) || enVivo(yo, t);
        List<VistaConsejero.Burbuja> burbujas = VistaConsejero.burbujas(s, lista, vivo, cola);
        String estado = VistaConsejero.estado(s, lista);
        if ("redactando".equals(estado) && !vivo.test(lista.getLast())) {
            estado = "esperando";
        }
        Contexto ctx = motor.contexto(yo.id(), yo.institucionId());
        VistaConsejero.Panel panel = VistaConsejero.panel(s, lista, consejero.panel(s, lista, ctx, Optional.empty(), Optional.empty(), Optional.empty(), List.of()));
        Optional<gg.jte.Content> resultado = s.ejecucionId().flatMap(e -> ejecuciones.porId(yo.id(), e))
                .map(e -> renderizadores.render(tecnica(e.tecnica()), Optional.of(e.id()), "", motor.valorDe(e), Modo.LECTURA));
        List<String> comprobables = s.modo() == SesionConsejero.Modo.ESCALERA ? List.of("datos", "seleccion", "interpretacion", "supuestos") : List.of();
        EjecutorReflexion.Config t47 = reflexion(yo);
        return new VistaConsejero.Sesion(s, s.postura(), burbujas, panel, estado, motor.modeloDisponible(), resultado, comprobables,
                expedientes.deUsuario(yo.id()), error, s.expedienteId().flatMap(x -> expedientes.porId(yo.id(), x)),
                new VistaConsejero.Reflexion(t47.preguntas().stream().filter(q -> q != EjecutorReflexion.Pregunta.CAMBIO).toList(),
                        t47.obligatoriaAlCerrar()));
    }

    /** La configuración vigente de la persona en T47 · Reflexión estructurada, para las preguntas del cierre. */
    private EjecutorReflexion.Config reflexion(UsuarioSesion yo) {
        Tecnica t = tecnica(EjecutorReflexion.ID);
        return pensamiento.catalogo.MapeadorJson.mapper().convertValue(motor.configDeUsuario(yo.id(), t), EjecutorReflexion.Config.class);
    }

    private boolean enVivo(UsuarioSesion yo, TurnoConsejero t) {
        return t.estado() == TurnoConsejero.Estado.REDACTANDO
                && turnos.porClave(yo.id(), clave(t.id())).filter(x -> !x.terminado() && !x.cancelado()).isPresent();
    }

    private static String clave(UUID turnoId) {
        return "consejero:" + turnoId;
    }

    @PostMapping("/consejero/sesiones/{id}/turnos")
    @Transactional
    public String responder(@PathVariable UUID id, @RequestParam(required = false) String texto, HttpServletResponse respuesta, Model modelo) {
        UsuarioSesion yo = paginas.usuarioActual();
        SesionConsejero s = sesionDe(yo, id);
        boolean redactara = s.usaModelo() && motor.modeloDisponible();
        try {
            int antes = sesiones.turnos(yo.id(), id).size();
            Optional<Consejero.Nuevo> nuevo = consejero.responder(yo.id(), id, texto == null ? "" : texto, redactara);
            java.util.Set<UUID> aAbrir = new java.util.HashSet<>();
            nuevo.filter(n -> n.turno().estado() == TurnoConsejero.Estado.REDACTANDO).ifPresent(n -> {
                abrirRedaccion(yo, s, n);
                aAbrir.add(n.turno().id());
            });
            VistaConsejero.Sesion v = vista(yo, id, "", aAbrir);
            modelo.addAttribute("v", v);
            modelo.addAttribute("nuevas", v.burbujas().stream().filter(b -> b.numero() > antes).toList());
            return "fragmentos/consejero/respuesta";
        } catch (Consejero.NoPermitido e) {
            respuesta.setStatus(422);
            respuesta.setHeader("HX-Retarget", "#mensaje-consejero");
            respuesta.setHeader("HX-Reswap", "innerHTML");
            modelo.addAttribute("mensaje", e.getMessage());
            return "fragmentos/consejero/mensaje";
        }
    }

    @PostMapping("/consejero/sesiones/{id}/cierre")
    @Transactional
    public String irAlCierre(@PathVariable UUID id, HttpServletResponse respuesta, Model modelo) {
        UsuarioSesion yo = paginas.usuarioActual();
        sesionDe(yo, id);
        try {
            int antes = sesiones.turnos(yo.id(), id).size();
            consejero.irAlCierre(yo.id(), id);
            List<TurnoConsejero> lista = sesiones.turnos(yo.id(), id);
            modelo.addAttribute("v", vista(yo, id, ""));
            modelo.addAttribute("nuevas", VistaConsejero.burbujas(consejero.sesion(yo.id(), id), lista, t -> enVivo(yo, t), 0).stream()
                    .filter(b -> b.numero() > antes).toList());
            return "fragmentos/consejero/respuesta";
        } catch (Consejero.NoPermitido e) {
            respuesta.setStatus(422);
            respuesta.setHeader("HX-Retarget", "#mensaje-consejero");
            respuesta.setHeader("HX-Reswap", "innerHTML");
            modelo.addAttribute("mensaje", e.getMessage());
            return "fragmentos/consejero/mensaje";
        }
    }

    @PostMapping("/consejero/sesiones/{id}/cerrar")
    @Transactional
    public Object cerrar(@PathVariable UUID id, @RequestParam MultiValueMap<String, String> p, HtmxRequest htmx, HttpServletRequest request,
                         HttpServletResponse respuesta, Model modelo) {
        UsuarioSesion yo = paginas.usuarioActual();
        sesionDe(yo, id);
        Optional<Integer> despues;
        String confianza = p.getFirst("confianzaDespues");
        try {
            despues = confianza == null || Textos.vacio(confianza) ? Optional.empty() : Optional.of(Integer.parseInt(confianza.strip()));
        } catch (NumberFormatException e) {
            return mensaje("La confianza es un número de 0 a 100.", respuesta, modelo);
        }
        List<String> comprobados = Optional.ofNullable(p.get("comprobados")).orElse(List.of()).stream()
                .filter(x -> List.of("datos", "seleccion", "interpretacion", "supuestos").contains(x)).toList();
        try {
            Consejero.Reflexion preguntas = new Consejero.Reflexion(reflexion(yo), p.getFirst("r_aprendi"), p.getFirst("r_sin_claro"),
                    p.getFirst("r_distinto"), p.getFirst("r_sentimientos"), p.getFirst("r_siguiente"));
            consejero.cerrar(yo.id(), id, Optional.ofNullable(p.getFirst("reflexion")), despues, Optional.ofNullable(p.getFirst("causa")), comprobados,
                    preguntas, motor.contexto(yo.id(), yo.institucionId()));
            respuesta.setHeader("HX-Trigger", "ejecucion-guardada");
            return redirigir("/consejero/sesiones/" + id, htmx, respuesta);
        } catch (Consejero.NoPermitido e) {
            return mensaje(e.getMessage(), respuesta, modelo);
        }
    }

    /** Un 422 con el motivo, que htmx pinta en el aviso de la sesión. */
    private static String mensaje(String texto, HttpServletResponse respuesta, Model modelo) {
        respuesta.setStatus(422);
        respuesta.setHeader("HX-Retarget", "#mensaje-consejero");
        respuesta.setHeader("HX-Reswap", "innerHTML");
        modelo.addAttribute("mensaje", texto);
        return "fragmentos/consejero/mensaje";
    }

    @PostMapping("/consejero/sesiones/{id}/expediente")
    @Transactional
    public Object asociar(@PathVariable UUID id, @RequestParam(required = false) String expediente, HtmxRequest htmx, HttpServletResponse respuesta) {
        UsuarioSesion yo = paginas.usuarioActual();
        Optional<UUID> x;
        try {
            x = Textos.vacio(expediente) ? Optional.empty() : Optional.of(UUID.fromString(expediente));
            consejero.asociar(yo.id(), id, x);
        } catch (IllegalArgumentException | Consejero.NoEncontrada e) {
            throw new ObjetoNoEncontrado("expediente");
        }
        return redirigir("/consejero/sesiones/" + id, htmx, respuesta);
    }

    /** Adoptar el elemento que propuso el segundo paso: el panel se vuelve a pintar con él lleno, de origen modelo. */
    @PostMapping("/consejero/turnos/{turno}/elemento")
    @Transactional
    public String adoptar(@PathVariable UUID turno, @RequestParam UUID sesion, Model modelo) {
        UsuarioSesion yo = paginas.usuarioActual();
        SesionConsejero s = sesionDe(yo, sesion);
        TurnoConsejero t = sesiones.turnos(yo.id(), s.id()).stream().filter(x -> x.id().equals(turno) && x.elementoPropuesto().isPresent()).findFirst()
                .orElseThrow(() -> new ObjetoNoEncontrado("propuesta"));
        if (s.cerrada()) {
            throw new ObjetoNoEncontrado("propuesta");
        }
        sesiones.adoptarElemento(yo.id(), t.id());
        modelo.addAttribute("v", vista(yo, sesion, ""));
        modelo.addAttribute("oob", false);
        return "fragmentos/consejero/panel";
    }

    // ---------------------------------------------------------------------------------------------
    // El turno con el modelo, por SSE
    // ---------------------------------------------------------------------------------------------

    /**
     * Abre el trabajo que redacta el turno con el modelo (fuera de la transacción) y lo guarda al terminar. Si hay una
     * transacción en curso, el trabajo se abre cuando se confirma: así nunca busca un turno que todavía no se guardó.
     */
    private void abrirRedaccion(UsuarioSesion yo, SesionConsejero s, Consejero.Nuevo nuevo) {
        List<TurnoConsejero> previos = sesiones.turnos(yo.id(), s.id()).stream().filter(t -> t.numero() < nuevo.turno().numero()).toList();
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            abrirTrabajo(yo, s, nuevo, previos);
                        }
                    });
        } else {
            abrirTrabajo(yo, s, nuevo, previos);
        }
    }

    private void abrirTrabajo(UsuarioSesion yo, SesionConsejero s, Consejero.Nuevo nuevo, List<TurnoConsejero> previos) {
        Optional<Ia> ia = motor.contextoConIa(yo.id(), yo.institucionId()).ia();
        AtomicReference<TurnosIa.Turno> propio = new AtomicReference<>();
        Optional<TurnoConsejero> respuestaPrevia = previos.isEmpty() || previos.getLast().delConsejero() ? Optional.empty() : Optional.of(previos.getLast());
        TurnosIa.Turno t = turnos.abrir(yo.id(), clave(nuevo.turno().id()), provisional -> {
            Consejero.Redactado r = consejero.redactar(ia, nuevo.paso(), provisional);
            Optional<Clasificacion> elemento = respuestaPrevia.flatMap(rp -> consejero.extraerElemento(ia, s, previos, rp));
            return ContextoRls.conUsuario(yo.id(), yo.institucionId(), () -> tx.execute(estado -> {
                TurnosIa.Turno este = propio.get();
                List<TurnoConsejero> ahora = sesiones.turnos(yo.id(), s.id());
                boolean sigue = ahora.stream().anyMatch(x -> x.id().equals(nuevo.turno().id()) && x.estado() == TurnoConsejero.Estado.REDACTANDO);
                if (sigue && (este == null || !este.cancelado())) {
                    sesiones.completarTurno(yo.id(), nuevo.turno().id(), r.texto(), r.origen(), r.intentos(), r.modelo());
                }
                elemento.ifPresent(c -> sesiones.proponerElemento(yo.id(), respuestaPrevia.get().id(), c.etiqueta(), c.porQue()));
                return new TurnosIa.Fin(finDelTurno(yo, s.id(), nuevo.turno().id()));
            }));
        });
        propio.set(t);
    }

    /** El HTML del evento final: la burbuja validada, el panel y la entrada, estos dos fuera de banda. */
    private String finDelTurno(UsuarioSesion yo, UUID sesionId, UUID turnoId) {
        VistaConsejero.Sesion v = vista(yo, sesionId, "");
        VistaConsejero.Burbuja b = v.burbujas().stream().filter(x -> x.turnoId().equals(turnoId)).findFirst().orElseThrow();
        VistaConsejero.Burbuja lista = new VistaConsejero.Burbuja(b.sesionId(), b.turnoId(), b.numero(), b.rol(), b.autor(), b.texto(), b.chips(), b.detalle(), false, 0);
        StringOutput salida = new StringOutput();
        plantillas.render("fragmentos/consejero/burbuja.jte", Map.of("b", lista), salida);
        plantillas.render("fragmentos/consejero/panel.jte", Map.of("v", v, "oob", true), salida);
        plantillas.render("fragmentos/consejero/entrada.jte", Map.of("v", v, "oob", true), salida);
        return salida.toString();
    }

    @GetMapping(value = "/consejero/turnos/{turno}/flujo", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ResponseBody
    public SseEmitter flujo(@PathVariable UUID turno, HttpServletResponse respuesta) {
        UsuarioSesion yo = paginas.usuarioActual();
        TurnosIa.Turno t = turnos.porClave(yo.id(), clave(turno)).orElseThrow(() -> new ObjetoNoEncontrado("turno"));
        respuesta.setHeader("Cache-Control", "no-store");
        respuesta.setHeader("X-Accel-Buffering", "no");
        return turnos.conectar(t);
    }

    /** Cancelar: se deja de esperar al modelo y el turno queda con la pregunta del banco. */
    @PostMapping("/consejero/turnos/{turno}/cancelar")
    @Transactional
    public String cancelar(@PathVariable UUID turno, @RequestParam UUID sesion, Model modelo) {
        UsuarioSesion yo = paginas.usuarioActual();
        SesionConsejero s = sesionDe(yo, sesion);
        TurnoConsejero t = sesiones.turnos(yo.id(), s.id()).stream().filter(x -> x.id().equals(turno)).findFirst()
                .orElseThrow(() -> new ObjetoNoEncontrado("turno"));
        turnos.porClave(yo.id(), clave(turno)).ifPresent(x -> turnos.cancelar(yo.id(), x.id()));
        if (t.estado() == TurnoConsejero.Estado.REDACTANDO) {
            sesiones.completarTurno(yo.id(), t.id(), t.texto(), TurnoConsejero.Origen.BANCO, 0, Optional.empty());
        }
        VistaConsejero.Sesion v = vista(yo, sesion, "");
        modelo.addAttribute("v", v);
        modelo.addAttribute("b", v.burbujas().stream().filter(x -> x.turnoId().equals(turno)).findFirst().orElseThrow());
        return "fragmentos/consejero/cancelado";
    }

    // ---------------------------------------------------------------------------------------------
    // Debate: T34, T37 y T38 después del equipo rojo (P16)
    // ---------------------------------------------------------------------------------------------

    @GetMapping("/consejero/sesiones/{id}/debate")
    @Transactional(readOnly = true)
    public String debate(@PathVariable UUID id, @RequestParam(required = false) String tecnica, Model modelo) {
        UsuarioSesion yo = paginas.usuarioActual();
        SesionConsejero s = sesionDe(yo, id);
        if (s.modo() != SesionConsejero.Modo.DEBATE) {
            throw new ObjetoNoEncontrado("debate");
        }
        IdTecnica elegida = switch (Optional.ofNullable(tecnica).orElse("T34")) {
            case "T37" -> IdTecnica.de("T37");
            case "T38" -> IdTecnica.de("T38");
            default -> IdTecnica.de("T34");
        };
        Tecnica t = tecnica(elegida);
        Map<String, Object> config = motor.configDeUsuario(yo.id(), t);
        Map<String, Object> valores = new LinkedHashMap<>(ControladorTecnicas.vacio(motor.camposEntrada(t)));
        Optional<ResultadoSteelman> steelman = s.expedienteId().stream().flatMap(x -> ejecuciones.porExpediente(yo.id(), x).stream())
                .filter(e -> e.tecnica().valor().equals("T34")).findFirst().map(e -> (ResultadoSteelman) motor.valorDe(e));
        // Las filas opcionales (mínimo 0) empiezan vacías: una fila en blanco no debe bloquear el guardado.
        for (String opcional : List.of("razones", "argumentos", "cruxes")) {
            valores.computeIfPresent(opcional, (k, x) -> List.of());
        }
        switch (elegida.valor()) {
            case "T37" -> {
                valores.put("postura", "Quien no está de acuerdo con: " + s.postura());
                steelman.filter(ResultadoSteelman::tieneSteelman).ifPresent(x -> valores.put("steelman", x.steelman()));
            }
            case "T38" -> {
                valores.put("quienA", "Tú");
                valores.put("posturaA", s.postura());
                valores.put("dependeA", s.razones().stream().map(r -> (Object) Map.of("texto", r.texto())).toList());
                valores.put("quienB", "Quien piensa distinto");
                steelman.filter(ResultadoSteelman::tieneSteelman).ifPresent(x -> valores.put("posturaB", x.steelman()));
            }
            default -> {
            }
        }
        LectorFormulario.igualarCeldas(motor.camposEntrada(t), valores);
        VistaFormulario f = VistaFormulario.de(t, motor.camposConfig(t), motor.camposEntrada(t), config, valores, Map.of(), UUID.randomUUID().toString(),
                "tu configuración").conExpediente(s.expedienteId())
                .conModelo(new VistaFormulario.Modelo(motor.conModelo(t.id()).isPresent(), motor.modeloDisponible(), t.iaExperimental()));
        modelo.addAttribute("f", f);
        modelo.addAttribute("t", t);
        modelo.addAttribute("v", vista(yo, id, ""));
        return "fragmentos/consejero/debate";
    }

    // ---------------------------------------------------------------------------------------------

    private SesionConsejero sesionDe(UsuarioSesion yo, UUID id) {
        try {
            return consejero.sesion(yo.id(), id);
        } catch (Consejero.NoEncontrada e) {
            throw new ObjetoNoEncontrado("sesión");
        }
    }

    private Tecnica tecnica(IdTecnica id) {
        return tecnicas.porId(id).orElseThrow(() -> new IllegalStateException(id + " no está en el catálogo"));
    }

    private static ResponseEntity<Void> redirigir(String destino, HtmxRequest htmx, HttpServletResponse respuesta) {
        if (htmx.isHtmxRequest()) {
            respuesta.setHeader("HX-Redirect", destino);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.status(303).location(URI.create(destino)).build();
    }
}
