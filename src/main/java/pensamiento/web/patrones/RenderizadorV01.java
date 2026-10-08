package pensamiento.web.patrones;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import gg.jte.Content;
import gg.jte.TemplateEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import pensamiento.nucleo.puertos.Grafico;
import pensamiento.tecnicas.f1.ResultadoMapa;

/**
 * V01, grafo de nodos: genera el DOT, lo dibuja con el puerto Grafico (Graphviz como proceso hijo) y pinta
 * tag/v/v01.jte. Graphviz corre al escribir la plantilla, fuera de la transacción del controlador. Si no
 * responde, el mapa se muestra como lista de nodos y argumentos.
 */
@Component
public class RenderizadorV01 implements RenderizadorResultado<ResultadoMapa> {

    private static final Logger LOG = LoggerFactory.getLogger(RenderizadorV01.class);

    private final TemplateEngine plantillas;
    private final Grafico grafico;

    public RenderizadorV01(TemplateEngine plantillas, Grafico grafico) {
        this.plantillas = plantillas;
        this.grafico = grafico;
    }

    @Override
    public String patron() {
        return V01.PATRON;
    }

    @Override
    public Class<ResultadoMapa> tipo() {
        return ResultadoMapa.class;
    }

    @Override
    public Content render(Optional<UUID> idEjecucion, String sufijo, ResultadoMapa valor, Modo modo) {
        return salida -> {
            V01 v = new V01(idEjecucion, sufijo, valor, modo, dibujar(valor));
            plantillas.render("tag/v/v01.jte", Map.of("v", v), salida);
        };
    }

    private Optional<String> dibujar(ResultadoMapa valor) {
        try {
            return Optional.of(grafico.svg(GeneradorDot.dot(valor)));
        } catch (Grafico.GraficoInvalido | Grafico.GraficoTiempoAgotado e) {
            LOG.warn("Graphviz no dibujó el mapa; se muestra solo la lista de nodos: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
