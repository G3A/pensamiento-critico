package pensamiento.nucleo.puertos;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Abre una transacción como una persona, fuera de una petición web: los trabajos largos (indexar y vectorizar) escriben bajo
 * RLS con el usuario y la institución que trae su payload. La web lo implementa con el mismo contexto RLS de la sesión.
 */
public interface TransaccionComoUsuario {

    <T> T ejecutar(UUID usuarioId, UUID institucionId, Supplier<T> accion);
}
