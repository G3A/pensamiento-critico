package pensamiento.nucleo;

import java.util.UUID;

/**
 * Una afirmación consumida o producida por una ejecución, con el rol que cumple en ella. Lleva texto y tipo
 * porque la persistencia inserta las producidas en la tabla afirmacion; el JSONB del resultado guarda solo
 * el identificador, nunca una copia.
 *
 * @param adoptada lo producido por el usuario cuenta desde el principio; lo del modelo no cuenta en R02, R03 ni R04
 *                 hasta que la persona lo adopta
 */
public record AfirmacionConRol(UUID afirmacionId, String texto, TipoAfirmacion tipo, RolAfirmacion rol,
                               SentidoAfirmacion sentido, OrigenAfirmacion origen, boolean adoptada) {

    public AfirmacionConRol {
        if (origen != OrigenAfirmacion.MODELO && !adoptada) {
            throw new IllegalArgumentException("Solo lo que viene del modelo puede quedar sin adoptar");
        }
    }

    /** Lo que no viene del modelo nace adoptado; lo del modelo, sin adoptar. */
    public AfirmacionConRol(UUID afirmacionId, String texto, TipoAfirmacion tipo, RolAfirmacion rol,
                            SentidoAfirmacion sentido, OrigenAfirmacion origen) {
        this(afirmacionId, texto, tipo, rol, sentido, origen, origen != OrigenAfirmacion.MODELO);
    }

    /** Si cuenta en R02, R03 y R04: lo propio siempre; lo del modelo, solo adoptado. */
    public boolean cuenta() {
        return adoptada;
    }
}
