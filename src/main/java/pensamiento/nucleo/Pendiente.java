package pensamiento.nucleo;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Proyección de algo que falta por hacer; se escribe en la misma transacción que la ejecución.
 *
 * @param objetoId    la afirmación a la que se refiere, si hay una
 * @param descripcion una línea accionable para "qué falta para cerrar" del Expediente
 */
public record Pendiente(TipoPendiente tipo, Optional<UUID> objetoId, Optional<LocalDate> vence, String descripcion) {
}
