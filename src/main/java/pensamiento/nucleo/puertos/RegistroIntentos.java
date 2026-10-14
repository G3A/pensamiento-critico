package pensamiento.nucleo.puertos;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Contadores de intentos fallidos al entrar (hito 9, RF-15), por clave: la cuenta o la IP dentro de una institución, o la
 * IP sin institución (tope global). La regla de cuánto esperar vive fuera, en LimiteDeIntentos; este puerto solo guarda.
 * Se usa antes de saber quién es la persona.
 */
public interface RegistroIntentos {

    /** Fallos contados desde {@code ventanaDesde}; si hay espera, hasta cuándo. */
    record Estado(int fallos, Instant ventanaDesde, Optional<Instant> esperaHasta) {
    }

    /** Una cuenta que espera: la clave ("cuenta:<clave del nombre>") y hasta cuándo. */
    record Espera(String clave, Instant hasta) {
    }

    Optional<Estado> leer(Optional<UUID> institucion, String clave);

    /** Inserta o reemplaza el estado de esa clave. */
    void guardar(Optional<UUID> institucion, String clave, Estado estado);

    void borrar(Optional<UUID> institucion, String clave);

    /** Las claves de cuenta de la institución con una espera que todavía no termina, ordenadas por clave. */
    List<Espera> esperas(UUID institucion, Instant ahora);
}
