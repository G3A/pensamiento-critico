package pensamiento.nucleo.puertos;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import pensamiento.nucleo.CambioOpinion;

/**
 * Los cambios de opinión de cada persona (tabla cambio_opinion, R05). Todo método recibe el usuario de la sesión y solo
 * devuelve lo suyo; la implementación real además corre bajo RLS. Guardar va en la misma transacción que la ejecución,
 * que ya debe existir con la afirmación de cada cambio. Un cambio es inmutable: solo se inserta.
 */
public interface RepositorioCambiosOpinion {

    /** Guarda los cambios declarados por la ejecución; volver a guardar los mismos identificadores no duplica nada. */
    void guardar(UUID usuarioId, UUID institucionId, UUID ejecucionId, List<CambioOpinion.Declarado> cambios, Instant cuando);

    /** Importa un cambio tal como estaba en el respaldo. Si el identificador ya existe, no cambia nada. */
    void restaurar(UUID usuarioId, UUID institucionId, CambioOpinion cambio);

    /** Todos los de la persona, del más viejo al más nuevo (y por identificador si coinciden). */
    List<CambioOpinion> deUsuario(UUID usuarioId);

    /** Los de una ejecución; vacía si la ejecución no es del usuario. */
    List<CambioOpinion> deEjecucion(UUID usuarioId, UUID ejecucionId);
}
