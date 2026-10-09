package pensamiento.web.patrones;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import gg.jte.Content;
import gg.jte.TemplateEngine;
import org.springframework.stereotype.Component;

import pensamiento.tecnicas.f4.ResultadoCraap;

/**
 * V10 para T21 · CRAAP: con una fuente, una barra por criterio (0 a 5) y la del puntaje (0 a 25); con varias, la barra del
 * puntaje de cada una y sus criterios en el detalle. El veredicto es "aprobada" o "no aprobada" contra el umbral.
 */
@Component
public class RenderizadorCraap implements RenderizadorResultado<ResultadoCraap> {

    private final TemplateEngine plantillas;

    public RenderizadorCraap(TemplateEngine plantillas) {
        this.plantillas = plantillas;
    }

    @Override
    public String patron() {
        return V10.PATRON;
    }

    @Override
    public Class<ResultadoCraap> tipo() {
        return ResultadoCraap.class;
    }

    @Override
    public Content render(Optional<UUID> idEjecucion, String sufijo, ResultadoCraap r, Modo modo) {
        List<V10.Barra> barras = new ArrayList<>();
        if (r.fuentes().size() == 1) {
            ResultadoCraap.FuenteEvaluada f = r.fuentes().getFirst();
            for (ResultadoCraap.Criterio c : f.criterios()) {
                barras.add(new V10.Barra(clave(c.nombre()), c.nombre() + " " + c.valor() + (c.peso() == 1 ? "" : " · peso " + c.peso()), 0, 5, c.valor()));
            }
            barras.add(new V10.Barra("puntaje", "Puntaje " + f.puntaje() + " de 25 · umbral " + r.umbral(), 0, 25, f.puntaje()));
        } else {
            for (ResultadoCraap.FuenteEvaluada f : r.fuentes()) {
                barras.add(new V10.Barra(f.codigo().toLowerCase(), f.codigo() + " · " + f.puntaje() + " de 25", 0, 25, f.puntaje()));
            }
        }
        List<V10.Item> items = r.fuentes().stream().map(f -> new V10.Item(f.codigo(), f.codigo() + " · " + f.titulo()
                + (f.codigo().equals(r.mejor()) ? " (la mejor)" : ""), f.linea(), f.aprobada() ? "chip chip-ok" : "chip chip-aviso",
                criterios(f) + (f.nota() == null ? "" : " · " + f.nota()), f.debil())).toList();
        V10.Veredicto veredicto = null;
        if (r.fuentes().size() == 1) {
            ResultadoCraap.FuenteEvaluada f = r.fuentes().getFirst();
            veredicto = new V10.Veredicto("Con umbral " + r.umbral(), f.aprobada() ? "aprobada" : "no aprobada",
                    f.aprobada() ? "chip chip-ok" : "chip chip-aviso", f.puntaje() + " de 25. " + f.debil());
        }
        V10 v = new V10(idEjecucion, sufijo, modo, "CRAAP", r.uso() == null ? null : "Uso: " + r.uso(), barras, "Fuentes", items, veredicto,
                List.of(), List.of(), r.resumen(), "El puntaje compara fuentes para tu pregunta; no dice si lo que afirman es cierto.");
        return salida -> plantillas.render("tag/v/v10.jte", Map.of("v", v), salida);
    }

    private static String criterios(ResultadoCraap.FuenteEvaluada f) {
        return String.join(" · ", f.criterios().stream().map(c -> c.nombre() + " " + c.valor()).toList());
    }

    private static String clave(String criterio) {
        return criterio.replace("ó", "o");
    }
}
