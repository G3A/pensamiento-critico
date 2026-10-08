package pensamiento.web.patrones;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import gg.jte.Content;
import gg.jte.TemplateEngine;
import org.springframework.stereotype.Component;

import pensamiento.tecnicas.f5.ResultadoInversion;

/** V04 para T30 · Inversión: cómo fracasar a la izquierda y la acción contraria a la derecha, en el mismo orden. */
@Component
public class RenderizadorInversion implements RenderizadorResultado<ResultadoInversion> {

    private final TemplateEngine plantillas;

    public RenderizadorInversion(TemplateEngine plantillas) {
        this.plantillas = plantillas;
    }

    @Override
    public String patron() {
        return V04.PATRON;
    }

    @Override
    public Class<ResultadoInversion> tipo() {
        return ResultadoInversion.class;
    }

    @Override
    public Content render(Optional<UUID> idEjecucion, String sufijo, ResultadoInversion r, Modo modo) {
        List<V04.Item> formas = r.pares().stream().map(p -> new V04.Item(p.forma(), null, null, null)).toList();
        List<V04.Item> contrarias = r.pares().stream().map(p -> p.contraria() != null ? new V04.Item(p.contraria(), null, null, null)
                : new V04.Item("—", r.contrariaExigida() ? "falta la acción contraria" : "sin acción contraria",
                r.contrariaExigida() ? "chip chip-aviso" : "chip", "Para: " + p.forma())).toList();
        V04 v = new V04(idEjecucion, sufijo, modo, "Inversión", r.enunciado(), new V04.Columna("Cómo fracasar", formas, ""),
                new V04.Columna("Acción contraria", contrarias, ""),
                new V04.Estado("Estado", r.completa() ? "completa" : "incompleta", r.completa() ? "chip chip-ok" : "chip chip-aviso",
                        r.completa() ? null : r.estado().substring("incompleta: ".length())),
                "Lo que falta", r.avisos(), List.of(), r.resumen(), "Evitar los errores tontos suele rendir más que buscar la jugada brillante.");
        return salida -> plantillas.render("tag/v/v04.jte", Map.of("v", v), salida);
    }
}
