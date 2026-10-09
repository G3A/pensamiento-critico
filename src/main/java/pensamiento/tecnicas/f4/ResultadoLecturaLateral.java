package pensamiento.tecnicas.f4;

import java.util.List;

/**
 * Valor que pinta el patrón V10 (tarjeta de veredicto) para T20 · Lectura lateral: la tabla de fuentes externas con su
 * postura y si cuentan, y el veredicto. "Respaldada" es "por las fuentes que revisaste", nunca "verdadera".
 *
 * @param veredicto faltan fuentes, dividida, en duda, respaldada o sin eco
 * @param nota      las que no cuentan por no ser independientes; nula si no hay o si no se exige independencia
 */
public record ResultadoLecturaLateral(String original, String afirmacion, List<ExternaEvaluada> externas, String veredicto, String motivo,
                                      String nota, int cuentan, int minimo, String resumen) {

    /** @param postura la confirma, la contradice o no la menciona */
    public record ExternaEvaluada(String codigo, String nombre, String dice, String postura, boolean independiente, boolean cuenta) {
    }

    public ResultadoLecturaLateral {
        externas = List.copyOf(externas);
    }
}
