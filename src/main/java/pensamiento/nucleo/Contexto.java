package pensamiento.nucleo;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import pensamiento.nucleo.puertos.Ia;
import pensamiento.nucleo.puertos.Reloj;

/**
 * Contexto de una ejecución: usuario, expediente opcional, reloj, la IA local si está disponible y el
 * generador de identificadores (uuidv7 en producción, una secuencia fija en las pruebas).
 */
public record Contexto(UUID usuarioId, UUID institucionId, Optional<UUID> expedienteId, Reloj reloj, Optional<Ia> ia,
                       Supplier<UUID> nuevoId) {
}
