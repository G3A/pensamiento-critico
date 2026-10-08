package pensamiento.tecnicas.f7;

import java.util.List;
import java.util.UUID;

/**
 * Valor que pinta el patrón V06 (árbol, con Graphviz) para T42 · Árbol de hipótesis MECE: los nodos con su clase, las
 * hojas, los huecos y los solapes que encontró la verificación.
 *
 * @param raizId la afirmación de la raíz: es el id de su nodo en el SVG
 */
public record ResultadoArbolMece(UUID raizId, String raiz, List<NodoArbol> nodos, List<String> hojas, List<String> huecos, List<Solape> solapes,
                                 boolean verificado, String resumen) {

    /** La clase CSS de cada nodo del SVG (sin colores en el DOT). */
    public enum Clase {
        RAMA("rama"), RAMA_VACIA("rama vacia"), INTERMEDIO("intermedio"), HOJA("hoja");

        private final String css;

        Clase(String css) {
            this.css = css;
        }

        public String css() {
            return css;
        }

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    /** @param padre el código del padre; nulo si cuelga de la raíz */
    public record NodoArbol(UUID afirmacionId, String codigo, String texto, String padre, int profundidad, Clase clase, boolean solape) {
    }

    public record Solape(String a, String b, String textoA, String textoB) {
    }

    public ResultadoArbolMece {
        nodos = List.copyOf(nodos);
        hojas = List.copyOf(hojas);
        huecos = List.copyOf(huecos);
        solapes = List.copyOf(solapes);
    }
}
