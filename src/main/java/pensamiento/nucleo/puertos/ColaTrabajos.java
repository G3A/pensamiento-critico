package pensamiento.nucleo.puertos;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import pensamiento.nucleo.Json;
import pensamiento.nucleo.Trabajo;

/**
 * La tabla de trabajos largos (indexar y vectorizar documentos). Tomar un trabajo es atómico: dos ejecutores nunca toman el
 * mismo. Los que quedan en proceso al apagar la app vuelven a la cola al arrancar.
 */
public interface ColaTrabajos {

    /** Encola un trabajo pendiente que no se toma antes de {@code disponibleEn}. */
    UUID encolar(String tipo, Json payload, Instant disponibleEn);

    /** El pendiente más viejo ya disponible de alguno de esos tipos: lo pasa a en proceso y suma un intento. Vacío si no hay. */
    Optional<Trabajo> tomar(Instant ahora, Set<String> tipos);

    /** Lo deja hecho. */
    void terminar(UUID id);

    /** Lo devuelve a pendiente para tomarlo de nuevo desde {@code disponibleEn}, con el motivo. */
    void reintentar(UUID id, Instant disponibleEn, String motivo);

    /** Lo deja en error con su motivo; no se vuelve a tomar. */
    void fallar(UUID id, String motivo);

    /** Devuelve a pendiente los de esos tipos que quedaron en proceso. Devuelve cuántos. */
    int reencolarEnProceso(Set<String> tipos);

    Optional<Trabajo> porId(UUID id);
}
