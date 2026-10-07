package pensamiento.nucleo;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Asunto con nombre que reúne ejecuciones de cualquier técnica. */
public record Expediente(UUID id, UUID usuarioId, UUID institucionId, String nombre, Optional<UUID> posturaId, Estado estado, Instant creadoEn) {

    public enum Estado { ABIERTO, CERRADO }
}
