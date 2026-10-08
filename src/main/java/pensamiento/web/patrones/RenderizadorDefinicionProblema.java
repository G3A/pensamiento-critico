package pensamiento.web.patrones;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import gg.jte.Content;
import gg.jte.TemplateEngine;
import org.springframework.stereotype.Component;

import pensamiento.tecnicas.f7.ResultadoDefinicionProblema;

/** V13a para T40 · Definición del problema: la elegida primero, luego la original y las descartadas, con sus opciones. */
@Component
public class RenderizadorDefinicionProblema implements RenderizadorResultado<ResultadoDefinicionProblema> {

    private final TemplateEngine plantillas;

    public RenderizadorDefinicionProblema(TemplateEngine plantillas) {
        this.plantillas = plantillas;
    }

    @Override
    public String patron() {
        return V13a.PATRON;
    }

    @Override
    public Class<ResultadoDefinicionProblema> tipo() {
        return ResultadoDefinicionProblema.class;
    }

    @Override
    public Content render(Optional<UUID> idEjecucion, String sufijo, ResultadoDefinicionProblema r, Modo modo) {
        List<V13a.Item> items = new ArrayList<>();
        for (int i = 0; i < r.items().size(); i++) {
            ResultadoDefinicionProblema.Item it = r.items().get(i);
            String clase = switch (it.estado()) {
                case ELEGIDA -> "chip chip-ok";
                case ORIGINAL -> "chip";
                case DESCARTADA -> "chip chip-pendiente";
            };
            List<String> detalle = new ArrayList<>();
            if (it.opciones() != null) {
                detalle.add("abre " + it.opciones() + (it.opciones() == 1 ? " opción" : " opciones"));
            }
            if (it.plantilla() != null) {
                detalle.add("plantilla " + it.plantilla());
            }
            items.add(new V13a.Item("r" + i, it.texto(), it.estado().toString(), clase,
                    detalle.isEmpty() ? "sin número de opciones" : String.join(" · ", detalle)));
        }
        List<String> notas = new ArrayList<>(r.avisos());
        notas.add(r.plantillas());
        String tarjeta = r.comparacion() != null && r.avisos().isEmpty() ? r.comparacion()
                : "Resolver la pregunta equivocada bien es peor que resolver la correcta a medias.";
        V13a v = new V13a(idEjecucion, sufijo, modo, "Definición del problema", "Problema como llegó: «" + r.original() + "»", items,
                "Para tener en cuenta", notas, r.resumen(), tarjeta);
        return salida -> plantillas.render("tag/v/v13a.jte", Map.of("v", v), salida);
    }
}
