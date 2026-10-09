package pensamiento.web.patrones;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import gg.jte.Content;
import gg.jte.TemplateEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import pensamiento.nucleo.puertos.Grafico;
import pensamiento.tecnicas.f2.ResultadoCincoPorques;

/**
 * V06 para T09 · 5 porqués: Graphviz dibuja la cadena (fuera de la transacción, al escribir la plantilla) y la lista la
 * repite con su sangría, su estado y la evidencia de cada nivel.
 */
@Component
public class RenderizadorCincoPorques implements RenderizadorResultado<ResultadoCincoPorques> {

    private static final Logger LOG = LoggerFactory.getLogger(RenderizadorCincoPorques.class);

    private final TemplateEngine plantillas;
    private final Grafico grafico;

    public RenderizadorCincoPorques(TemplateEngine plantillas, Grafico grafico) {
        this.plantillas = plantillas;
        this.grafico = grafico;
    }

    @Override
    public String patron() {
        return V06.PATRON;
    }

    @Override
    public Class<ResultadoCincoPorques> tipo() {
        return ResultadoCincoPorques.class;
    }

    @Override
    public Content render(Optional<UUID> idEjecucion, String sufijo, ResultadoCincoPorques r, Modo modo) {
        return salida -> plantillas.render("tag/v/v06.jte", Map.of("v", vista(idEjecucion, sufijo, r, modo, dibujar(r))), salida);
    }

    static V06 vista(Optional<UUID> idEjecucion, String sufijo, ResultadoCincoPorques r, Modo modo, Optional<String> svg) {
        List<V06.Item> items = new ArrayList<>();
        items.add(new V06.Item(r.problemaId().toString(), "Problema", r.problema(), null, null, 0));
        agregarHijos(items, r, null);
        List<String> evidencia = r.porques().stream().map(p -> p.codigo() + " · " + (p.evidencia() == null ? "sin evidencia" : p.evidencia())).toList();
        List<String> raices = r.porques().stream().filter(p -> p.estado() == ResultadoCincoPorques.Estado.CAUSA_RAIZ)
                .map(p -> p.codigo() + " · " + p.texto()).toList();
        List<V06.Seccion> secciones = List.of(new V06.Seccion("Causas raíz", raices.isEmpty() ? List.of("Todavía ninguna.") : raices),
                new V06.Seccion("Evidencia por nivel: " + r.conEvidencia() + " de " + r.porques().size(), evidencia));
        String tarjeta = "Una causa raíz es una hipótesis hasta que la compruebes: cada una queda como pendiente de verificación.";
        return new V06(idEjecucion, sufijo, modo, "5 porqués", null, svg, "Cadena de porqués dibujada; la lista de abajo dice lo mismo con texto",
                "Cadena", items, secciones, r.avisos(), r.resumen(), tarjeta);
    }

    private static void agregarHijos(List<V06.Item> items, ResultadoCincoPorques r, String padre) {
        for (ResultadoCincoPorques.Porque p : r.porques()) {
            if (java.util.Objects.equals(p.padre(), padre)) {
                String chip = p.estado() == ResultadoCincoPorques.Estado.INTERMEDIO ? null : p.estado().texto();
                String clase = p.estado() == ResultadoCincoPorques.Estado.CAUSA_RAIZ ? "chip chip-ok"
                        : p.estado() == ResultadoCincoPorques.Estado.SIN_TERMINAR ? "chip chip-pendiente" : null;
                items.add(new V06.Item(p.afirmacionId().toString(), p.codigo(), p.texto(), chip, clase, p.nivel()));
                agregarHijos(items, r, p.codigo());
            }
        }
    }

    private Optional<String> dibujar(ResultadoCincoPorques r) {
        try {
            return Optional.of(grafico.svg(GeneradorDotDiagramas.porques(r)));
        } catch (Grafico.GraficoInvalido | Grafico.GraficoTiempoAgotado e) {
            LOG.warn("Graphviz no dibujó los 5 porqués; se muestra solo la lista: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
