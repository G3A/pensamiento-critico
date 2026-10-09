package pensamiento.web.patrones;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import gg.jte.Content;
import gg.jte.TemplateEngine;
import org.springframework.stereotype.Component;

import pensamiento.tecnicas.f4.ResultadoJerarquia;

/**
 * V10 para T23 · Jerarquía de evidencia: la barra de fuerza neta (R02), cada nivel de la jerarquía con sus evidencias y su
 * fuerza (R01), "ninguna encontrada" donde no hay. No da el estado de la afirmación: eso es R03.
 */
@Component
public class RenderizadorJerarquia implements RenderizadorResultado<ResultadoJerarquia> {

    private final TemplateEngine plantillas;

    public RenderizadorJerarquia(TemplateEngine plantillas) {
        this.plantillas = plantillas;
    }

    @Override
    public String patron() {
        return V10.PATRON;
    }

    @Override
    public Class<ResultadoJerarquia> tipo() {
        return ResultadoJerarquia.class;
    }

    @Override
    public Content render(Optional<UUID> idEjecucion, String sufijo, ResultadoJerarquia r, Modo modo) {
        List<V10.Item> items = new ArrayList<>();
        int n = 0;
        for (ResultadoJerarquia.Nivel nivel : r.niveles()) {
            n++;
            if (nivel.evidencias().isEmpty()) {
                items.add(new V10.Item("nivel-" + n, nivel.nombre(), "ninguna encontrada", "chip chip-pendiente", null, ""));
                continue;
            }
            for (String codigo : nivel.evidencias()) {
                ResultadoJerarquia.EvidenciaPesada e = r.evidencias().stream().filter(x -> x.codigo().equals(codigo)).findFirst().orElseThrow();
                items.add(new V10.Item(e.codigo(), nivel.nombre() + " · " + e.codigo(), e.postura() + " · fuerza " + e.fuerza(),
                        switch (e.postura()) {
                            case "apoya" -> "chip chip-ok";
                            case "contradice" -> "chip chip-aviso";
                            default -> "chip chip-pendiente";
                        }, e.descripcion(), "matiza".equals(e.postura()) ? "Matiza: no suma en la fuerza neta (R02)." : "Fuerza " + e.fuerza() + " (R01)"));
            }
        }
        String neta = r.neta() > 0 ? "+" + r.neta() : String.valueOf(r.neta());
        int maximo = Math.max(8, Math.abs(r.neta()));
        String clase = r.neta() > 0 ? "chip chip-ok" : r.neta() < 0 ? "chip chip-aviso" : "chip chip-pendiente";
        V10 v = new V10(idEjecucion, sufijo, modo, "Jerarquía de evidencia", "Afirmación: «" + r.afirmacion() + "»",
                List.of(new V10.Barra("neta", "Fuerza neta " + neta + " (" + r.magnitud() + ")", -maximo, maximo, r.neta())),
                r.porDiseno() ? "Por diseño del estudio" : "Por tipo de fuente", items,
                new V10.Veredicto("Fuerza neta (R02)", r.magnitud() + (r.sentido().isEmpty() ? "" : " " + r.sentido()), clase,
                        "Suma de las que apoyan menos las que contradicen: " + neta + "."),
                List.of(), List.of(), r.resumen(), "No toda evidencia pesa igual: la fuerza no dice si la afirmación es cierta, dice cuánto la sostiene lo que encontraste.");
        return salida -> plantillas.render("tag/v/v10.jte", Map.of("v", v), salida);
    }
}
