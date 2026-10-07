package pensamiento.nucleo;

import java.util.UUID;

/**
 * Una afirmación consumida o producida por una ejecución, con el rol que cumple en ella. Lleva texto y tipo
 * porque la persistencia inserta las producidas en la tabla afirmacion; el JSONB del resultado guarda solo
 * el identificador, nunca una copia.
 */
public record AfirmacionConRol(UUID afirmacionId, String texto, TipoAfirmacion tipo, RolAfirmacion rol,
                               SentidoAfirmacion sentido, OrigenAfirmacion origen) {

    /** Lo producido por el usuario cuenta desde el principio; lo del modelo no cuenta hasta que se adopta. */
    public boolean adoptada() {
        return origen != OrigenAfirmacion.MODELO;
    }
}
