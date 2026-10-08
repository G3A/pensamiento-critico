package pensamiento.web.patrones;

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
import pensamiento.tecnicas.f7.ResultadoIshikawa;

/** V07 para T43 · Diagrama de Ishikawa: Graphviz dibuja la espina y la lista repite las causas por categoría. */
@Component
public class RenderizadorIshikawa implements RenderizadorResultado<ResultadoIshikawa> {

    private static final Logger LOG = LoggerFactory.getLogger(RenderizadorIshikawa.class);

    private final TemplateEngine plantillas;
    private final Grafico grafico;

    public RenderizadorIshikawa(TemplateEngine plantillas, Grafico grafico) {
        this.plantillas = plantillas;
        this.grafico = grafico;
    }

    @Override
    public String patron() {
        return V07.PATRON;
    }

    @Override
    public Class<ResultadoIshikawa> tipo() {
        return ResultadoIshikawa.class;
    }

    @Override
    public Content render(Optional<UUID> idEjecucion, String sufijo, ResultadoIshikawa r, Modo modo) {
        return salida -> plantillas.render("tag/v/v07.jte", Map.of("v", vista(idEjecucion, sufijo, r, modo, dibujar(r, V07.idRaiz(idEjecucion, sufijo)))),
                salida);
    }

    static V07 vista(Optional<UUID> idEjecucion, String sufijo, ResultadoIshikawa r, Modo modo, Optional<String> svg) {
        List<V07.Categoria> categorias = r.categorias().stream().map(c -> new V07.Categoria(c.nombre(),
                c.causas().stream().map(ResultadoIshikawa.CausaEn::texto).toList(),
                c.vacia() ? "vacía" : c.faltan() == 0 ? null : c.faltan() == 1 ? "le falta 1 causa" : "le faltan " + c.faltan() + " causas")).toList();
        return new V07(idEjecucion, sufijo, modo, r.efecto(), svg, categorias, r.resumen(),
                "Una categoría vacía no prueba que por ahí no haya causa: es la pregunta que falta hacer.");
    }

    private Optional<String> dibujar(ResultadoIshikawa r, String idFragmento) {
        try {
            return Optional.of(grafico.svg(GeneradorDotDiagramas.espina(r, idFragmento)));
        } catch (Grafico.GraficoInvalido | Grafico.GraficoTiempoAgotado e) {
            LOG.warn("Graphviz no dibujó la espina de Ishikawa; se muestra solo la lista: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
