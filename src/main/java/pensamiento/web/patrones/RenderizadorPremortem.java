package pensamiento.web.patrones;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import gg.jte.Content;
import gg.jte.TemplateEngine;
import org.springframework.stereotype.Component;

import pensamiento.tecnicas.f5.ResultadoPremortem;

/** V13a para T29 · Pre-mortem: las causas de más a menos probable, cada una con su mitigación o lo que falta. */
@Component
public class RenderizadorPremortem implements RenderizadorResultado<ResultadoPremortem> {

    private final TemplateEngine plantillas;

    public RenderizadorPremortem(TemplateEngine plantillas) {
        this.plantillas = plantillas;
    }

    @Override
    public String patron() {
        return V13a.PATRON;
    }

    @Override
    public Class<ResultadoPremortem> tipo() {
        return ResultadoPremortem.class;
    }

    @Override
    public Content render(Optional<UUID> idEjecucion, String sufijo, ResultadoPremortem r, Modo modo) {
        List<V13a.Item> items = r.causas().stream().map(c -> new V13a.Item(c.afirmacionId().toString(), c.texto(), c.probabilidad(),
                "alta".equals(c.probabilidad()) ? "chip chip-aviso" : "media".equals(c.probabilidad()) ? "chip chip-pendiente" : "chip",
                (switch (c.estado()) {
                    case CON_MITIGACION -> "mitigación: " + c.mitigacion();
                    case FALTA_MITIGACION -> "falta la mitigación";
                    case SIN_MITIGACION -> "sin mitigación";
                }) + (c.categoria() == null ? "" : " · " + c.categoria()))).toList();
        V13a v = new V13a(idEjecucion, sufijo, modo, "Pre-mortem", r.enunciado(), items, "Para tener en cuenta", r.avisos(), r.resumen(),
                "Imaginar el fracaso no lo predice: sirve para encontrar a tiempo lo que puedes prevenir.");
        return salida -> plantillas.render("tag/v/v13a.jte", Map.of("v", v), salida);
    }
}
