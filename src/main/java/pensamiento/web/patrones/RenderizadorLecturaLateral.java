package pensamiento.web.patrones;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import gg.jte.Content;
import gg.jte.TemplateEngine;
import org.springframework.stereotype.Component;

import pensamiento.tecnicas.f4.ResultadoLecturaLateral;

/**
 * V10 para T20 · Lectura lateral: la barra de fuentes externas que cuentan contra el mínimo, cada fuente externa con su
 * postura y si es independiente, y el veredicto. "Respaldada" es por las fuentes revisadas, nunca "verdadera".
 */
@Component
public class RenderizadorLecturaLateral implements RenderizadorResultado<ResultadoLecturaLateral> {

    private final TemplateEngine plantillas;

    public RenderizadorLecturaLateral(TemplateEngine plantillas) {
        this.plantillas = plantillas;
    }

    @Override
    public String patron() {
        return V10.PATRON;
    }

    @Override
    public Class<ResultadoLecturaLateral> tipo() {
        return ResultadoLecturaLateral.class;
    }

    @Override
    public Content render(Optional<UUID> idEjecucion, String sufijo, ResultadoLecturaLateral r, Modo modo) {
        List<V10.Item> items = r.externas().stream().map(e -> new V10.Item(e.codigo(), e.codigo() + " · " + e.nombre(),
                e.postura() + (e.cuenta() ? " · cuenta" : " · no cuenta"), e.cuenta() ? "chip chip-ok" : "chip chip-pendiente",
                e.dice(), e.independiente() ? "Independiente de la original" : "No es independiente de la original")).toList();
        String clase = switch (r.veredicto()) {
            case "respaldada" -> "chip chip-ok";
            case "en duda", "dividida" -> "chip chip-aviso";
            default -> "chip chip-pendiente";
        };
        List<String> lineas = new ArrayList<>();
        if (r.nota() != null) {
            lineas.add(r.nota());
        }
        int total = r.externas().size();
        V10 v = new V10(idEjecucion, sufijo, modo, "Lectura lateral", "La fuente: " + r.original() + " · afirma: «" + r.afirmacion() + "»",
                List.of(new V10.Barra("cuentan", "Cuentan " + r.cuentan() + " de " + total + " · mínimo " + r.minimo(), 0, Math.max(total, r.minimo()),
                        r.cuentan())),
                "Fuentes externas", items, new V10.Veredicto("Veredicto", r.veredicto(), clase, r.motivo()), lineas, List.of(), r.resumen(),
                "La lectura lateral no decide si es cierta: dice qué dicen de ella fuentes de afuera.");
        return salida -> plantillas.render("tag/v/v10.jte", Map.of("v", v), salida);
    }
}
