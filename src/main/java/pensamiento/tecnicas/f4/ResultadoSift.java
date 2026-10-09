package pensamiento.tecnicas.f4;

import java.util.List;

/**
 * Valor que pinta el patrón V02 (lista de verificación con estado) para T19 · SIFT: los pasos activos con su hallazgo y la
 * señal. La señal dice si conviene compartir, nunca si el dato es cierto.
 *
 * @param senal   una de las siete señales en minúscula ("fuente interesada")
 * @param minutos el recordatorio de minutos por paso de la configuración
 */
public record ResultadoSift(String afirmacion, String fuente, List<PasoEvaluado> pasos, String senal, String motivo, int hechos, int activos,
                            int minutos, String resumen) {

    /** @param linea "{paso} · {hallazgo}", tal como se lee en la tarjeta */
    public record PasoEvaluado(EjecutorSift.Paso paso, String nombre, boolean hecho, String hallazgo, String linea) {
    }

    public ResultadoSift {
        pasos = List.copyOf(pasos);
    }
}
