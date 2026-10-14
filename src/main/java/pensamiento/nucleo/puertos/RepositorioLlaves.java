package pensamiento.nucleo.puertos;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import pensamiento.nucleo.LlaveDeAcceso;
import pensamiento.nucleo.Titular;

/**
 * Llaves de acceso y códigos de recuperación de un titular (hito 9). Cada operación recibe de quién es: nadie ve ni toca la
 * llave de otro. Una cuenta solo se puede tocar con su institución fijada en la transacción.
 */
public interface RepositorioLlaves {

    /** Un código de recuperación sin usar: su id y su hash (el código en claro solo se mostró una vez). */
    record CodigoDeRecuperacion(UUID id, String hash) {
    }

    /** Registra la llave o actualiza su contador y su último uso; false si el titular no puede o la llave es de otro. */
    boolean guardar(Titular titular, LlaveDeAcceso llave);

    /** La llave con ese id de credencial, solo si es de ese titular. */
    Optional<LlaveDeAcceso> porCredencial(Titular titular, byte[] credencialId);

    /** Las llaves del titular, de la más antigua a la más nueva. */
    List<LlaveDeAcceso> de(Titular titular);

    boolean borrar(Titular titular, byte[] credencialId);

    /** Los códigos nuevos (solo sus hashes) reemplazan a los que quedaban sin usar. */
    boolean reemplazarRecuperacion(Titular titular, List<String> hashes);

    List<CodigoDeRecuperacion> recuperacionVigente(Titular titular);

    /** Gasta un código una sola vez: true la primera vez. */
    boolean usarRecuperacion(Titular titular, UUID id, Instant ahora);
}
