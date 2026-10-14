package pensamiento.nucleo;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Una clase abierta por el docente para un grupo (tabla sesion_aula, hito 10): dura hasta 60 minutos o hasta "Terminar
 * clase". Mientras está abierta, la sesión de un estudiante del grupo dura lo que la clase. El código (o el QR) solo lleva a
 * la entrada con el grupo a la vista: no reemplaza la contraseña.
 */
public record ClaseDeAula(UUID id, UUID grupoId, Instant abiertaEn, Instant cierraEn, Optional<Instant> terminadaEn) {

    public static final Duration DURACION_MAXIMA = Duration.ofMinutes(60);

    public boolean abiertaEn(Instant ahora) {
        return terminadaEn.isEmpty() && ahora.isBefore(cierraEn);
    }

    /** Cuánto le queda a la clase en ese instante (cero si ya cerró). */
    public Duration quedan(Instant ahora) {
        return abiertaEn(ahora) ? Duration.between(ahora, cierraEn) : Duration.ZERO;
    }
}
