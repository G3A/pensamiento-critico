package pensamiento.tecnicas.f5;

import java.util.List;
import java.util.UUID;

/**
 * Valor que pinta el patrón V06 (cadena, con Graphviz) para T26 · Estimación de Fermi: los factores en orden con su
 * rango, el más incierto marcado, y el resultado con su rango y su valor central.
 *
 * @param resultadoId la afirmación de la estimación: es el id del nodo final en el SVG
 * @param referencia  "260 está dentro del rango."; nula si no hay referencia
 */
public record ResultadoFermi(String pregunta, String unidad, UUID resultadoId, List<FactorEn> factores, String minimo, String maximo,
                             String central, String referencia, String deDondeReferencia, List<String> avisos, String resumen) {

    /** @param masAncho el factor más incierto: el de mayor máximo / mínimo */
    public record FactorEn(UUID afirmacionId, String codigo, String texto, String rango, boolean masAncho) {
    }

    public ResultadoFermi {
        factores = List.copyOf(factores);
        avisos = List.copyOf(avisos);
    }
}
