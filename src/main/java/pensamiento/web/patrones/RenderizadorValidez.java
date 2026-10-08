package pensamiento.web.patrones;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import gg.jte.Content;
import gg.jte.TemplateEngine;
import org.springframework.stereotype.Component;

import pensamiento.tecnicas.f1.ResultadoValidez;

/**
 * V10 para T05 · Validez y solidez: tipo, forma y solidez, cada uno con su estado en texto y su frase; nunca "válido"
 * ni "sólido" (corrección 13). Las premisas dicen si la persona las marcó establecidas.
 */
@Component
public class RenderizadorValidez implements RenderizadorResultado<ResultadoValidez> {

    private final TemplateEngine plantillas;

    public RenderizadorValidez(TemplateEngine plantillas) {
        this.plantillas = plantillas;
    }

    @Override
    public String patron() {
        return V10.PATRON;
    }

    @Override
    public Class<ResultadoValidez> tipo() {
        return ResultadoValidez.class;
    }

    @Override
    public Content render(Optional<UUID> idEjecucion, String sufijo, ResultadoValidez r, Modo modo) {
        List<V10.Item> items = new ArrayList<>();
        for (int i = 0; i < r.premisas().size(); i++) {
            ResultadoValidez.PremisaEvaluada p = r.premisas().get(i);
            items.add(new V10.Item("P" + (i + 1), "P" + (i + 1), p.establecida() ? "establecida" : "sin establecer",
                    p.establecida() ? "chip chip-ok" : "chip chip-pendiente", p.texto(), null));
        }
        items.add(new V10.Item("tipo", "Tipo", r.tipo().texto(), "chip", r.tipoDetectado() ? "detectado por las palabras del argumento" : "el que elegiste", null));
        items.add(new V10.Item("forma", "Forma", r.estadoForma(), claseForma(r.estadoForma()), null, r.fraseForma()));
        items.add(new V10.Item("solidez", "Solidez", r.estadoSolidez(), claseSolidez(r.estadoSolidez()), null,
                r.preguntas().isEmpty() ? r.fraseSolidez() : null));
        V10 v = new V10(idEjecucion, sufijo, modo, "¿Se sigue y son ciertas?", "Conclusión: «" + r.conclusion() + "»", List.of(),
                "Premisas, forma y solidez", items, null, r.preguntas(), List.of(), r.resumen(),
                "La app no decide si la conclusión se sigue: lo respondiste tú. Una premisa establecida hoy puede dejar de estarlo.");
        return salida -> plantillas.render("tag/v/v10.jte", Map.of("v", v), salida);
    }

    private static String claseForma(String estado) {
        return switch (estado) {
            case "se sigue", "fuerte" -> "chip chip-ok";
            case "sin responder" -> "chip chip-pendiente";
            default -> "chip chip-aviso";
        };
    }

    private static String claseSolidez(String estado) {
        return switch (estado) {
            case "establecida por ti" -> "chip chip-ok";
            case "sin establecer" -> "chip chip-pendiente";
            default -> "chip";
        };
    }
}
