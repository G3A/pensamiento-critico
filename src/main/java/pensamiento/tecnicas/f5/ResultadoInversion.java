package pensamiento.tecnicas.f5;

import java.util.List;

/**
 * Valor que pinta el patrón V04 (dos columnas) para T30 · Inversión: cómo fracasar a la izquierda y la acción contraria
 * a la derecha, en el mismo orden.
 *
 * @param estado "completa" o "incompleta: falta …"
 */
public record ResultadoInversion(String meta, String enunciado, List<Par> pares, boolean completa, String estado, boolean contrariaExigida,
                                 List<String> avisos, String resumen) {

    /** @param contraria nula si no la escribió */
    public record Par(String forma, String contraria) {
    }

    public ResultadoInversion {
        pares = List.copyOf(pares);
        avisos = List.copyOf(avisos);
    }
}
