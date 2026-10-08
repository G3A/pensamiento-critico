package pensamiento.nucleo;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Una predicción con la confianza que la persona declaró (tabla prediccion, R05). La confianza nunca sale de R02 ni del
 * modelo. Una vez resuelta es inmutable: cambiar de opinión es otra predicción.
 *
 * @param texto        el texto de la afirmación con rol de predicción
 * @param confianza    porcentaje entero de 0 a 100
 * @param resueltaEn   cuándo se registró el resultado; vacío mientras está pendiente
 */
public record Prediccion(UUID id, UUID ejecucionId, UUID afirmacionId, String texto, int confianza, LocalDate fechaRevision,
                         Estado estado, Optional<Instant> resueltaEn) {

    public enum Estado {
        PENDIENTE, ACIERTO, FALLO;

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    /** Se quiso cambiar el resultado de una predicción ya resuelta. */
    public static class YaResuelta extends IllegalStateException {
        public YaResuelta() {
            super("Esta predicción ya está resuelta: no se puede modificar.");
        }
    }

    public Prediccion {
        if (confianza < 0 || confianza > 100) {
            throw new IllegalArgumentException("La confianza va de 0 a 100: " + confianza);
        }
        if ((estado == Estado.PENDIENTE) != resueltaEn.isEmpty()) {
            throw new IllegalArgumentException("Una predicción resuelta lleva su fecha de resolución y una pendiente no");
        }
    }

    public boolean resuelta() {
        return estado != Estado.PENDIENTE;
    }

    /** Pendiente y con la fecha de revisión ya cumplida: aparece en la lista de revisiones al entrar. */
    public boolean vencida(LocalDate hoy) {
        return !resuelta() && !fechaRevision.isAfter(hoy);
    }

    /** R05: resolverla una sola vez; después es inmutable. */
    public Prediccion resolver(boolean seCumplio, Instant cuando) {
        if (resuelta()) {
            throw new YaResuelta();
        }
        return new Prediccion(id, ejecucionId, afirmacionId, texto, confianza, fechaRevision, seCumplio ? Estado.ACIERTO : Estado.FALLO,
                Optional.of(cuando));
    }
}
