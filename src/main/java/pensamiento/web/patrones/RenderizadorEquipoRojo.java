package pensamiento.web.patrones;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import gg.jte.Content;
import gg.jte.TemplateEngine;
import org.springframework.stereotype.Component;

import pensamiento.tecnicas.f6.ResultadoEquipoRojo;

/**
 * V09 para T36 · Equipo rojo / abogado del diablo: cada ataque con su respuesta y, al lado, la debilidad que el código
 * identificó en cada razón, con las preguntas críticas y la falacia que les corresponde si quedan sin respuesta (R06).
 */
@Component
public class RenderizadorEquipoRojo implements RenderizadorResultado<ResultadoEquipoRojo> {

    private final TemplateEngine plantillas;

    public RenderizadorEquipoRojo(TemplateEngine plantillas) {
        this.plantillas = plantillas;
    }

    @Override
    public String patron() {
        return V09.PATRON;
    }

    @Override
    public Class<ResultadoEquipoRojo> tipo() {
        return ResultadoEquipoRojo.class;
    }

    @Override
    public Content render(Optional<UUID> idEjecucion, String sufijo, ResultadoEquipoRojo r, Modo modo) {
        return salida -> plantillas.render("tag/v/v09.jte", Map.of("v", vista(idEjecucion, sufijo, r, modo)), salida);
    }

    public static V09 vista(Optional<UUID> idEjecucion, String sufijo, ResultadoEquipoRojo r, Modo modo) {
        List<V09.Burbuja> burbujas = new ArrayList<>();
        burbujas.add(new V09.Burbuja("postura", "persona", "tú · postura", r.postura(), List.of(), null));
        int n = r.ataques().size();
        for (int i = 0; i < n; i++) {
            ResultadoEquipoRojo.Ataque a = r.ataques().get(i);
            String origen = switch (a.origen()) {
                case "modelo" -> "del modelo · adoptado";
                case "persona" -> "escrito a mano";
                default -> "del banco";
            };
            burbujas.add(new V09.Burbuja(a.codigo(), "equipo-rojo", "equipo rojo · ataque " + (i + 1) + " de " + n + " · a " + a.razon(), a.texto(),
                    List.of(origen), null));
            burbujas.add(new V09.Burbuja("r-" + a.codigo(), "persona", "tú · respuesta a " + a.codigo(),
                    a.respuesta() == null ? "Sin responder todavía." : a.respuesta(), a.respondido() ? List.of() : List.of("sin responder"), null));
        }
        List<V09.SeccionPanel> panel = new ArrayList<>();
        for (ResultadoEquipoRojo.Debilidad d : r.debilidades()) {
            List<V09.ItemPanel> items = new ArrayList<>();
            items.add(new V09.ItemPanel(d.razon(), d.texto(), d.esquema() == null ? "sin esquema" : d.esquemaNombre(),
                    d.esquema() == null ? "chip chip-aviso" : "chip", null, d.deDonde()));
            for (ResultadoEquipoRojo.Pregunta q : d.preguntas()) {
                items.add(new V09.ItemPanel(d.razon() + "-" + q.numero(), q.texto(), "pregunta " + q.numero(), "chip", null,
                        "Si queda sin respuesta: " + q.falacia() + " (la confirmas tú en T13)."));
            }
            panel.add(new V09.SeccionPanel(d.razon(), items));
        }
        String tarjeta = "El equipo rojo ataca a propósito: que un ataque no tenga respuesta no prueba que la postura sea falsa, señala dónde falta una razón.";
        return new V09(idEjecucion, sufijo, modo, "Equipo rojo · " + modoLegible(r.modo()), null, burbujas, null, "Debilidades que identificó el código",
                panel, List.of(), r.propuestas(), r.resumen(), tarjeta);
    }

    private static String modoLegible(String modo) {
        return switch (modo) {
            case "banco_y_modelo" -> "banco y modelo";
            case "a_mano" -> "ataques escritos a mano";
            default -> "banco por esquema de Walton";
        };
    }
}
