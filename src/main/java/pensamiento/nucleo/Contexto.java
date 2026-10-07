package pensamiento.nucleo;

import java.util.Optional;
import java.util.UUID;

import pensamiento.nucleo.puertos.Ia;
import pensamiento.nucleo.puertos.Reloj;

/** Contexto de una ejecución: usuario, expediente opcional, reloj y la IA local si está disponible. */
public record Contexto(UUID usuarioId, UUID institucionId, Optional<UUID> expedienteId, Reloj reloj, Optional<Ia> ia) {
}
