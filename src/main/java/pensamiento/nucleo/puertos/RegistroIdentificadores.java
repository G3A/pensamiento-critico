package pensamiento.nucleo.puertos;

import java.util.UUID;

/**
 * Responde si un identificador ya pertenece a otra persona (expediente, ejecución o afirmación). Lo usa la
 * importación de datos (RF-12) para rechazar identificadores ajenos sin poder leer filas de otros.
 */
public interface RegistroIdentificadores {

    /** Verdadero si el identificador existe y es de un usuario distinto de usuarioId. */
    boolean deOtroUsuario(UUID usuarioId, UUID id);
}
