package pensamiento.web.dojo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.github.wimdeblauwe.htmx.spring.boot.mvc.HtmxRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import pensamiento.flujos.DojoDeRazonamiento;
import pensamiento.nucleo.BancoDojo;
import pensamiento.nucleo.NivelBloom;
import pensamiento.tecnicas.f8.ResultadoBloom;
import pensamiento.tecnicas.f8.TemaDojo;
import pensamiento.web.ConfiguracionesF8;
import pensamiento.web.ObjetoNoEncontrado;
import pensamiento.web.Pagina;
import pensamiento.web.patrones.Modo;
import pensamiento.web.patrones.RenderizadoresF8;
import pensamiento.web.patrones.V13b;
import pensamiento.web.patrones.V13c;
import pensamiento.web.seguridad.UsuarioSesion;

/**
 * P19 · Dojo de razonamiento (flujo B): el reto que toca, la respuesta calificada por reglas y el progreso por tema con el
 * calendario de repasos. Cada reto es un fragmento htmx; sin JavaScript, el formulario funciona con una página completa.
 */
@Controller
public class ControladorDojo {

    /** Un tema del filtro con su enlace. @param clave vacía para "Todos" */
    public record Tema(String clave, String nombre, boolean actual) {
        public String url() {
            return clave.isEmpty() ? "/dojo" : "/dojo?tema=" + clave;
        }
    }

    /** Lo que pinta P19: la pantalla del Dojo, la clave del formulario, el error y la respuesta, si la hay. */
    public record VistaDojo(DojoDeRazonamiento.Pantalla p, List<Tema> temas, String clave, String error, Optional<DojoDeRazonamiento.Respuesta> respuesta) {
        public String claveTema() {
            return p.filtro().map(TemaDojo::clave).orElse("");
        }

        public String urlSiguiente() {
            return p.filtro().map(t -> "/dojo?tema=" + t.clave()).orElse("/dojo");
        }
    }

    /** Progreso: un V13b por tema y el calendario V13c. */
    public record VistaProgreso(List<V13b> temas, V13c calendario, int racha) {
    }

    private final DojoDeRazonamiento dojo;
    private final ConfiguracionesF8 configuraciones;
    private final Pagina.Fabrica paginas;

    public ControladorDojo(DojoDeRazonamiento dojo, ConfiguracionesF8 configuraciones, Pagina.Fabrica paginas) {
        this.dojo = dojo;
        this.configuraciones = configuraciones;
        this.paginas = paginas;
    }

    @GetMapping("/dojo")
    @Transactional(readOnly = true)
    public String dojo(@RequestParam(required = false) String tema, @RequestParam(required = false) String nivel, HtmxRequest htmx,
                       HttpServletRequest request, Model modelo) {
        UsuarioSesion yo = paginas.usuarioActual();
        Optional<TemaDojo> filtro = tema(tema);
        DojoDeRazonamiento.Pantalla p = dojo.pantalla(yo.id(), filtro, nivel(nivel), configuraciones.dojo(yo.id()));
        modelo.addAttribute("pagina", paginas.crear("Dojo", request));
        modelo.addAttribute("v", new VistaDojo(p, temas(filtro), UUID.randomUUID().toString(), "", Optional.empty()));
        return htmx.isHtmxRequest() ? "fragmentos/dojo/reto" : "dojo";
    }

    /** Responder: se califica por reglas y se guarda el intento; el doble clic con la misma clave no guarda otro. */
    @PostMapping("/dojo/retos/{id}")
    @Transactional
    public String responder(@PathVariable String id, @RequestParam(required = false) String respuesta, @RequestParam(name = "_clave", required = false) String clave,
                            @RequestParam(required = false) String tema, @RequestParam(required = false) String nivel, HtmxRequest htmx,
                            HttpServletRequest request, HttpServletResponse salida, Model modelo) {
        UsuarioSesion yo = paginas.usuarioActual();
        Optional<TemaDojo> filtro = tema(tema);
        DojoDeRazonamiento.Configuracion config = configuraciones.dojo(yo.id());
        modelo.addAttribute("pagina", paginas.crear("Dojo", request));
        try {
            DojoDeRazonamiento.Respuesta r = dojo.responder(yo.id(), yo.institucionId(), id, respuesta, clave, config);
            DojoDeRazonamiento.Pantalla p = dojo.pantalla(yo.id(), filtro, nivel(nivel), config);
            modelo.addAttribute("v", new VistaDojo(p, temas(filtro), UUID.randomUUID().toString(), "", Optional.of(r)));
            return htmx.isHtmxRequest() ? "fragmentos/dojo/respuesta" : "dojo";
        } catch (DojoDeRazonamiento.NoEncontrado e) {
            throw new ObjetoNoEncontrado("reto");
        } catch (DojoDeRazonamiento.NoPermitido e) {
            // El mismo reto otra vez, con el error junto a las opciones.
            salida.setStatus(422);
            BancoDojo.Reto reto = dojo.banco().reto(id).orElseThrow(() -> new ObjetoNoEncontrado("reto"));
            BancoDojo.Concepto concepto = dojo.banco().concepto(reto.concepto()).orElseThrow();
            DojoDeRazonamiento.Pantalla actual = dojo.pantalla(yo.id(), filtro, nivel(nivel), config);
            DojoDeRazonamiento.Pantalla p = new DojoDeRazonamiento.Pantalla(actual.filtro(), actual.hechosHoy(), actual.limite(), actual.racha(),
                    Optional.of(new DojoDeRazonamiento.RetoElegido(reto, concepto, TemaDojo.de(concepto.idTecnica()), false)), Optional.empty(),
                    reto.nivel(), actual.progreso(), actual.avanceManual(), actual.nivelesActivos());
            modelo.addAttribute("v", new VistaDojo(p, temas(filtro), clave == null || clave.isBlank() ? UUID.randomUUID().toString() : clave,
                    e.getMessage(), Optional.empty()));
            return htmx.isHtmxRequest() ? "fragmentos/dojo/reto" : "dojo";
        }
    }

    @GetMapping("/dojo/progreso")
    @Transactional(readOnly = true)
    public String progreso(HttpServletRequest request, Model modelo) {
        UsuarioSesion yo = paginas.usuarioActual();
        DojoDeRazonamiento.Progreso p = dojo.progreso(yo.id(), configuraciones.dojo(yo.id()));
        List<V13b> temas = p.temas().stream().map((ResultadoBloom r) -> RenderizadoresF8.Bloom.vista(Optional.empty(), "dojo-" + r.tema().clave(), r,
                Modo.COMPLETO)).toList();
        modelo.addAttribute("pagina", paginas.crear("Dojo", request));
        modelo.addAttribute("v", new VistaProgreso(temas, RenderizadoresF8.Repeticion.vista(Optional.empty(), "dojo", p.calendario(), Modo.COMPLETO),
                p.racha()));
        return "dojo-progreso";
    }

    private static Optional<TemaDojo> tema(String clave) {
        if (clave == null || clave.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(TemaDojo.porClave(clave));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static Optional<NivelBloom> nivel(String valor) {
        if (valor == null || valor.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(NivelBloom.de(valor));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static List<Tema> temas(Optional<TemaDojo> filtro) {
        List<Tema> temas = new java.util.ArrayList<>();
        temas.add(new Tema("", "Todos", filtro.isEmpty()));
        for (TemaDojo t : TemaDojo.values()) {
            temas.add(new Tema(t.clave(), t.nombre(), filtro.equals(Optional.of(t))));
        }
        return temas;
    }
}
