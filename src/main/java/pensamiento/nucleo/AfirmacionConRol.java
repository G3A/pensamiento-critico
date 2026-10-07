package pensamiento.nucleo;

import java.util.UUID;

/** Una afirmación consumida o producida por una ejecución, con el rol que cumple en ella. */
public record AfirmacionConRol(UUID afirmacionId, RolAfirmacion rol, SentidoAfirmacion sentido, OrigenAfirmacion origen) {
}
