package pensamiento.nucleo.puertos;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import pensamiento.nucleo.Afirmacion;
import pensamiento.nucleo.TipoAfirmacion;
import pensamiento.nucleo.Verificacion;

/**
 * Lo que la ficha de verificación lee y escribe de una afirmación (tablas afirmacion y verificacion): su estado, el tipo
 * elegido en el paso 1, las preguntas críticas marcadas y el veredicto. Todo método recibe el usuario de la sesión y solo
 * toca lo suyo; la implementación real además corre bajo RLS. La afirmación la creó una ejecución antes.
 */
public interface RepositorioVerificaciones {

    /** Vacío si no existe o si es de otra persona. */
    Optional<Afirmacion> afirmacion(UUID usuarioId, UUID afirmacionId);

    /** Cambia el tipo de la afirmación. Falso si no existe o es de otra persona. */
    boolean cambiarTipo(UUID usuarioId, UUID afirmacionId, TipoAfirmacion tipo);

    /** La ficha guardada de la afirmación; una nueva, sin preguntas marcadas, si nunca se guardó o si es de otra persona. */
    Verificacion verificacion(UUID usuarioId, UUID afirmacionId);

    /** Guarda las preguntas críticas marcadas; reemplaza las anteriores. La afirmación debe ser del usuario. */
    void marcarPreguntas(UUID usuarioId, UUID institucionId, UUID afirmacionId, List<String> respondidas, Instant cuando);

    /** Escribe en la afirmación tipo, estado, fuerza neta y confianza, con la versión de las reglas. Falso si no es suya. */
    boolean guardarVeredicto(UUID usuarioId, UUID afirmacionId, Verificacion.Veredicto veredicto);

    /** La ejecución que produjo la afirmación; vacío si no la hay o si es de otra persona. */
    Optional<Verificacion.Origen> origen(UUID usuarioId, UUID afirmacionId);

    /** Todas las fichas guardadas de la persona (para el respaldo), de la más vieja a la más nueva. */
    List<Verificacion> deUsuario(UUID usuarioId);
}
