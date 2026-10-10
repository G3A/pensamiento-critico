package pensamiento.web.tecnicas;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import gg.jte.Content;
import io.github.wimdeblauwe.htmx.spring.boot.mvc.HtmxRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import pensamiento.nucleo.Contexto;
import pensamiento.expediente.GuardadoDeEjecuciones;
import pensamiento.nucleo.Ejecucion;
import pensamiento.nucleo.Ejemplo;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Resultado;
import pensamiento.nucleo.Tecnica;
import pensamiento.nucleo.Uuid7;
import pensamiento.nucleo.puertos.Reloj;
import pensamiento.nucleo.puertos.RepositorioEjecucion;
import pensamiento.nucleo.puertos.RepositorioExpediente;
import pensamiento.nucleo.puertos.RepositorioTecnica;
import pensamiento.web.ObjetoNoEncontrado;
import pensamiento.web.Pagina;
import pensamiento.web.formulario.Campo;
import pensamiento.web.formulario.LectorFormulario;
import pensamiento.web.formulario.LenguajeCampos;
import pensamiento.web.patrones.Modo;
import pensamiento.web.patrones.Renderizadores;
import pensamiento.web.seguridad.UsuarioSesion;

/**
 * Ficha de técnica de tres pestañas (P04 a P08): Qué es, Usar (ejemplos, configuración plegable, formulario y
 * resultado) e Historial. Evaluar no abre transacción; guardar es una transacción corta.
 */
@Controller
public class ControladorTecnicas {

    public enum Pestana {
        QUE_ES("que-es", "Qué es"), USAR("usar", "Usar"), HISTORIAL("historial", "Historial");

        private final String clave;
        private final String titulo;

        Pestana(String clave, String titulo) {
            this.clave = clave;
            this.titulo = titulo;
        }

        public String clave() {
            return clave;
        }

        public String titulo() {
            return titulo;
        }

        static Pestana de(String clave) {
            for (Pestana p : values()) {
                if (p.clave.equals(clave)) {
                    return p;
                }
            }
            return QUE_ES;
        }
    }

    /** Lo que pinta la pestaña Usar. */
    /** @param datosPropios qué trae "Usar mis datos" (T45, T46, T48 y T49); vacío en las demás técnicas */
    public record VistaUsar(Tecnica tecnica, List<Ejemplo> ejemplos, Optional<Ejemplo> ejemploElegido, Optional<Content> resultadoEjemplo,
                            VistaConfiguracion configuracion, VistaFormulario formulario, Optional<String> datosPropios) {
    }

    /** El bloque plegable de configuración del usuario. */
    public record VistaConfiguracion(String tecnica, List<pensamiento.web.formulario.VistaCampo> campos, String resumen, boolean personalizada,
                                     String mensaje) {
    }

    /** Una fila del historial. */
    public record FilaHistorial(Ejecucion ejecucion, String fecha, String config) {
    }

    private final RepositorioTecnica tecnicas;
    private final RepositorioEjecucion ejecuciones;
    private final RepositorioExpediente expedientes;
    private final GuardadoDeEjecuciones guardado;
    private final MotorTecnicas motor;
    private final Renderizadores renderizadores;
    private final Reloj reloj;
    private final Pagina.Fabrica paginas;
    private final DatosPropios datosPropios;

    public ControladorTecnicas(RepositorioTecnica tecnicas, RepositorioEjecucion ejecuciones, RepositorioExpediente expedientes,
                               GuardadoDeEjecuciones guardado, MotorTecnicas motor, Renderizadores renderizadores, Reloj reloj,
                               Pagina.Fabrica paginas, DatosPropios datosPropios) {
        this.datosPropios = datosPropios;
        this.tecnicas = tecnicas;
        this.ejecuciones = ejecuciones;
        this.expedientes = expedientes;
        this.guardado = guardado;
        this.motor = motor;
        this.renderizadores = renderizadores;
        this.reloj = reloj;
        this.paginas = paginas;
    }

    // ---------------------------------------------------------------------------------------------
    // Ficha y pestañas
    // ---------------------------------------------------------------------------------------------

    @GetMapping("/tecnicas/{id}")
    @Transactional(readOnly = true)
    public String ficha(@PathVariable String id, @RequestParam(required = false) String pestana,
                        @RequestParam(required = false) UUID ejemplo, @RequestParam(required = false) UUID reejecutar,
                        @RequestParam(required = false) UUID a, @RequestParam(required = false) UUID b,
                        @RequestParam(required = false) String propios, HtmxRequest htmx, HttpServletRequest request, Model modelo) {
        Tecnica t = tecnica(id);
        boolean conPropios = "1".equals(propios);
        Pestana elegida = ejemplo != null || reejecutar != null || conPropios ? Pestana.USAR : Pestana.de(pestana);
        modelo.addAttribute("pagina", paginas.crear(t.nombreLlano(), request));
        cuerpo(t, elegida, Optional.ofNullable(ejemplo), Optional.ofNullable(reejecutar), conPropios, Optional.ofNullable(a), Optional.ofNullable(b), modelo);
        return htmx.isHtmxRequest() ? "fragmentos/ficha/cuerpo" : "ficha";
    }

    /** Todo lo que pinta la ficha; solo la pestaña elegida trae sus datos. */
    public record VistaFicha(Tecnica tecnica, String familia, Pestana pestana, boolean activa, String resumenConfig, int hito,
                             List<Relacionada> relacionadas, VistaUsar usar, List<FilaHistorial> historial,
                             ComparadorEjecuciones.Comparacion comparacion) {

        public Pestana[] pestanas() {
            return Pestana.values();
        }

        public String url(Pestana p) {
            return "/tecnicas/" + tecnica.id() + "?pestana=" + p.clave();
        }
    }

    private void cuerpo(Tecnica t, Pestana pestana, Optional<UUID> ejemplo, Optional<UUID> reejecutar, boolean propios, Optional<UUID> a,
                        Optional<UUID> b, Model modelo) {
        UsuarioSesion yo = paginas.usuarioActual();
        String familia = tecnicas.familias().stream().filter(f -> f.codigo().equals(t.familia())).map(f -> f.codigo() + " · " + f.nombre())
                .findFirst().orElse(t.familia());
        boolean activa = motor.ejecutor(t.id()).isPresent() && !t.estaPendiente();
        modelo.addAttribute("ficha", new VistaFicha(t, familia, pestana, activa, resumenConfig(yo, t), HitosDeTecnicas.hito(t.id()),
                relacionadas(t),
                pestana == Pestana.USAR && activa ? vistaUsar(yo, t, ejemplo, reejecutar, propios) : null,
                pestana == Pestana.HISTORIAL && activa ? historial(yo, t) : List.of(),
                pestana == Pestana.HISTORIAL && a.isPresent() && b.isPresent() ? comparar(yo, a.get(), b.get()) : null));
    }

    /** Qué es: técnicas relacionadas por relación tipada, con la frase desde esta técnica. */
    public record Relacionada(Tecnica tecnica, String frase) {
    }

    private List<Relacionada> relacionadas(Tecnica t) {
        List<Relacionada> lista = new ArrayList<>();
        for (var r : tecnicas.relaciones(t.id())) {
            Optional<Tecnica> otra = tecnicas.porId(r.otra(t.id()));
            otra.ifPresent(o -> lista.add(new Relacionada(o, r.origen().equals(t.id())
                    ? "Esta técnica " + r.tipo().frase() : o.id() + " " + r.tipo().frase() + " esta técnica")));
        }
        return lista;
    }

    private String resumenConfig(UsuarioSesion yo, Tecnica t) {
        if (t.estaPendiente() || motor.ejecutor(t.id()).isEmpty()) {
            return "";
        }
        return VistaFormulario.resumen(motor.camposConfig(t), motor.configDeUsuario(yo.id(), t));
    }

    private VistaUsar vistaUsar(UsuarioSesion yo, Tecnica t, Optional<UUID> ejemploId, Optional<UUID> reejecutar, boolean propios) {
        List<Ejemplo> ejemplos = tecnicas.ejemplos(t.id());
        Optional<Ejemplo> elegido = ejemploId.flatMap(id -> ejemplos.stream().filter(e -> e.id().equals(id)).findFirst());
        Map<String, Object> configUsuario = motor.configDeUsuario(yo.id(), t);
        Map<String, Object> config = configUsuario;
        Map<String, Object> valores;
        String origen = "tu configuración";
        if (elegido.isPresent()) {
            config = LenguajeCampos.mapa(elegido.get().config());
            valores = LenguajeCampos.mapa(elegido.get().datos());
            origen = "la configuración del ejemplo \"" + elegido.get().titulo() + "\"";
        } else if (propios && datosPropios.descripcion(t.id()).isPresent()) {
            valores = datosPropios.entrada(t.id(), yo.id());
            LectorFormulario.igualarCeldas(motor.camposEntrada(t), valores);
            origen = "tu configuración, con " + datosPropios.descripcion(t.id()).get();
        } else if (reejecutar.isPresent()) {
            Ejecucion anterior = ejecuciones.porId(yo.id(), reejecutar.get()).orElseThrow(() -> new ObjetoNoEncontrado("ejecución"));
            valores = LenguajeCampos.mapa(anterior.datos());
            origen = "tu configuración actual (reejecución de la del " + fecha(anterior) + ")";
        } else {
            valores = vacio(motor.camposEntrada(t));
        }
        Optional<Content> resultadoEjemplo = elegido.map(e -> {
            var ev = motor.evaluar(t, LenguajeCampos.mapa(e.config()), LenguajeCampos.mapa(e.datos()), motor.contexto(yo.id(), yo.institucionId()));
            return renderizadores.render(t, Optional.empty(), "ejemplo-" + e.orden(), ev.resultado().orElseThrow().valor(), Modo.LECTURA);
        });
        VistaFormulario formulario = VistaFormulario.de(t, motor.camposConfig(t), motor.camposEntrada(t), config, valores, Map.of(),
                nuevaClave(), origen).conModelo(modeloEn(t, config));
        return new VistaUsar(t, ejemplos, elegido, resultadoEjemplo, configuracion(yo, t, configUsuario, Map.of(), ""), formulario,
                datosPropios.descripcion(t.id()));
    }

    private VistaConfiguracion configuracion(UsuarioSesion yo, Tecnica t, Map<String, Object> config, Map<String, String> errores, String mensaje) {
        List<Campo> campos = motor.camposConfig(t);
        var vistas = pensamiento.web.formulario.ConstructorVista.para("cfg-" + t.id(), campos, config, config, errores, null)
                .construir(campos, config, "", "cfg.");
        return new VistaConfiguracion(t.id().valor(), vistas, VistaFormulario.resumen(campos, config), motor.configPersonalizada(yo.id(), t), mensaje);
    }

    /** Un formulario vacío con el mínimo de filas que pide cada campo de filas, para empezar a escribir. */
    public static Map<String, Object> vacio(List<Campo> campos) {
        Map<String, Object> valores = new LinkedHashMap<>();
        for (Campo c : campos) {
            if (c.tipo() == Campo.Tipo.FILAS) {
                List<Object> filas = new ArrayList<>();
                for (int i = 0; i < (c.minimo() == null ? 1 : Math.max(1, c.minimo())); i++) {
                    filas.add(new LinkedHashMap<String, Object>());
                }
                valores.put(c.nombre(), filas);
            }
        }
        LectorFormulario.igualarCeldas(campos, valores);
        return valores;
    }

    // ---------------------------------------------------------------------------------------------
    // Formulario: acciones de filas, evaluar y guardar
    // ---------------------------------------------------------------------------------------------

    /**
     * Añadir, quitar, subir o bajar: devuelve el mismo formulario con los valores que traía. "adoptar:IA1" adopta una
     * propuesta del modelo (una acción explícita por propuesta) y además vuelve a evaluar, para que se vea contar.
     */
    @PostMapping("/tecnicas/{id}/formulario")
    public String formulario(@PathVariable String id, @RequestParam MultiValueMap<String, String> parametros, Model modelo) {
        Tecnica t = tecnicaActiva(id);
        UsuarioSesion yo = paginas.usuarioActual();
        Map<String, Object> config = configDelFormulario(t, parametros);
        Map<String, Object> valores = LectorFormulario.leer(motor.camposEntrada(t), parametros, "");
        String accion = parametros.getFirst("_accion");
        Content resultado = null;
        if (accion != null && accion.startsWith("adoptar:")) {
            Optional<Map<String, Object>> adoptados = motor.adoptar(t, config, valores, accion.substring("adoptar:".length()));
            if (adoptados.isPresent()) {
                valores = adoptados.get();
                MotorTecnicas.Evaluacion ev = motor.evaluar(t, config, valores, motor.contexto(yo.id(), yo.institucionId()));
                if (ev.valida()) {
                    resultado = renderizadores.render(t, Optional.empty(), "borrador", ev.resultado().orElseThrow().valor(), Modo.COMPLETO);
                }
            }
        } else {
            LectorFormulario.aplicar(accion, motor.camposEntrada(t), valores, config);
        }
        VistaFormulario f = VistaFormulario.de(t, motor.camposConfig(t), motor.camposEntrada(t), config, valores, Map.of(),
                clave(parametros), origen(parametros)).conModelo(modeloEn(t, config)).conExpediente(expediente(parametros));
        modelo.addAttribute("f", f);
        modelo.addAttribute("resultadoOob", resultado);
        return "fragmentos/ficha/formulario-con-resultado";
    }

    /** Lo que el formulario dice del modelo local: si la configuración lo pide, si responde y si es experimental. */
    private VistaFormulario.Modelo modeloEn(Tecnica t, Map<String, Object> config) {
        return motor.pideModelo(t, config) ? new VistaFormulario.Modelo(true, motor.modeloDisponible(), t.iaExperimental())
                : VistaFormulario.Modelo.NINGUNO;
    }

    /** Evaluar sin guardar: endpoint sin transacción. 422 con el mismo formulario si algo falta. */
    @PostMapping("/tecnicas/{id}/evaluar")
    public String evaluar(@PathVariable String id, @RequestParam MultiValueMap<String, String> parametros,
                          HttpServletResponse respuesta, Model modelo) {
        Tecnica t = tecnicaActiva(id);
        UsuarioSesion yo = paginas.usuarioActual();
        Map<String, Object> config = configDelFormulario(t, parametros);
        Map<String, Object> valores = LectorFormulario.leer(motor.camposEntrada(t), parametros, "");
        MotorTecnicas.Evaluacion ev = motor.evaluar(t, config, valores, motor.contexto(yo.id(), yo.institucionId()));
        if (!ev.valida()) {
            return conErrores(t, config, valores, ev.errores(), parametros, respuesta, modelo);
        }
        modelo.addAttribute("resultado", renderizadores.render(t, Optional.empty(), "borrador", ev.resultado().orElseThrow().valor(), Modo.COMPLETO));
        modelo.addAttribute("tecnica", t);
        modelo.addAttribute("ejecucion", null);
        modelo.addAttribute("expedientes", List.of());
        modelo.addAttribute("nuevaClave", null);
        return "fragmentos/ficha/resultado";
    }

    /** Guardar en historial: transacción corta con ejecución, afirmaciones, pendientes y argumentos; idempotente por clave. */
    @PostMapping("/tecnicas/{id}/ejecuciones")
    @Transactional
    public String guardar(@PathVariable String id, @RequestParam MultiValueMap<String, String> parametros,
                          HttpServletResponse respuesta, Model modelo) {
        Tecnica t = tecnicaActiva(id);
        UsuarioSesion yo = paginas.usuarioActual();
        Map<String, Object> config = configDelFormulario(t, parametros);
        Map<String, Object> valores = LectorFormulario.leer(motor.camposEntrada(t), parametros, "");
        Contexto ctx = motor.contexto(yo.id(), yo.institucionId());
        MotorTecnicas.Evaluacion ev = motor.evaluar(t, config, valores, ctx);
        if (!ev.valida()) {
            return conErrores(t, config, valores, ev.errores(), parametros, respuesta, modelo);
        }
        Resultado<?> r = ev.resultado().orElseThrow();
        if (r.bloqueoGuardado().isPresent()) {
            return conErrores(t, config, valores, Map.of("_guardado", r.bloqueoGuardado().get()), parametros, respuesta, modelo);
        }
        // Dentro del asistente del Diario, lo guardado queda en el expediente de la decisión, si es de esta persona.
        Optional<UUID> expediente = expediente(parametros);
        if (expediente.isPresent() && expedientes.porId(yo.id(), expediente.get()).isEmpty()) {
            throw new ObjetoNoEncontrado("expediente");
        }
        Ejecucion nueva = new Ejecucion(Uuid7.en(reloj.ahora()), yo.id(), yo.institucionId(), t.id(), r.versionEsquema(), expediente,
                ev.config(), ev.entrada(), pensamiento.catalogo.MapeadorJson.escribir(r.valor()), r.resumen(), r.modelo(),
                clave(parametros), reloj.ahora());
        Ejecucion guardada = guardado.guardar(nueva, r);
        respuesta.setHeader("HX-Trigger", "ejecucion-guardada");
        modelo.addAttribute("resultado", renderizadores.render(t, Optional.of(guardada.id()), "", motor.valorDe(guardada), Modo.COMPLETO));
        modelo.addAttribute("tecnica", t);
        modelo.addAttribute("ejecucion", guardada);
        modelo.addAttribute("expedientes", expedientes.deUsuario(yo.id()));
        modelo.addAttribute("nuevaClave", nuevaClave());
        return "fragmentos/ficha/resultado";
    }

    private String conErrores(Tecnica t, Map<String, Object> config, Map<String, Object> valores, Map<String, String> errores,
                              MultiValueMap<String, String> parametros, HttpServletResponse respuesta, Model modelo) {
        respuesta.setStatus(422);
        respuesta.setHeader("HX-Retarget", "#form-" + t.id());
        respuesta.setHeader("HX-Reswap", "outerHTML");
        modelo.addAttribute("f", VistaFormulario.de(t, motor.camposConfig(t), motor.camposEntrada(t), config, valores, errores,
                clave(parametros), origen(parametros)).conModelo(modeloEn(t, config)).conExpediente(expediente(parametros)));
        return "fragmentos/ficha/formulario";
    }

    // ---------------------------------------------------------------------------------------------
    // Configuración plegable
    // ---------------------------------------------------------------------------------------------

    @PostMapping("/tecnicas/{id}/configuracion")
    @Transactional
    public String guardarConfiguracion(@PathVariable String id, @RequestParam MultiValueMap<String, String> parametros,
                                       HttpServletResponse respuesta, Model modelo) {
        Tecnica t = tecnicaActiva(id);
        UsuarioSesion yo = paginas.usuarioActual();
        Map<String, Object> config = LectorFormulario.leer(motor.camposConfig(t), parametros, "cfg.");
        Map<String, String> errores = motor.validarConfig(t, config);
        if (!errores.isEmpty()) {
            respuesta.setStatus(422);
            modelo.addAttribute("c", configuracion(yo, t, config, errores, ""));
            modelo.addAttribute("f", null);
            return "fragmentos/ficha/configuracion-guardada";
        }
        motor.guardarConfig(yo.id(), yo.institucionId(), t, config);
        return configuracionAplicada(t, yo, parametros, "Configuración guardada.", modelo);
    }

    @PostMapping("/tecnicas/{id}/configuracion/restablecer")
    @Transactional
    public String restablecerConfiguracion(@PathVariable String id, @RequestParam MultiValueMap<String, String> parametros, Model modelo) {
        Tecnica t = tecnicaActiva(id);
        UsuarioSesion yo = paginas.usuarioActual();
        motor.restablecerConfig(yo.id(), t);
        return configuracionAplicada(t, yo, parametros, "Volviste a la configuración del catálogo.", modelo);
    }

    /** Devuelve el bloque de configuración y, fuera de banda, el resumen de la cabecera y el formulario con la configuración nueva. */
    private String configuracionAplicada(Tecnica t, UsuarioSesion yo, MultiValueMap<String, String> parametros, String mensaje, Model modelo) {
        Map<String, Object> config = motor.configDeUsuario(yo.id(), t);
        modelo.addAttribute("c", configuracion(yo, t, config, Map.of(), mensaje));
        Map<String, Object> valores = LectorFormulario.leer(motor.camposEntrada(t), parametros, "");
        LectorFormulario.igualarCeldas(motor.camposEntrada(t), valores);
        modelo.addAttribute("f", VistaFormulario.de(t, motor.camposConfig(t), motor.camposEntrada(t), config, valores, Map.of(),
                clave(parametros), "tu configuración").conModelo(modeloEn(t, config)).conExpediente(expediente(parametros)));
        return "fragmentos/ficha/configuracion-guardada";
    }

    // ---------------------------------------------------------------------------------------------
    // Historial
    // ---------------------------------------------------------------------------------------------

    private List<FilaHistorial> historial(UsuarioSesion yo, Tecnica t) {
        if (t.estaPendiente()) {
            return List.of();
        }
        return ejecuciones.porTecnica(yo.id(), t.id()).stream()
                .map(e -> new FilaHistorial(e, fecha(e), VistaFormulario.resumen(motor.camposConfig(t), LenguajeCampos.mapa(e.config()))))
                .toList();
    }

    private ComparadorEjecuciones.Comparacion comparar(UsuarioSesion yo, UUID a, UUID b) {
        Ejecucion ea = ejecuciones.porId(yo.id(), a).orElseThrow(() -> new ObjetoNoEncontrado("ejecución"));
        Ejecucion eb = ejecuciones.porId(yo.id(), b).orElseThrow(() -> new ObjetoNoEncontrado("ejecución"));
        return ComparadorEjecuciones.comparar(ea, eb);
    }

    // ---------------------------------------------------------------------------------------------
    // Utilidades
    // ---------------------------------------------------------------------------------------------

    private Tecnica tecnica(String id) {
        try {
            return tecnicas.porId(IdTecnica.de(id)).orElseThrow(() -> new ObjetoNoEncontrado("técnica"));
        } catch (IllegalArgumentException e) {
            throw new ObjetoNoEncontrado("técnica");
        }
    }

    private Tecnica tecnicaActiva(String id) {
        Tecnica t = tecnica(id);
        if (t.estaPendiente() || motor.ejecutor(t.id()).isEmpty()) {
            throw new ObjetoNoEncontrado("técnica");
        }
        return t;
    }

    /** La configuración viaja oculta en el formulario ("config.escala"); si falta, la del usuario. */
    private Map<String, Object> configDelFormulario(Tecnica t, MultiValueMap<String, String> parametros) {
        return configDelFormulario(motor, t, paginas.usuarioActual(), parametros);
    }

    static Map<String, Object> configDelFormulario(MotorTecnicas motor, Tecnica t, UsuarioSesion yo, MultiValueMap<String, String> parametros) {
        boolean trae = parametros.keySet().stream().anyMatch(k -> k.startsWith("config."));
        if (!trae) {
            return motor.configDeUsuario(yo.id(), t);
        }
        return LectorFormulario.leer(motor.camposConfig(t), parametros, "config.");
    }

    static String clave(MultiValueMap<String, String> parametros) {
        String clave = parametros.getFirst("_clave");
        return clave == null || clave.isBlank() || clave.length() > 64 ? nuevaClave() : clave;
    }

    /** El expediente del asistente del Diario que viaja oculto; vacío en la ficha o si no es un identificador. */
    static Optional<UUID> expediente(MultiValueMap<String, String> parametros) {
        String x = parametros.getFirst("_expediente");
        if (x == null || x.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(x.strip()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    static String origen(MultiValueMap<String, String> parametros) {
        String origen = parametros.getFirst("_origen");
        return origen == null || origen.isBlank() || origen.length() > 200 ? "tu configuración" : origen;
    }

    private static String nuevaClave() {
        return UUID.randomUUID().toString();
    }

    private String fecha(Ejecucion e) {
        return Fechas.corta(e.creadaEn(), reloj.zona());
    }

    static Map<String, String> sinErrores() {
        return new HashMap<>();
    }
}
