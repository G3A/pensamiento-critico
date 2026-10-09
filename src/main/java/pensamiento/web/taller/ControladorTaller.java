package pensamiento.web.taller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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

import pensamiento.catalogo.CatalogoJson;
import pensamiento.catalogo.MapeadorJson;
import pensamiento.expediente.GuardadoDeEjecuciones;
import pensamiento.expediente.ServicioExpedientes;
import pensamiento.flujos.TallerDeArgumentos;
import pensamiento.nucleo.AfirmacionConRol;
import pensamiento.nucleo.Contexto;
import pensamiento.nucleo.Ejecucion;
import pensamiento.nucleo.EstandarPrueba;
import pensamiento.nucleo.Expediente;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Resultado;
import pensamiento.nucleo.Tecnica;
import pensamiento.nucleo.Uuid7;
import pensamiento.nucleo.puertos.Reloj;
import pensamiento.nucleo.puertos.RepositorioArgumentos;
import pensamiento.nucleo.puertos.RepositorioEjecucion;
import pensamiento.nucleo.puertos.RepositorioExpediente;
import pensamiento.nucleo.puertos.RepositorioTecnica;
import pensamiento.tecnicas.f1.EjecutorMapa;
import pensamiento.tecnicas.f1.EjecutorToulmin;
import pensamiento.tecnicas.f1.ResultadoMapa;
import pensamiento.tecnicas.f3.EjecutorFalacias;
import pensamiento.tecnicas.f3.ResultadoFalacias;
import pensamiento.web.ObjetoNoEncontrado;
import pensamiento.web.Pagina;
import pensamiento.web.patrones.Modo;
import pensamiento.web.patrones.Renderizadores;
import pensamiento.web.seguridad.UsuarioSesion;
import pensamiento.web.tecnicas.MotorTecnicas;

/**
 * Taller de argumentos reducido (P14, flujo A del hito 2): un texto Argdown, el mapa de T01, el panel Toulmin de
 * T02 y el panel de falacias por reglas de T13 sobre el mismo texto. Evaluar no guarda ni abre transacción;
 * guardar crea una ejecución por técnica, en una sola transacción corta e idempotente por clave.
 */
@Controller
public class ControladorTaller {

    /** Lo que trae el formulario del Taller. */
    record Pedido(String argdown, EstandarPrueba estandar, TallerDeArgumentos.ExtrasToulmin extras, List<String> confirmadas, String clave) {
    }

    /** Las tres evaluaciones; vacías si el texto no pasó la validación de T01. */
    record Evaluacion(Map<String, String> errores, Optional<MotorTecnicas.Evaluacion> mapa, Optional<MotorTecnicas.Evaluacion> toulmin,
                      Optional<MotorTecnicas.Evaluacion> falacias) {
    }

    private static final List<String> LINEAS = List.of("conclusion", "premisa", "objecion", "oculta");

    private final RepositorioTecnica tecnicas;
    private final RepositorioEjecucion ejecuciones;
    private final RepositorioExpediente expedientes;
    private final RepositorioArgumentos argumentos;
    private final GuardadoDeEjecuciones guardado;
    private final ServicioExpedientes servicioExpedientes;
    private final MotorTecnicas motor;
    private final Renderizadores renderizadores;
    private final Reloj reloj;
    private final Pagina.Fabrica paginas;
    private final pensamiento.flujos.FichaDeVerificacion fichas;
    private final pensamiento.nucleo.puertos.RepositorioVerificaciones verificaciones;

    public ControladorTaller(RepositorioTecnica tecnicas, RepositorioEjecucion ejecuciones, RepositorioExpediente expedientes,
                             RepositorioArgumentos argumentos, GuardadoDeEjecuciones guardado, ServicioExpedientes servicioExpedientes,
                             MotorTecnicas motor, Renderizadores renderizadores, Reloj reloj, Pagina.Fabrica paginas,
                             pensamiento.flujos.FichaDeVerificacion fichas, pensamiento.nucleo.puertos.RepositorioVerificaciones verificaciones) {
        this.fichas = fichas;
        this.verificaciones = verificaciones;
        this.tecnicas = tecnicas;
        this.ejecuciones = ejecuciones;
        this.expedientes = expedientes;
        this.argumentos = argumentos;
        this.guardado = guardado;
        this.servicioExpedientes = servicioExpedientes;
        this.motor = motor;
        this.renderizadores = renderizadores;
        this.reloj = reloj;
        this.paginas = paginas;
    }

    @GetMapping("/taller")
    @Transactional(readOnly = true)
    public String taller(@RequestParam(required = false) String ejemplo, HttpServletRequest request, Model modelo) {
        UsuarioSesion yo = paginas.usuarioActual();
        String texto = "sucursal".equals(ejemplo) ? ejemploDeLaSucursal() : "";
        EstandarPrueba estandar = estandarDeUsuario(yo);
        modelo.addAttribute("pagina", paginas.crear("Taller", request));
        modelo.addAttribute("t", vacia(texto, estandar, TallerDeArgumentos.ExtrasToulmin.VACIOS, nuevaClave(), Map.of()));
        return "taller";
    }

    /** Agrega una línea de ejemplo al texto (marcado manual: conclusión, premisa, objeción o premisa oculta). */
    @PostMapping("/taller/linea")
    public String linea(@RequestParam MultiValueMap<String, String> parametros, Model modelo) {
        Pedido p = pedido(parametros);
        String tipo = Optional.ofNullable(parametros.getFirst("_linea")).filter(LINEAS::contains).orElse("premisa");
        String linea = switch (tipo) {
            case "conclusion" -> "Escribe aquí la conclusión.";
            case "objecion" -> "  - Escribe aquí una objeción.";
            case "oculta" -> "  + Escribe aquí lo que se da por sentado. #oculta";
            default -> "  + Escribe aquí una premisa.";
        };
        String texto = p.argdown().isBlank() ? linea.strip() : p.argdown().stripTrailing() + "\n" + ("conclusion".equals(tipo) ? "\n" : "") + linea;
        modelo.addAttribute("t", vacia(texto, p.estandar(), p.extras(), p.clave(), Map.of()));
        return "fragmentos/taller/formulario";
    }

    /** Evaluar sin guardar: endpoint sin transacción. 422 con el mismo formulario si el texto no está en el subconjunto. */
    @PostMapping("/taller/evaluar")
    public String evaluar(@RequestParam MultiValueMap<String, String> parametros, HttpServletResponse respuesta, Model modelo) {
        UsuarioSesion yo = paginas.usuarioActual();
        Pedido p = pedido(parametros);
        Evaluacion ev = evaluar(yo, p, confirmadasVigentes(parametros, p));
        if (ev.mapa().isEmpty()) {
            respuesta.setStatus(422);
        }
        modelo.addAttribute("t", vista(p, ev));
        return "fragmentos/taller/formulario";
    }

    /** Guardar: una ejecución por técnica (T01, T02 y T13), con afirmaciones, pendientes y argumentos; idempotente. */
    @PostMapping("/taller/guardar")
    @Transactional
    public String guardar(@RequestParam MultiValueMap<String, String> parametros, HttpServletResponse respuesta, Model modelo) {
        UsuarioSesion yo = paginas.usuarioActual();
        Pedido p = pedido(parametros);
        Evaluacion ev = evaluar(yo, p, confirmadasVigentes(parametros, p));
        if (ev.mapa().isEmpty()) {
            respuesta.setStatus(422);
            respuesta.setHeader("HX-Retarget", "#" + VistaTaller.ID_FORMULARIO);
            respuesta.setHeader("HX-Reswap", "outerHTML");
            modelo.addAttribute("t", vista(p, ev));
            return "fragmentos/taller/formulario";
        }
        List<Ejecucion> guardadas = new ArrayList<>();
        guardar(yo, EjecutorMapa.ID, ev.mapa().get(), p.clave()).ifPresent(guardadas::add);
        ev.toulmin().filter(MotorTecnicas.Evaluacion::valida).flatMap(e -> guardar(yo, EjecutorToulmin.ID, e, p.clave())).ifPresent(guardadas::add);
        ev.falacias().filter(MotorTecnicas.Evaluacion::valida).flatMap(e -> guardar(yo, EjecutorFalacias.ID, e, p.clave())).ifPresent(guardadas::add);
        respuesta.setHeader("HX-Trigger", "ejecucion-guardada");
        modelo.addAttribute("g", new VistaGuardado(guardadas, citas(guardadas), expedientes.deUsuario(yo.id()), "", nuevaClave()));
        return "fragmentos/taller/guardado";
    }

    /** Asocia todas las ejecuciones recién guardadas a un expediente existente o a uno nuevo. */
    @PostMapping("/taller/expediente")
    @Transactional
    public String asociar(@RequestParam(name = "ejecucion", required = false) List<UUID> ids, @RequestParam(required = false) String expediente,
                          @RequestParam(required = false) String nuevo, HttpServletResponse respuesta, Model modelo) {
        UsuarioSesion yo = paginas.usuarioActual();
        List<Ejecucion> suyas = new ArrayList<>();
        for (UUID id : ids == null ? List.<UUID>of() : ids) {
            suyas.add(ejecuciones.porId(yo.id(), id).orElseThrow(() -> new ObjetoNoEncontrado("ejecución")));
        }
        Optional<UUID> destino = Optional.ofNullable(expediente).filter(s -> !s.isBlank()).map(ControladorTaller::uuid);
        Optional<String> nombre = Optional.ofNullable(nuevo).filter(s -> !s.isBlank());
        String mensaje = "Sin expediente.";
        try {
            for (Ejecucion e : suyas) {
                Optional<Optional<Expediente>> r = servicioExpedientes.asociar(yo.id(), yo.institucionId(), e.id(), destino, nombre);
                Optional<Expediente> x = r.orElseThrow(() -> new ObjetoNoEncontrado("expediente"));
                if (x.isPresent()) {
                    destino = Optional.of(x.get().id());
                    nombre = Optional.empty();
                    mensaje = "Asociadas al expediente \"" + x.get().nombre() + "\".";
                }
            }
        } catch (ServicioExpedientes.NombreInvalido e) {
            respuesta.setStatus(422);
            mensaje = e.getMessage();
        }
        respuesta.setHeader("HX-Trigger", "expediente-cambiado");
        List<Ejecucion> actualizadas = suyas.stream().map(e -> ejecuciones.porId(yo.id(), e.id()).orElseThrow()).toList();
        modelo.addAttribute("g", new VistaGuardado(actualizadas, citas(actualizadas), expedientes.deUsuario(yo.id()), mensaje, null));
        return "fragmentos/taller/guardado";
    }

    /** Un argumento guardado (RF-03: el de otra persona da 404, igual que uno que no existe). */
    @GetMapping("/argumentos/{id}")
    @Transactional(readOnly = true)
    public String argumento(@PathVariable String id, HttpServletRequest request, Model modelo) {
        UsuarioSesion yo = paginas.usuarioActual();
        RepositorioArgumentos.ArgumentoGuardado g = argumentos.porId(yo.id(), uuid(id)).orElseThrow(() -> new ObjetoNoEncontrado("argumento"));
        Map<UUID, String> textos = new LinkedHashMap<>();
        for (AfirmacionConRol a : ejecuciones.afirmacionesDe(yo.id(), g.ejecucionId())) {
            textos.put(a.afirmacionId(), a.texto());
        }
        modelo.addAttribute("pagina", paginas.crear("Argumento", request));
        Map<UUID, pensamiento.nucleo.EstadoAfirmacion> estados = new LinkedHashMap<>();
        for (UUID afirmacion : textos.keySet()) {
            verificaciones.afirmacion(yo.id(), afirmacion).ifPresent(a -> estados.put(afirmacion, a.estado()));
        }
        modelo.addAttribute("a", new VistaArgumento(g, textos, estados, fichas.r04DeArgumento(yo.id(), g.argumento().argumento().id())));
        return "argumento";
    }

    // ---------------------------------------------------------------------------------------------

    private Evaluacion evaluar(UsuarioSesion yo, Pedido p, List<String> confirmadas) {
        Contexto ctx = motor.contexto(yo.id(), yo.institucionId());
        Tecnica t01 = tecnica(EjecutorMapa.ID);
        Map<String, Object> config01 = motor.configDeUsuario(yo.id(), t01);
        config01.put("estandar", p.estandar().toString());
        Map<String, Object> entrada01 = new LinkedHashMap<>();
        if (!p.argdown().isBlank()) {
            entrada01.put("argdown", p.argdown());
        }
        MotorTecnicas.Evaluacion mapa = motor.evaluar(t01, config01, entrada01, ctx);
        if (!mapa.valida()) {
            Map<String, String> errores = new LinkedHashMap<>();
            mapa.errores().forEach((campo, mensaje) -> errores.put(campo.startsWith("config.") ? "estandar" : "argdown", mensaje));
            return new Evaluacion(errores, Optional.empty(), Optional.empty(), Optional.empty());
        }
        ResultadoMapa valor = (ResultadoMapa) mapa.resultado().orElseThrow().valor();

        Tecnica t02 = tecnica(EjecutorToulmin.ID);
        Map<String, Object> config02 = motor.configDeUsuario(yo.id(), t02);
        // En el Taller el panel Toulmin señala lo que falta en vez de exigirlo: ninguna parte es obligatoria.
        config02.put("obligatorios", List.of());
        MotorTecnicas.Evaluacion toulmin = motor.evaluar(t02, config02, TallerDeArgumentos.entradaToulmin(valor, p.extras()), ctx);

        Tecnica t13 = tecnica(EjecutorFalacias.ID);
        Map<String, Object> entrada13 = new LinkedHashMap<>();
        entrada13.put("texto", TallerDeArgumentos.textoParaFalacias(valor));
        entrada13.put("confirmadas", confirmadas);
        MotorTecnicas.Evaluacion falacias = motor.evaluar(t13, motor.configDeUsuario(yo.id(), t13), entrada13, ctx);
        return new Evaluacion(Map.of(), Optional.of(mapa), Optional.of(toulmin), Optional.of(falacias));
    }

    private Optional<Ejecucion> guardar(UsuarioSesion yo, IdTecnica tecnica, MotorTecnicas.Evaluacion ev, String clave) {
        Resultado<?> r = ev.resultado().orElseThrow();
        Ejecucion nueva = new Ejecucion(Uuid7.en(reloj.ahora()), yo.id(), yo.institucionId(), tecnica, r.versionEsquema(), Optional.empty(),
                ev.config(), ev.entrada(), MapeadorJson.escribir(r.valor()), r.resumen(), Optional.empty(), clave + "/" + tecnica, reloj.ahora());
        return Optional.of(guardado.guardar(nueva, r));
    }

    private VistaTaller vista(Pedido p, Evaluacion ev) {
        Optional<gg.jte.Content> mapa = ev.mapa().map(e -> renderizadores.render(tecnica(EjecutorMapa.ID), Optional.empty(), "taller-mapa",
                e.resultado().orElseThrow().valor(), Modo.COMPLETO));
        Optional<gg.jte.Content> toulmin = ev.toulmin().filter(MotorTecnicas.Evaluacion::valida).map(e -> renderizadores.render(
                tecnica(EjecutorToulmin.ID), Optional.empty(), "taller-toulmin", e.resultado().orElseThrow().valor(), Modo.COMPLETO));
        String errorToulmin = ev.toulmin().filter(e -> !e.valida()).map(e -> String.join(" ", e.errores().values())).orElse("");
        Optional<ResultadoFalacias> falacias = ev.falacias().filter(MotorTecnicas.Evaluacion::valida)
                .map(e -> (ResultadoFalacias) e.resultado().orElseThrow().valor());
        Optional<gg.jte.Content> panelFalacias = falacias.map(v -> renderizadores.render(tecnica(EjecutorFalacias.ID), Optional.empty(),
                "taller-falacias", v, Modo.COMPLETO));
        List<ResultadoFalacias.Marca> marcas = falacias.map(ResultadoFalacias::marcas).orElse(List.of());
        List<String> confirmadas = marcas.stream().filter(ResultadoFalacias.Marca::confirmada).map(ResultadoFalacias.Marca::codigo).toList();
        return new VistaTaller(p.argdown(), p.estandar(), p.extras().respaldo(), p.extras().fuenteRespaldo(), p.extras().calificador(),
                ev.mapa().isPresent() ? p.argdown() : "", p.clave(), ev.errores(), mapa, toulmin, panelFalacias, marcas, confirmadas, errorToulmin);
    }

    private static VistaTaller vacia(String texto, EstandarPrueba estandar, TallerDeArgumentos.ExtrasToulmin extras, String clave,
                                     Map<String, String> errores) {
        return new VistaTaller(texto, estandar, extras.respaldo(), extras.fuenteRespaldo(), extras.calificador(), "", clave, errores,
                Optional.empty(), Optional.empty(), Optional.empty(), List.of(), List.of(), "");
    }

    private Pedido pedido(MultiValueMap<String, String> p) {
        EstandarPrueba estandar;
        try {
            estandar = EstandarPrueba.valueOf(Optional.ofNullable(p.getFirst("estandar")).orElse("preponderancia").toUpperCase());
        } catch (IllegalArgumentException e) {
            estandar = EstandarPrueba.PREPONDERANCIA;
        }
        String clave = Optional.ofNullable(p.getFirst("_clave")).filter(c -> c.matches("[0-9a-f-]{36}")).orElseGet(ControladorTaller::nuevaClave);
        return new Pedido(Optional.ofNullable(p.getFirst("argdown")).orElse(""), estandar,
                new TallerDeArgumentos.ExtrasToulmin(p.getFirst("respaldo"), p.getFirst("fuenteRespaldo"), p.getFirst("calificador")),
                p.getOrDefault("confirmadas", List.of()), clave);
    }

    /** Las confirmaciones valen solo si el texto es el mismo que se evaluó: si cambió, los códigos M1, M2… pueden ser otros. */
    private static List<String> confirmadasVigentes(MultiValueMap<String, String> parametros, Pedido p) {
        String evaluado = Optional.ofNullable(parametros.getFirst("textoEvaluado")).orElse("");
        return evaluado.equals(p.argdown()) ? p.confirmadas().stream().filter(c -> c.matches("M\\d{1,2}")).distinct().toList() : List.of();
    }

    private EstandarPrueba estandarDeUsuario(UsuarioSesion yo) {
        Object valor = motor.configDeUsuario(yo.id(), tecnica(EjecutorMapa.ID)).get("estandar");
        try {
            return EstandarPrueba.valueOf(String.valueOf(valor).toUpperCase());
        } catch (IllegalArgumentException e) {
            return EstandarPrueba.PREPONDERANCIA;
        }
    }

    private Tecnica tecnica(IdTecnica id) {
        return tecnicas.porId(id).orElseThrow(() -> new IllegalStateException(id + " no está en el catálogo"));
    }

    private List<String> citas(List<Ejecucion> guardadas) {
        return guardadas.stream().map(e -> tecnica(e.tecnica()).cita()).toList();
    }

    /** El texto del ejemplo 2 de T01 · Mapeo de argumentos: la segunda sucursal de la panadería. */
    private static String ejemploDeLaSucursal() {
        return new CatalogoJson().ejemplosDe(EjecutorMapa.ID).stream().filter(e -> e.titulo().equals("La segunda sucursal")).findFirst()
                .map(e -> MapeadorJson.leer(e.datos(), EjecutorMapa.Entrada.class).argdown()).orElse("");
    }

    private static String nuevaClave() {
        return UUID.randomUUID().toString();
    }

    private static UUID uuid(String texto) {
        try {
            return UUID.fromString(texto);
        } catch (IllegalArgumentException e) {
            throw new ObjetoNoEncontrado("argumento");
        }
    }
}
