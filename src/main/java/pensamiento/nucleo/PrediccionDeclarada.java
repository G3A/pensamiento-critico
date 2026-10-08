package pensamiento.nucleo;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Lo que un ejecutor declara para la tabla prediccion (T32 · Diario de decisiones): la afirmación con rol de predicción,
 * la confianza que la persona escribió y la fecha de revisión. Se guarda en la misma transacción que la ejecución.
 */
public record PrediccionDeclarada(UUID id, UUID afirmacionId, int confianza, LocalDate fechaRevision) {

    public PrediccionDeclarada {
        if (confianza < 0 || confianza > 100) {
            throw new IllegalArgumentException("La confianza va de 0 a 100: " + confianza);
        }
    }
}
