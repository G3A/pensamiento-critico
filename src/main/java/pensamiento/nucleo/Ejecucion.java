package pensamiento.nucleo;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Una corrida de una técnica con configuración, datos y resultado. Es el historial de la técnica. */
public record Ejecucion(
        UUID id,
        UUID usuarioId,
        UUID institucionId,
        IdTecnica tecnica,
        int versionEsquema,
        Optional<UUID> expedienteId,
        Json config,
        Json datos,
        Json resultado,
        String resumen,
        Optional<RegistroModelo> modelo,
        String claveIdempotencia,
        Instant creadaEn) {

    /** Reproducibilidad de la IA (RNF-07): qué modelo, digest, prompt, temperatura y semilla se usaron. */
    public record RegistroModelo(String modelo, String digest, String promptVersion, double temperatura, long semilla) {
    }
}
