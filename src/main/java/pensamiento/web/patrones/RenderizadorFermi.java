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
import pensamiento.tecnicas.f5.ResultadoFermi;

/** V06 para T26 · Estimación de Fermi: la cadena de factores dibujada con Graphviz y repetida como lista con su rango. */
@Component
public class RenderizadorFermi implements RenderizadorResultado<ResultadoFermi> {

    private static final Logger LOG = LoggerFactory.getLogger(RenderizadorFermi.class);

    private final TemplateEngine plantillas;
    private final Grafico grafico;

    public RenderizadorFermi(TemplateEngine plantillas, Grafico grafico) {
        this.plantillas = plantillas;
        this.grafico = grafico;
    }

    @Override
    public String patron() {
        return V06.PATRON;
    }

    @Override
    public Class<ResultadoFermi> tipo() {
        return ResultadoFermi.class;
    }

    @Override
    public Content render(Optional<UUID> idEjecucion, String sufijo, ResultadoFermi r, Modo modo) {
        List<V06.Item> items = new ArrayList<>();
        for (ResultadoFermi.FactorEn f : r.factores()) {
            items.add(new V06.Item(f.afirmacionId().toString(), f.codigo(), f.texto() + ": " + f.rango(), f.masAncho() ? "el más incierto" : null,
                    "chip chip-aviso", 1));
        }
        items.add(new V06.Item(r.resultadoId().toString(), "=", "Entre " + r.minimo() + " y " + r.maximo() + " " + r.unidad() + ", central " + r.central(),
                "estimación", "chip", 0));
        List<String> referencia = r.referencia() == null ? List.of()
                : List.of(r.referencia() + (r.deDondeReferencia() == null ? "" : " Referencia: " + r.deDondeReferencia() + "."));
        V06 v = new V06(idEjecucion, sufijo, modo, "Estimación de Fermi", r.pregunta(), dibujar(r),
                "Cadena de factores dibujada; la lista de abajo dice lo mismo con texto", "Factores que se multiplican", items,
                List.of(new V06.Seccion("Comparación", referencia)), r.avisos(), r.resumen(),
                "Es un orden de magnitud, no un dato: el rango dice cuánto no sabes. Busca un dato para el factor más incierto.");
        return salida -> plantillas.render("tag/v/v06.jte", Map.of("v", v), salida);
    }

    private Optional<String> dibujar(ResultadoFermi r) {
        try {
            return Optional.of(grafico.svg(GeneradorDotDiagramas.cadena(r)));
        } catch (Grafico.GraficoInvalido | Grafico.GraficoTiempoAgotado e) {
            LOG.warn("Graphviz no dibujó la cadena de Fermi; se muestra solo la lista: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
