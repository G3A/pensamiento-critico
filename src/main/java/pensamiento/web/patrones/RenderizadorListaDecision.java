package pensamiento.web.patrones;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import gg.jte.Content;
import gg.jte.TemplateEngine;
import org.springframework.stereotype.Component;

import pensamiento.tecnicas.f3.ResultadoListaDecision;

/** V02 para T16 · Lista de verificación antes de decidir: cada ítem con su estado, el bloqueo del guardado y la firma. */
@Component
public class RenderizadorListaDecision implements RenderizadorResultado<ResultadoListaDecision> {

    private final TemplateEngine plantillas;

    public RenderizadorListaDecision(TemplateEngine plantillas) {
        this.plantillas = plantillas;
    }

    @Override
    public String patron() {
        return V02.PATRON;
    }

    @Override
    public Class<ResultadoListaDecision> tipo() {
        return ResultadoListaDecision.class;
    }

    @Override
    public Content render(Optional<UUID> idEjecucion, String sufijo, ResultadoListaDecision r, Modo modo) {
        List<V02.Item> items = r.items().stream().map(i -> new V02.Item(i.item().toString(), i.pregunta(), i.estado().texto(),
                switch (i.estado()) {
                    case RESPONDIDO -> "chip chip-ok";
                    case FALTA_OBLIGATORIO -> "chip chip-aviso";
                    case SIN_RESPONDER -> "chip chip-pendiente";
                }, i.estado().toString(), i.respuesta(), "", null)).toList();
        List<V02.Aviso> avisos = new ArrayList<>();
        if (r.bloqueo() != null) {
            avisos.add(new V02.Aviso(r.bloqueo(), "aviso"));
        }
        avisos.add(new V02.Aviso(r.firma(), "nota"));
        String tarjeta = r.respondidos() == r.items().size()
                ? "Respondiste la lista. Eso no hace buena la decisión: hace visible lo que revisaste."
                : "Lo que falta queda a la vista antes de decidir.";
        V02 v = new V02(idEjecucion, sufijo, modo, "Antes de decidir", "Decisión: «" + r.decision() + "»",
                List.of(new V02.Medidor("respondidos", "Respondidos " + r.respondidos() + " de " + r.items().size(), r.respondidos(), r.items().size())),
                List.of(new V02.Seccion(null, items)), avisos, List.of(), r.resumen(), tarjeta);
        return salida -> plantillas.render("tag/v/v02.jte", Map.of("v", v), salida);
    }
}
