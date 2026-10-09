package pensamiento.web.verificacion;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;
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
import org.springframework.web.util.HtmlUtils;

import pensamiento.catalogo.MapeadorJson;
import pensamiento.flujos.FichaDeVerificacion;
import pensamiento.nucleo.Afirmacion;
import pensamiento.nucleo.ConModelo;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.PendienteGuardado;
import pensamiento.nucleo.Propuesta;
import pensamiento.nucleo.TipoAfirmacion;
import pensamiento.nucleo.TipoPendiente;
import pensamiento.nucleo.Verificacion;
import pensamiento.nucleo.puertos.RepositorioEjecucion;
import pensamiento.nucleo.puertos.RepositorioTecnica;
import pensamiento.nucleo.puertos.RepositorioVerificaciones;
import pensamiento.tecnicas.f4.EjecutorCraap;
import pensamiento.tecnicas.f4.EjecutorTriangulacion;
import pensamiento.web.ObjetoNoEncontrado;
import pensamiento.web.Pagina;
import pensamiento.web.seguridad.UsuarioSesion;
import pensamiento.web.tecnicas.MotorTecnicas;
import pensamiento.web.tecnicas.TurnosIa;

/**
 * A+ · Ficha de verificación (P10 con el panel de biblioteca P11) y ficha de fuente (P12). Se abre desde una premisa de un
 * argumento guardado o desde un pendiente de verificación; la de otra persona da 404 (RF-03). Cada paso guarda en el momento
 * y vuelve a pintar la ficha; el veredicto recalcula R01 a R04, cierra los pendientes de verificación de la afirmación y
 * guarda una ejecución de T22 en su expediente.
 */
@Controller
public class ControladorVerificacion {

    private final FichaDeVerificacion fichas;
    private final RepositorioVerificaciones verificaciones;
    private final RepositorioEjecucion ejecuciones;
    private final RepositorioTecnica tecnicas;
    private final MotorTecnicas motor;
    private final TurnosIa turnos;
    private final TemplateEngine plantillas;
    private final Pagina.Fabrica paginas;

    public ControladorVerificacion(FichaDeVerificacion fichas, RepositorioVerificaciones verificaciones, RepositorioEjecucion ejecuciones,
                                   RepositorioTecnica tecnicas, MotorTecnicas motor, TurnosIa turnos, TemplateEngine plantillas, Pagina.Fabrica paginas) {
        this.fichas = fichas;
        this.verificaciones = verificaciones;
        this.ejecuciones = ejecuciones;
        this.tecnicas = tecnicas;
        this.motor = motor;
        this.turnos = turnos;
        this.plantillas = plantillas;
        this.paginas = paginas;
    }

    /** Las afirmaciones por verificar (pendientes) y las fichas ya trabajadas. */
    @GetMapping("/verificar")
    @Transactional(readOnly = true)
    public String indice(HttpServletRequest request, Model modelo) {
        UsuarioSesion yo = paginas.usuarioActual();
        List<PendienteGuardado> pendientes = ejecuciones.pendientes(yo.id()).stream()
                .filter(p -> p.pendiente().tipo() == TipoPendiente.VERIFICACION && p.pendiente().objetoId().isPresent()).toList();
        List<VistaIndice.Trabajada> trabajadas = new ArrayList<>();
        for (Verificacion v : verificaciones.deUsuario(yo.id()).reversed()) {
            verificaciones.afirmacion(yo.id(), v.afirmacionId()).ifPresent(a -> trabajadas.add(new VistaIndice.Trabajada(a)));
        }
        modelo.addAttribute("pagina", paginas.crear("Verificación", request));
        modelo.addAttribute("v", new VistaIndice(pendientes, trabajadas));
        return "verificar";
    }

    @GetMapping("/verificar/{id}")
    @Transactional(readOnly = true)
    public String ficha(@PathVariable UUID id, HttpServletRequest request, Model modelo) {
        UsuarioSesion yo = paginas.usuarioActual();
        FichaDeVerificacion.Ficha f = fichas.abrir(yo.id(), id, configuracion(yo)).orElseThrow(() -> new ObjetoNoEncontrado("afirmación"));
        modelo.addAttribute("pagina", paginas.crear("Verificación", request));
        modelo.addAttribute("f", new VistaFicha(f, "", nuevaClave(), motor.modeloDisponible()));
        return "verificacion";
    }

    @PostMapping("/verificar/{id}/tipo")
    @Transactional
    public String tipo(@PathVariable UUID id, @RequestParam String tipo, Model modelo) {
        UsuarioSesion yo = paginas.usuarioActual();
        TipoAfirmacion t;
        try {
            t = TipoAfirmacion.valueOf(tipo.toUpperCase());
        } catch (IllegalArgumentException e) {
            t = null;
        }
        if (t == null || !fichas.cambiarTipo(yo.id(), id, t)) {
            throw new ObjetoNoEncontrado("afirmación");
        }
        return cuerpo(yo, id, "Tipo guardado: " + fichas.abrir(yo.id(), id, configuracion(yo)).orElseThrow().tipo().nombre().toLowerCase() + ".", modelo);
    }

    @PostMapping("/verificar/{id}/preguntas")
    @Transactional
    public String preguntas(@PathVariable UUID id, @RequestParam(name = "respondida", required = false) List<String> respondidas, Model modelo) {
        UsuarioSesion yo = paginas.usuarioActual();
        if (!fichas.marcarPreguntas(yo.id(), yo.institucionId(), id, respondidas == null ? List.of() : respondidas, configuracion(yo))) {
            throw new ObjetoNoEncontrado("afirmación");
        }
        return cuerpo(yo, id, "Preguntas guardadas.", modelo);
    }

    @PostMapping("/verificar/{id}/evidencias/{evidencia}/quitar")
    @Transactional
    public String quitar(@PathVariable UUID id, @PathVariable UUID evidencia, Model modelo) {
        UsuarioSesion yo = paginas.usuarioActual();
        if (!fichas.quitarEvidencia(yo.id(), id, evidencia)) {
            throw new ObjetoNoEncontrado("evidencia");
        }
        return cuerpo(yo, id, "Evidencia quitada: el cálculo se rehízo sin ella.", modelo);
    }

    @PostMapping("/verificar/{id}/veredicto")
    @Transactional
    public String veredicto(@PathVariable UUID id, @RequestParam(required = false) String confianza, @RequestParam(required = false) String clave,
                            HttpServletResponse respuesta, Model modelo) {
        UsuarioSesion yo = paginas.usuarioActual();
        Optional<Integer> declarada;
        try {
            declarada = confianza == null || confianza.isBlank() ? Optional.empty() : Optional.of(Integer.parseInt(confianza.strip()));
        } catch (NumberFormatException e) {
            declarada = Optional.of(-1);
        }
        String claveSegura = clave == null || clave.isBlank() ? nuevaClave() : clave.strip();
        FichaDeVerificacion.VeredictoGuardado g = fichas.guardarVeredicto(yo.id(), yo.institucionId(), id, declarada, "verificacion:" + claveSegura,
                configuracion(yo)).orElseThrow(() -> new ObjetoNoEncontrado("afirmación"));
        if (g.error().isPresent()) {
            respuesta.setStatus(422);
            return cuerpo(yo, id, g.error().get(), modelo);
        }
        Afirmacion a = verificaciones.afirmacion(yo.id(), id).orElseThrow();
        StringBuilder mensaje = new StringBuilder("Veredicto guardado: " + FichaDeVerificacion.texto(a.estado()) + ".");
        if (g.cerrados() > 0) {
            mensaje.append(g.cerrados() == 1 ? " Se cerró 1 pendiente de verificación." : " Se cerraron " + g.cerrados() + " pendientes de verificación.");
        }
        g.cambio().ifPresent(c -> mensaje.append(" Cambio de opinión: ").append(c.confianzaAntes()).append(" → ").append(c.confianzaDespues())
                .append(" (causa: evidencia)."));
        g.ejecucion().ifPresent(e -> respuesta.setHeader("HX-Trigger", "ejecucion-guardada"));
        return cuerpo(yo, id, mensaje.toString(), modelo);
    }

    // ---------------------------------------------------------------------------------------------
    // P12 · Ficha de fuente
    // ---------------------------------------------------------------------------------------------

    @GetMapping("/verificar/{id}/fuentes/nueva")
    @Transactional(readOnly = true)
    public String nuevaFuente(@PathVariable UUID id, @RequestParam(required = false) String fragmento, @RequestParam(required = false) String postura,
                              @RequestParam(required = false) String propuesta, @RequestParam(required = false) String porque,
                              @RequestParam(name = "modelo", defaultValue = "false") boolean delModelo, HttpServletRequest request, Model modelo) {
        UsuarioSesion yo = paginas.usuarioActual();
        FichaDeVerificacion.Ficha f = fichas.abrir(yo.id(), id, configuracion(yo)).orElseThrow(() -> new ObjetoNoEncontrado("afirmación"));
        VistaFuente v = VistaFuente.nueva(f, fragmento == null ? "" : fragmento, postura == null ? "" : postura, propuesta == null ? "" : propuesta,
                porque == null ? "" : porque, delModelo, configuracion(yo).t21());
        if (!fragmento(fragmento, v, yo)) {
            throw new ObjetoNoEncontrado("pasaje");
        }
        modelo.addAttribute("pagina", paginas.crear("Verificación", request));
        modelo.addAttribute("v", v);
        return "fuente";
    }

    /** Si viene de la biblioteca, el pasaje y su documento se llenan solos. Falso si el fragmento no lo puede ver. */
    private boolean fragmento(String fragmento, VistaFuente v, UsuarioSesion yo) {
        if (fragmento == null || fragmento.isBlank()) {
            return true;
        }
        try {
            Optional<FichaDeVerificacion.PasajeParaEtiquetar> p = fichas.pasajeParaEtiquetar(yo.id(), v.afirmacion().id(), UUID.fromString(fragmento));
            p.ifPresent(x -> v.desdeBiblioteca(x.cita()));
            return p.isPresent();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @PostMapping("/verificar/{id}/fuentes/previa")
    @Transactional(readOnly = true)
    public String previa(@PathVariable UUID id, @RequestParam MultiValueMap<String, String> parametros, Model modelo) {
        UsuarioSesion yo = paginas.usuarioActual();
        FichaDeVerificacion.Previa p = fichas.previsualizar(yo.id(), id, VistaFuente.borrador(parametros), configuracion(yo))
                .orElseThrow(() -> new ObjetoNoEncontrado("afirmación"));
        modelo.addAttribute("p", p);
        return "fragmentos/verificacion/previa";
    }

    @PostMapping("/verificar/{id}/fuentes")
    @Transactional
    public String guardarFuente(@PathVariable UUID id, @RequestParam MultiValueMap<String, String> parametros, HttpServletRequest request,
                                HttpServletResponse respuesta, Model modelo) {
        UsuarioSesion yo = paginas.usuarioActual();
        FichaDeVerificacion.BorradorFuente borrador = VistaFuente.borrador(parametros);
        FichaDeVerificacion.Registro r = fichas.registrarFuente(yo.id(), yo.institucionId(), id, borrador, configuracion(yo))
                .orElseThrow(() -> new ObjetoNoEncontrado("afirmación"));
        if (!r.errores().isEmpty()) {
            FichaDeVerificacion.Ficha f = fichas.abrir(yo.id(), id, configuracion(yo)).orElseThrow();
            VistaFuente v = VistaFuente.conErrores(f, borrador, r.errores(), configuracion(yo).t21());
            fragmento(borrador.fragmentoId(), v, yo);
            respuesta.setStatus(422);
            modelo.addAttribute("pagina", paginas.crear("Verificación", request));
            modelo.addAttribute("v", v);
            return "fuente";
        }
        String destino = "otra".equals(parametros.getFirst("despues")) ? "/verificar/" + id + "/fuentes/nueva" : "/verificar/" + id;
        respuesta.setHeader("HX-Redirect", destino);
        return "redirect:" + destino;
    }

    // ---------------------------------------------------------------------------------------------
    // P11 · Etiquetar un pasaje con el modelo (SSE)
    // ---------------------------------------------------------------------------------------------

    @PostMapping("/verificar/{id}/pasajes/{fragmento}/etiqueta")
    public String etiquetar(@PathVariable UUID id, @PathVariable UUID fragmento, Model modelo) {
        UsuarioSesion yo = paginas.usuarioActual();
        FichaDeVerificacion.PasajeParaEtiquetar p = fichas.pasajeParaEtiquetar(yo.id(), id, fragmento)
                .orElseThrow(() -> new ObjetoNoEncontrado("pasaje"));
        if (!motor.modeloDisponible()) {
            modelo.addAttribute("mensaje", "El modelo no está disponible: elige tú la postura al usar el pasaje como evidencia.");
            return "fragmentos/verificacion/sin-modelo";
        }
        var ctx = motor.contextoConIa(yo.id(), yo.institucionId());
        TurnosIa.Turno turno = turnos.abrir(yo.id(), "verificacion:" + fragmento, provisional -> fin(id, fragmento, fichas.etiquetar(p, ctx, provisional)));
        modelo.addAttribute("turno", turno.id().toString());
        modelo.addAttribute("destino", "etiqueta-" + fragmento);
        return "fragmentos/verificacion/espera";
    }

    @PostMapping("/verificar/turnos/{turno}/cancelar")
    public String cancelarEtiqueta(@PathVariable UUID turno, Model modelo) {
        UsuarioSesion yo = paginas.usuarioActual();
        if (!turnos.cancelar(yo.id(), turno)) {
            throw new ObjetoNoEncontrado("turno");
        }
        modelo.addAttribute("mensaje", "Cancelaste el pedido: elige tú la postura al usar el pasaje como evidencia.");
        return "fragmentos/verificacion/sin-modelo";
    }

    /** El evento final del turno: la propuesta, sin adoptar, con su por qué y el enlace para adoptarla en la ficha de fuente. */
    private TurnosIa.Fin fin(UUID afirmacion, UUID fragmento, ConModelo.Propuestas propuestas) {
        if (propuestas.caida().isPresent() || propuestas.nuevas().isEmpty()) {
            return new TurnosIa.Fin("<p class=\"aviso-ia\" role=\"status\"><span class=\"chip chip-aviso\">sin modelo</span> "
                    + HtmlUtils.htmlEscape(propuestas.caida().orElse("El modelo no propuso nada: elige tú la postura.")) + "</p>");
        }
        Propuesta p = propuestas.nuevas().getFirst();
        StringOutput salida = new StringOutput();
        plantillas.render("fragmentos/verificacion/propuesta.jte", Map.of("afirmacion", afirmacion.toString(), "fragmento", fragmento.toString(),
                "propuesta", p), salida);
        return new TurnosIa.Fin(salida.toString());
    }

    // ---------------------------------------------------------------------------------------------

    private String cuerpo(UsuarioSesion yo, UUID id, String mensaje, Model modelo) {
        FichaDeVerificacion.Ficha f = fichas.abrir(yo.id(), id, configuracion(yo)).orElseThrow(() -> new ObjetoNoEncontrado("afirmación"));
        modelo.addAttribute("f", new VistaFicha(f, mensaje, nuevaClave(), motor.modeloDisponible()));
        return "fragmentos/verificacion/ficha";
    }

    /** La configuración de T22 (mínimo de grupos) y de T21 (pesos de CRAAP) de la persona. */
    private FichaDeVerificacion.Configuracion configuracion(UsuarioSesion yo) {
        Map<String, Object> t22 = motor.configDeUsuario(yo.id(), tecnicas.porId(IdTecnica.de("T22")).orElseThrow());
        Map<String, Object> t21 = motor.configDeUsuario(yo.id(), tecnicas.porId(IdTecnica.de("T21")).orElseThrow());
        return new FichaDeVerificacion.Configuracion(MapeadorJson.leer(MapeadorJson.escribir(t22), EjecutorTriangulacion.Config.class),
                MapeadorJson.leer(MapeadorJson.escribir(t21), EjecutorCraap.Config.class));
    }

    private static String nuevaClave() {
        return UUID.randomUUID().toString();
    }
}
