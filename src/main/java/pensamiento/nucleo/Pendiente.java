package pensamiento.nucleo;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/** Proyección de algo que falta por hacer; se escribe en la misma transacción que la ejecución. */
public record Pendiente(TipoPendiente tipo, Optional<UUID> objetoId, Optional<LocalDate> vence) {
}
