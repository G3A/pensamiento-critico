package pensamiento.tecnicas.f7;

import java.util.List;
import java.util.UUID;

/**
 * Valor que pinta el patrón V07 (espina de pescado, con Graphviz) para T43 · Diagrama de Ishikawa: el efecto y las
 * categorías en su orden, cada una con sus causas y cuántas le faltan para el mínimo.
 *
 * @param efectoId la afirmación del efecto: es el id de la cabeza del pescado en el SVG
 */
public record ResultadoIshikawa(UUID efectoId, String efecto, List<Categoria> categorias, int causasMinimas, String resumen) {

    /** @param faltan cuántas causas le faltan para el mínimo; 0 si está vacía (se señala como vacía) o si llega */
    public record Categoria(String nombre, List<CausaEn> causas, int faltan) {
        public Categoria {
            causas = List.copyOf(causas);
        }

        public boolean vacia() {
            return causas.isEmpty();
        }
    }

    public record CausaEn(UUID afirmacionId, String texto) {
    }

    public ResultadoIshikawa {
        categorias = List.copyOf(categorias);
    }
}
