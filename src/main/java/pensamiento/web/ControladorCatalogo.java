package pensamiento.web;

import java.text.Normalizer;
import java.util.ArrayList;
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
import pensamiento.nucleo.Ejemplo;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Tecnica;
import pensamiento.nucleo.puertos.RepositorioTecnica;
import pensamiento.web.tecnicas.HitosDeTecnicas;

/**
 * P03 · Catálogo (RF-04): las 49 fichas con nombre llano y "úsala cuando", filtrables por familia, por las seis
 * intenciones y por nombre, por "úsala cuando" o por el título de un ejemplo. Las técnicas sin ejecutor dicen en qué hito
 * llegan. Con la intención "Entender por qué pasó algo" arriba va por dónde empezar, porque en el ensayo de RF-08 dos de
 * seis se desviaron a T09 · 5 porqués y nadie distinguía T28 de T33.
 */
@Controller
public class ControladorCatalogo {

    /** Una tarjeta del catálogo. */
    public record Tarjeta(Tecnica tecnica, boolean disponible, int hito, int ejemplos) {
    }

    /** Un ejemplo cuyo título coincide con la búsqueda: se abre listo para probar. */
    public record EjemploEncontrado(Tecnica tecnica, Ejemplo ejemplo) {
    }

    /** Cuándo conviene una técnica frente a sus vecinas de la misma intención. */
    public record Guia(String cuando, Tecnica tecnica) {
    }

    /** Por dónde empezar en una intención: la técnica, su primer ejemplo y la guía para elegir entre las parecidas. */
    public record Recomendacion(Tecnica tecnica, Ejemplo ejemplo, List<Guia> guia) {
    }

    /** La guía de "Entender por qué pasó algo", en el orden en que se muestra. */
    private static final List<List<String>> GUIA_POR_QUE = List.of(
            List.of("T28", "Tienes varias ideas de por qué pasó y quieres ver cuál queda en pie"),
            List.of("T33", "Ya descartaste y todavía quedan dos: hay que elegir una"),
            List.of("T09", "Tienes una sola causa y quieres llegar al fondo"));

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
        List<EjemploEncontrado> encontrados = new ArrayList<>();
        if (!busqueda.isEmpty()) {
            String buscada = normalizar(busqueda);
            for (Tecnica t : lista) {
                tecnicas.ejemplos(t.id()).stream().filter(e -> normalizar(e.titulo()).contains(buscada)).findFirst()
                        .ifPresent(e -> encontrados.add(new EjemploEncontrado(t, e)));
            }
            lista = lista.stream().filter(t -> normalizar(t.nombre() + " " + t.nombreLlano() + " " + t.id().valor() + " " + t.usalaCuando())
                    .contains(buscada) || encontrados.stream().anyMatch(x -> x.tecnica().id().equals(t.id()))).toList();
        }
        Optional<Recomendacion> recomendacion = intencionElegida.filter(i -> i == Intencion.ENTENDER_POR_QUE_PASO_ALGO && busqueda.isEmpty())
                .flatMap(i -> recomendacionPorQue());
        if (recomendacion.isPresent()) {
            List<IdTecnica> primero = GUIA_POR_QUE.stream().map(g -> IdTecnica.de(g.getFirst())).toList();
            List<Tecnica> ordenada = new ArrayList<>(lista.stream().filter(t -> primero.contains(t.id()))
                    .sorted(java.util.Comparator.comparingInt(t -> primero.indexOf(t.id()))).toList());
            lista.stream().filter(t -> !primero.contains(t.id())).forEach(ordenada::add);
            lista = ordenada;
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
        modelo.addAttribute("recomendacion", recomendacion.orElse(null));
        modelo.addAttribute("encontrados", encontrados);
        return htmx.isHtmxRequest() ? "fragmentos/rejilla-catalogo" : "catalogo";
    }

    private Optional<Recomendacion> recomendacionPorQue() {
        List<Guia> guia = new ArrayList<>();
        for (List<String> g : GUIA_POR_QUE) {
            tecnicas.porId(IdTecnica.de(g.getFirst())).ifPresent(t -> guia.add(new Guia(g.get(1), t)));
        }
        Optional<Tecnica> t28 = tecnicas.porId(IdTecnica.de("T28")).filter(t -> ejecutores.tiene(t.id()) && !t.estaPendiente());
        return t28.flatMap(t -> tecnicas.ejemplos(t.id()).stream().findFirst().map(e -> new Recomendacion(t, e, guia)));
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
