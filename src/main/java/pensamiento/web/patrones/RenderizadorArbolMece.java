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
import pensamiento.tecnicas.f7.ResultadoArbolMece;

/**
 * V06 para T42 · Árbol de hipótesis MECE: Graphviz dibuja el árbol (fuera de la transacción, al escribir la plantilla) y
 * la lista repite los nodos con su estado; debajo, hojas, huecos y solapes.
 */
@Component
public class RenderizadorArbolMece implements RenderizadorResultado<ResultadoArbolMece> {

    private static final Logger LOG = LoggerFactory.getLogger(RenderizadorArbolMece.class);

    private final TemplateEngine plantillas;
    private final Grafico grafico;

    public RenderizadorArbolMece(TemplateEngine plantillas, Grafico grafico) {
        this.plantillas = plantillas;
        this.grafico = grafico;
    }

    @Override
    public String patron() {
        return V06.PATRON;
    }

    @Override
    public Class<ResultadoArbolMece> tipo() {
        return ResultadoArbolMece.class;
    }

    @Override
    public Content render(Optional<UUID> idEjecucion, String sufijo, ResultadoArbolMece r, Modo modo) {
        return salida -> plantillas.render("tag/v/v06.jte", Map.of("v", vista(idEjecucion, sufijo, r, modo, dibujar(r))), salida);
    }

    static V06 vista(Optional<UUID> idEjecucion, String sufijo, ResultadoArbolMece r, Modo modo, Optional<String> svg) {
        List<V06.Item> items = new ArrayList<>();
        items.add(new V06.Item(r.raizId().toString(), "Raíz", r.raiz(), null, null, 0));
        agregarHijos(items, r, null, 1);
        List<String> solapes = r.solapes().stream().map(s -> s.a() + " y " + s.b() + ": «" + s.textoA() + "» y «" + s.textoB() + "»").toList();
        List<V06.Seccion> secciones = List.of(new V06.Seccion("Hojas", r.hojas()), new V06.Seccion("Huecos", r.huecos()),
                new V06.Seccion("Solapes", solapes));
        String tarjeta = r.verificado()
                ? "La verificación revisa la forma del árbol: ramas vacías y hermanos con las mismas palabras. Que no encuentre nada no prueba que el árbol cubra todo."
                : "Sin verificación MECE: revisa a mano que las ramas no se pisen y que entre todas cubran el problema.";
        return new V06(idEjecucion, sufijo, modo, "Árbol de hipótesis MECE", null, svg,
                "Árbol dibujado; la lista de abajo dice lo mismo con texto", "Nodos del árbol", items, secciones, List.of(), r.resumen(), tarjeta);
    }

    /** Recorre en profundidad para que cada hijo quede debajo de su padre en la lista. */
    private static void agregarHijos(List<V06.Item> items, ResultadoArbolMece r, String padre, int nivel) {
        for (ResultadoArbolMece.NodoArbol n : r.nodos()) {
            if (java.util.Objects.equals(n.padre(), padre)) {
                String chip = (switch (n.clase()) {
                    case RAMA -> "rama";
                    case RAMA_VACIA -> "rama vacía";
                    case INTERMEDIO -> "intermedio";
                    case HOJA -> "hoja";
                }) + (n.solape() ? " · se solapa" : "");
                String clase = n.clase() == ResultadoArbolMece.Clase.RAMA_VACIA || n.solape() ? "chip chip-aviso" : "chip";
                items.add(new V06.Item(n.afirmacionId().toString(), n.codigo(), n.texto(), chip, clase, nivel));
                agregarHijos(items, r, n.codigo(), nivel + 1);
            }
        }
    }

    private Optional<String> dibujar(ResultadoArbolMece r) {
        try {
            return Optional.of(grafico.svg(GeneradorDotDiagramas.arbol(r)));
        } catch (Grafico.GraficoInvalido | Grafico.GraficoTiempoAgotado e) {
            LOG.warn("Graphviz no dibujó el árbol MECE; se muestra solo la lista: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
