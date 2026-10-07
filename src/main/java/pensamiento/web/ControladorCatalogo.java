package pensamiento.web;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import io.github.wimdeblauwe.htmx.spring.boot.mvc.HtmxRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import pensamiento.catalogo.Intencion;
import pensamiento.catalogo.RegistroEjecutores;
import pensamiento.nucleo.Tecnica;
import pensamiento.nucleo.puertos.RepositorioTecnica;
import pensamiento.web.tecnicas.HitosDeTecnicas;

/**
 * P03 · Catálogo (RF-04): las 49 fichas con nombre llano y "úsala cuando", filtrables por familia, por las seis
 * intenciones y por nombre. Las técnicas sin ejecutor dicen en qué hito llegan.
 */
@Controller
public class ControladorCatalogo {

    /** Una tarjeta del catálogo. */
    public record Tarjeta(Tecnica tecnica, boolean disponible, int hito, int ejemplos) {
    }

    /** Filtros vigentes, para pintar los enlaces y el texto de "filtrado por". */
    public record Filtros(String familia, String intencion, String tituloIntencion, String q) {
        public boolean alguno() {
            return !familia.isEmpty() || !intencion.isEmpty() || !q.isEmpty();
        }

        public String conFamilia(String codigo) {
            StringBuilder url = new StringBuilder("/catalogo?familia=").append(codigo);
            if (!intencion.isEmpty()) {
                url.append("&intencion=").append(intencion);
            }
            return url.toString();
        }
    }

    private final RepositorioTecnica tecnicas;
    private final RegistroEjecutores ejecutores;
    private final Pagina.Fabrica paginas;

    public ControladorCatalogo(RepositorioTecnica tecnicas, RegistroEjecutores ejecutores, Pagina.Fabrica paginas) {
        this.tecnicas = tecnicas;
        this.ejecutores = ejecutores;
        this.paginas = paginas;
    }

    @GetMapping("/catalogo")
    @Transactional(readOnly = true)
    public String catalogo(@RequestParam(required = false) String familia,
                           @RequestParam(required = false) String intencion,
                           @RequestParam(required = false) String q,
                           HtmxRequest htmx, HttpServletRequest request, Model modelo) {
        Optional<Intencion> intencionElegida = Optional.ofNullable(intencion).filter(s -> !s.isBlank()).flatMap(ControladorCatalogo::intencion);
        String familiaElegida = familia == null ? "" : familia.trim();
        String busqueda = q == null ? "" : q.trim();
        List<Tecnica> lista = familiaElegida.isEmpty() ? tecnicas.todas() : tecnicas.porFamilia(familiaElegida);
        if (intencionElegida.isPresent()) {
            lista = lista.stream().filter(intencionElegida.get()::incluye).toList();
        }
        if (!busqueda.isEmpty()) {
            String buscada = normalizar(busqueda);
            lista = lista.stream().filter(t -> normalizar(t.nombre() + " " + t.nombreLlano() + " " + t.id().valor()).contains(buscada)).toList();
        }
        List<Tarjeta> tarjetas = lista.stream().map(t -> new Tarjeta(t, ejecutores.tiene(t.id()) && !t.estaPendiente(),
                HitosDeTecnicas.hito(t.id()), tecnicas.ejemplos(t.id()).size())).toList();
        Filtros filtros = new Filtros(familiaElegida, intencionElegida.map(Intencion::clave).orElse(""),
                intencionElegida.map(Intencion::titulo).orElse(""), busqueda);
        modelo.addAttribute("pagina", paginas.crear("Catálogo", request));
        modelo.addAttribute("familias", tecnicas.familias());
        modelo.addAttribute("intenciones", Intencion.values());
        modelo.addAttribute("tarjetas", tarjetas);
        modelo.addAttribute("filtros", filtros);
        modelo.addAttribute("total", tecnicas.contar());
        return htmx.isHtmxRequest() ? "fragmentos/rejilla-catalogo" : "catalogo";
    }

    private static Optional<Intencion> intencion(String clave) {
        try {
            return Optional.of(Intencion.porClave(clave));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** Sin tildes ni mayúsculas: "analisis" encuentra "Análisis". */
    static String normalizar(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
