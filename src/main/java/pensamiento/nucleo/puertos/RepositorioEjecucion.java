package pensamiento.nucleo.puertos;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import pensamiento.nucleo.AfirmacionConRol;
import pensamiento.nucleo.Ejecucion;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Pendiente;
import pensamiento.nucleo.PendienteGuardado;

/**
 * Historial de ejecuciones. Todo método recibe el usuario de la sesión y solo devuelve lo suyo;
 * la implementación real además corre bajo RLS, que es la segunda cerradura.
 */
public interface RepositorioEjecucion {

    /** Guarda; si ya existe una ejecución con la misma clave de idempotencia, devuelve esa sin duplicar. */
    default Ejecucion guardar(Ejecucion ejecucion) {
        return guardar(ejecucion, List.of(), List.of());
    }

    /**
     * Guarda en una sola transacción la ejecución, las afirmaciones producidas, la relación de cada afirmación
     * con su rol y sentido, y la proyección de pendientes (RF-07). Si ya existe una ejecución con la misma
     * clave de idempotencia (doble clic), devuelve esa y no escribe nada más.
     */
    Ejecucion guardar(Ejecucion ejecucion, List<AfirmacionConRol> afirmaciones, List<Pendiente> pendientes);

    Optional<Ejecucion> porId(UUID usuarioId, UUID id);

    /** De la más reciente a la más antigua. */
    List<Ejecucion> porTecnica(UUID usuarioId, IdTecnica tecnica);

    /** Las afirmaciones de una ejecución con su rol, sentido y origen; vacía si la ejecución no es del usuario. */
    List<AfirmacionConRol> afirmacionesDe(UUID usuarioId, UUID ejecucionId);

    /** Los pendientes sin resolver del usuario, de ejecuciones no borradas, en el orden en que se proyectaron. */
    List<PendienteGuardado> pendientes(UUID usuarioId);

    /** Las ejecuciones de un expediente, de la más reciente a la más antigua. */
    List<Ejecucion> porExpediente(UUID usuarioId, UUID expedienteId);

    /** Las últimas ejecuciones del usuario en cualquier técnica, de la más reciente a la más antigua. */
    List<Ejecucion> recientes(UUID usuarioId, int limite);

    /**
     * Cierra los pendientes sin resolver del usuario de ese tipo sobre ese objeto (por ejemplo, la revisión de una predicción
     * al registrar su resultado). Devuelve cuántos cerró; 0 si no había o si son de otra persona.
     */
    int cerrarPendientes(UUID usuarioId, pensamiento.nucleo.TipoPendiente tipo, UUID objetoId);

    /** Asocia la ejecución a un expediente o la desasocia (vacío). Falso si la ejecución no existe o no es del usuario. */
    boolean asociar(UUID usuarioId, UUID ejecucionId, Optional<UUID> expedienteId);
}
