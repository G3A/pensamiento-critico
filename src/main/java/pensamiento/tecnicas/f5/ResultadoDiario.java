package pensamiento.tecnicas.f5;

import java.util.List;
import java.util.UUID;

/**
 * Valor que pinta el patrón V11 (registro con línea de tiempo) para T32 · Diario de decisiones: la decisión con sus
 * campos, la predicción con su confianza y los hitos (registrada, revisión programada y, en el Diario, resuelta).
 *
 * @param prediccionId el identificador de la fila de prediccion que declara al guardarse
 * @param resultado    "se cumplió" o "no se cumplió" cuando el Diario la resuelve; nulo mientras está pendiente
 * @param bloqueo      el motivo si la fecha de revisión es demasiado cercana; nulo si se puede guardar
 */
public record ResultadoDiario(String decision, String contexto, String alternativas, String prediccion, int confianza, String cambiarOpinion,
                              String fechaRevision, String fechaRevisionTexto, UUID prediccionId, List<Hito> linea, String estado, String resultado,
                              String bloqueo, List<String> avisos, String resumen) {

    /** @param fecha AAAA-MM-DD; @param texto "15 de abril de 2027" */
    public record Hito(String fecha, String texto, String que) {
    }

    public ResultadoDiario {
        linea = List.copyOf(linea);
        avisos = List.copyOf(avisos);
    }
}
