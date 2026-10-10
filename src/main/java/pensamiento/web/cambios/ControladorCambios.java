package pensamiento.web.cambios;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import io.github.wimdeblauwe.htmx.spring.boot.mvc.HtmxRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import pensamiento.flujos.RegistroDeCambios;
import pensamiento.nucleo.CambioOpinion;
import pensamiento.nucleo.Validacion;
import pensamiento.tecnicas.f8.EjecutorCambiosOpinion;
import pensamiento.tecnicas.f8.ResultadoCambiosOpinion;
import pensamiento.web.ConfiguracionesF8;
import pensamiento.web.Pagina;
import pensamiento.web.patrones.Modo;
import pensamiento.web.patrones.RenderizadoresF8;
import pensamiento.web.patrones.V11;
import pensamiento.web.seguridad.UsuarioSesion;

/**
 * P20 · Registro de cambios de opinión (módulo T) y el diario de razonamiento: la línea de tiempo de todos los flujos, el
 * resumen del año, las posturas sin revisar con su acceso al modo debate del Consejero y el registro a mano. R05 a la vista:
 * la confianza la declara la persona y cada cambio guarda antes, después y la causa.
 */
@Controller
public class ControladorCambios {

    /** Una postura sin revisar con el enlace al debate del Consejero, ya con la postura escrita. */
    public record SinRevisar(String texto, String debate) {
    }

    /** Lo que pinta P20. @param errores por campo del registro a mano */
    public record VistaCambios(V11 registro, String tituloSinRevisar, List<SinRevisar> sinRevisar, List<String> posturas,
                               List<CambioOpinion.Causa> causas, boolean registroAutomatico, Map<String, String> errores, Valores valores, String clave) {
    }

    /** Lo escrito en el registro a mano, para devolverlo con los errores. */
    public record Valores(String postura, String antes, String despues, String causa) {
        static Valores vacios() {
            return new Valores("", "", "", "");
        }
    }

    /** El diario de razonamiento: vacío si no hay ejecuciones en la ventana. */
    public record VistaDiario(Optional<V11> diario, int semanas) {
    }

    private final RegistroDeCambios registro;
    private final ConfiguracionesF8 configuraciones;
    private final Pagina.Fabrica paginas;

    public ControladorCambios(RegistroDeCambios registro, ConfiguracionesF8 configuraciones, Pagina.Fabrica paginas) {
        this.registro = registro;
        this.configuraciones = configuraciones;
        this.paginas = paginas;
    }

    @GetMapping("/cambios-de-opinion")
    @Transactional(readOnly = true)
    public String cambios(HttpServletRequest request, Model modelo) {
        return pintar(Map.of(), Valores.vacios(), request, modelo);
    }

    private String pintar(Map<String, String> errores, Valores valores, HttpServletRequest request, Model modelo) {
        UsuarioSesion yo = paginas.usuarioActual();
        EjecutorCambiosOpinion.Config config = configuraciones.cambios(yo.id());
        RegistroDeCambios.Registro r = registro.registro(yo.id(), config);
        ResultadoCambiosOpinion c = r.cambios();
        List<SinRevisar> sinRevisar = c.sinRevisar().stream().map(s -> new SinRevisar(RenderizadoresF8.CambiosOpinion.sinRevisar(s),
                "/consejero?modo=debate&postura=" + URLEncoder.encode(s.postura(), StandardCharsets.UTF_8))).toList();
        modelo.addAttribute("pagina", paginas.crear("Cambios de opinión", request));
        modelo.addAttribute("v", new VistaCambios(RenderizadoresF8.CambiosOpinion.vista(Optional.empty(), "registro", c, Modo.COMPLETO, false),
                RenderizadoresF8.CambiosOpinion.tituloSinRevisar(c), sinRevisar, r.posturas(), config.causas(), config.registroAutomatico(), errores,
                valores, UUID.randomUUID().toString()));
        return "cambios";
    }

    /** Registrar un cambio a mano: una ejecución de T46 con el cambio y su causa (R05). */
    @PostMapping("/cambios-de-opinion")
    @Transactional
    public Object registrar(@RequestParam(required = false) String postura, @RequestParam(required = false) String antes,
                            @RequestParam(required = false) String despues, @RequestParam(required = false) String causa,
                            @RequestParam(name = "_clave", required = false) String clave, HtmxRequest htmx, HttpServletRequest request,
                            HttpServletResponse respuesta, Model modelo) {
        UsuarioSesion yo = paginas.usuarioActual();
        Valores valores = new Valores(postura == null ? "" : postura, antes == null ? "" : antes, despues == null ? "" : despues,
                causa == null ? "" : causa);
        Map<String, String> errores = new LinkedHashMap<>();
        Integer a = entero(antes);
        Integer d = entero(despues);
        Optional<CambioOpinion.Causa> c = causa(causa);
        if (a == null || d == null) {
            errores.put("nuevaDespues", "Escribe tu confianza antes y después, de 0 a 100.");
        }
        if (c.isEmpty()) {
            errores.put("nuevaCausa", "Elige una causa de las que tienes activas.");
        }
        if (errores.isEmpty()) {
            try {
                registro.registrarAMano(yo.id(), yo.institucionId(), configuraciones.cambios(yo.id()), postura, a, d, c.get(),
                        clave == null || clave.isBlank() || clave.length() > 64 ? UUID.randomUUID().toString() : clave);
                respuesta.setHeader("HX-Trigger", "ejecucion-guardada");
                return redirigir("/cambios-de-opinion", htmx, respuesta);
            } catch (RegistroDeCambios.NoPermitido e) {
                for (Validacion.Error err : e.errores()) {
                    errores.putIfAbsent(err.campo(), err.mensaje());
                }
            }
        }
        respuesta.setStatus(422);
        return pintar(errores, valores, request, modelo);
    }

    @GetMapping("/cambios-de-opinion/diario")
    @Transactional(readOnly = true)
    public String diario(HttpServletRequest request, Model modelo) {
        UsuarioSesion yo = paginas.usuarioActual();
        var config = configuraciones.diario(yo.id());
        Optional<V11> diario = registro.diario(yo.id(), config)
                .map(r -> RenderizadoresF8.DiarioRazonamiento.vista(Optional.empty(), "diario", r, Modo.COMPLETO));
        modelo.addAttribute("pagina", paginas.crear("Cambios de opinión", request));
        modelo.addAttribute("v", new VistaDiario(diario, config.semanas()));
        return "cambios-diario";
    }

    private static Integer entero(String valor) {
        try {
            int n = Integer.parseInt(valor == null ? "" : valor.strip());
            return n < 0 || n > 100 ? null : n;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Optional<CambioOpinion.Causa> causa(String valor) {
        try {
            return valor == null || valor.isBlank() ? Optional.empty() : Optional.of(CambioOpinion.Causa.de(valor.strip()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static ResponseEntity<Void> redirigir(String destino, HtmxRequest htmx, HttpServletResponse respuesta) {
        if (htmx.isHtmxRequest()) {
            respuesta.setHeader("HX-Redirect", destino);
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.status(303).location(URI.create(destino)).build();
    }
}
