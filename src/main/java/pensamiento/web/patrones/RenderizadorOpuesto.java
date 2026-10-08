package pensamiento.web.patrones;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import gg.jte.Content;
import gg.jte.TemplateEngine;
import org.springframework.stereotype.Component;

import pensamiento.tecnicas.f3.ResultadoOpuesto;

/** V04 para T15 · Considera lo opuesto: la postura propia frente a las opuestas, con qué cambiaría y la confianza. */
@Component
public class RenderizadorOpuesto implements RenderizadorResultado<ResultadoOpuesto> {

    private final TemplateEngine plantillas;

    public RenderizadorOpuesto(TemplateEngine plantillas) {
        this.plantillas = plantillas;
    }

    @Override
    public String patron() {
        return V04.PATRON;
    }

    @Override
    public Class<ResultadoOpuesto> tipo() {
        return ResultadoOpuesto.class;
    }

    @Override
    public Content render(Optional<UUID> idEjecucion, String sufijo, ResultadoOpuesto r, Modo modo) {
        List<V04.Item> derecha = r.opuestas().stream().map(o -> new V04.Item(o.texto(),
                o.delModelo() ? "del modelo · adoptada por ti" : o.falta() != null ? "falta qué cambiaría" : "completa",
                o.delModelo() ? "chip chip-ok" : o.falta() != null ? "chip chip-pendiente" : "chip",
                o.queCambiaria() != null ? "Qué cambiaría: " + o.queCambiaria() : o.falta())).toList();
        List<String> preguntas = new ArrayList<>();
        if (r.faltantes() != null) {
            preguntas.add(r.faltantes());
        }
        V04 v = new V04(idEjecucion, sufijo, modo, "Mi postura y su opuesta", null,
                new V04.Columna("Mi postura", List.of(new V04.Item(r.postura(), null, null, null)), ""),
                new V04.Columna("Postura opuesta", derecha, "Todavía no hay postura opuesta."),
                new V04.Estado("Confianza", r.confianza(), "chip", null), "Lo que falta", preguntas, r.propuestas(), r.resumen(),
                "Considerar lo opuesto no te obliga a cambiar de opinión: deja escrito qué la cambiaría.");
        return salida -> plantillas.render("tag/v/v04.jte", Map.of("v", v), salida);
    }
}
