package pensamiento.nucleo.puertos;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import pensamiento.nucleo.Ejecucion;
import pensamiento.nucleo.SesionConsejero;
import pensamiento.nucleo.TurnoConsejero;

/**
 * Las sesiones del Consejero socrático y sus turnos (tablas sesion_consejero y turno_consejero). Todo método recibe el
 * usuario de la sesión y solo ve y toca lo suyo; la implementación real además corre bajo RLS. Una sesión cerrada ya no
 * acepta turnos nuevos.
 */
public interface RepositorioSesiones {

    /** La sesión ya cerrada no acepta cambios: el Consejero lo valida antes, este error es la última barrera. */
    class SesionCerrada extends RuntimeException {
        public SesionCerrada(UUID id) {
            super("La sesión " + id + " ya está cerrada");
        }
    }

    /** Guarda una sesión nueva; si el identificador ya existe, no cambia nada. */
    void crear(SesionConsejero sesion);

    /** Vacío si no existe o es de otra persona: para quien pregunta, es lo mismo. */
    Optional<SesionConsejero> porId(UUID usuarioId, UUID sesionId);

    /** Las de la persona, de la más nueva a la más vieja. */
    List<SesionConsejero> deUsuario(UUID usuarioId);

    /** Los turnos de la sesión, por número; vacía si la sesión no es del usuario. */
    List<TurnoConsejero> turnos(UUID usuarioId, UUID sesionId);

    /**
     * Agrega un turno al final. Lanza SesionCerrada si la sesión está cerrada e IllegalArgumentException si el número no es
     * el siguiente (otro turno llegó antes).
     */
    void agregarTurno(UUID usuarioId, UUID institucionId, TurnoConsejero turno);

    /** Reemplaza el texto de un turno del Consejero que estaba redactando, con su origen, intentos y registro del modelo. */
    void completarTurno(UUID usuarioId, UUID turnoId, String texto, TurnoConsejero.Origen origen, int intentos, Optional<Ejecucion.RegistroModelo> modelo);

    /** Anota en un turno de la persona el elemento que propuso el modelo y su porqué. */
    void proponerElemento(UUID usuarioId, UUID turnoId, String elemento, String porque);

    /** Marca adoptada la propuesta de elemento de ese turno. */
    void adoptarElemento(UUID usuarioId, UUID turnoId);

    void pedirCierre(UUID usuarioId, UUID sesionId);

    void asociar(UUID usuarioId, UUID sesionId, Optional<UUID> expedienteId);

    /** Cierra la sesión con su reflexión, la confianza al terminar y la ejecución que produjo. */
    void cerrar(UUID usuarioId, UUID sesionId, Optional<String> reflexion, Optional<Integer> confianzaDespues, Optional<UUID> ejecucionId, Instant cuando);

    /** Importa una sesión con sus turnos tal como estaban en el respaldo; lo que ya existe no cambia. */
    void restaurar(UUID usuarioId, UUID institucionId, SesionConsejero sesion, List<TurnoConsejero> turnos);
}
