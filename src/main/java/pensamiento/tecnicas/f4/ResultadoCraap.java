package pensamiento.tecnicas.f4;

import java.util.List;

/**
 * Valor que pinta el patrón V10 (tarjeta de veredicto) para T21 · CRAAP: el puntaje de 0 a 25 de cada fuente con sus cinco
 * criterios, si aprueba el umbral y cuál es la mejor.
 *
 * @param mejor el código de la mejor fuente ("F1"); nulo con una sola fuente
 */
public record ResultadoCraap(String uso, List<FuenteEvaluada> fuentes, int umbral, String mejor, String resumen) {

    /** Un criterio con su valor de 0 a 5 y su peso de la configuración. */
    public record Criterio(String nombre, int valor, int peso) {
    }

    /**
     * @param linea "11 de 25 · no aprobada"
     * @param debil "Lo más débil: autoridad (1 de 5)."
     */
    public record FuenteEvaluada(String codigo, String titulo, List<Criterio> criterios, int puntaje, boolean aprobada, String linea, String debil,
                                 String nota) {
        public FuenteEvaluada {
            criterios = List.copyOf(criterios);
        }
    }

    public ResultadoCraap {
        fuentes = List.copyOf(fuentes);
    }
}
