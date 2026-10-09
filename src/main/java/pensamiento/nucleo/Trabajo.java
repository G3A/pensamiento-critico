package pensamiento.nucleo;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Un trabajo largo (tabla trabajo): indexar o vectorizar un documento. Pasa de pendiente a en proceso cuando un ejecutor lo
 * toma, y de ahí a hecho, a error o de vuelta a pendiente para reintentarse más tarde. Los que quedan en proceso al apagar
 * la app vuelven a la cola al arrancar.
 *
 * @param payload      lo que el procesador del tipo necesita (usuario, institución, documento)
 * @param disponibleEn no se toma antes de este momento (reintentos con espera)
 */
public record Trabajo(UUID id, String tipo, Estado estado, int intentos, Json payload, Optional<String> error, Instant creadoEn, Instant disponibleEn) {

    public enum Estado {
        PENDIENTE, EN_PROCESO, HECHO, ERROR;

        public String enBaseDeDatos() {
            return name().toLowerCase();
        }
    }
}
