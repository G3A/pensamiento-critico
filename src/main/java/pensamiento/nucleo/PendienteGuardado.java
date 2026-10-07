package pensamiento.nucleo;

import java.util.UUID;

/** Un pendiente ya proyectado en la tabla pendiente, con la ejecución que lo produjo. */
public record PendienteGuardado(UUID id, UUID ejecucionId, Pendiente pendiente, boolean resuelto) {
}
