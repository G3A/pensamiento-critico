package pensamiento.nucleo.puertos;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import pensamiento.nucleo.ClaseDeAula;
import pensamiento.nucleo.Grupo;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.PanelGrupo;
import pensamiento.nucleo.Usuario;

/**
 * El aula (hito 10): grupos, sus estudiantes, sus clases y el panel de agregados. Lo que cada quien ve depende de su rol
 * (V17): el administrador, todo lo de su institución; el docente, solo sus grupos y, de sus estudiantes, solo el nombre y
 * los agregados del panel, nunca su texto; el estudiante, sus grupos y sus clases, nunca la lista de sus compañeros; una
 * persona, nada. La implementación real corre bajo RLS con el {@link Actor} fijado en la transacción; ningún método lanza
 * por un dato repetido o un permiso que falta: lo dice el retorno, así la transacción del servicio sigue sana.
 */
public interface RepositorioAula {

    /** Quién consulta: la cuenta de la sesión, con su institución y su rol. */
    record Actor(UUID id, UUID institucionId, Usuario.RolGlobal rol) {
    }

    /** Un estudiante del grupo, como lo ve su docente: nombre y estado de la cuenta. */
    record Miembro(UUID id, String nombre, boolean activo, boolean tieneClave) {
    }

    /** La clase abierta que corresponde a un código, antes de entrar: su grupo y si su docente activó la lista. */
    record ClasePorCodigo(UUID claseId, UUID grupoId, String grupo, boolean lista, Instant cierraEn) {
    }

    /** Qué período resume el panel: el día (desde su comienzo), la clase abierta si hay, y el tema del grupo. */
    record Periodo(Instant desdeHoy, Optional<Instant> desdeClase, LocalDate hoy, Optional<IdTecnica> tema, int diasSinPracticar) {
    }

    /** Crea el grupo; vacío si el actor no es administrador o ya hay un grupo con ese nombre en la institución. */
    Optional<Grupo> crearGrupo(Actor actor, Grupo grupo);

    /** Asigna (o quita) el docente de un grupo; false si el actor no es administrador o el grupo no existe. */
    boolean asignarDocente(Actor actor, UUID grupoId, Optional<UUID> docenteId);

    /** Los grupos que el actor ve, por nombre: todos (administrador), los suyos (docente) o donde es miembro (estudiante). */
    List<Grupo> grupos(Actor actor);

    Optional<Grupo> grupo(Actor actor, UUID grupoId);

    /** El docente del grupo (o el administrador) asigna un tema del Dojo, con fecha opcional; vacío lo quita. */
    boolean asignarTema(Actor actor, UUID grupoId, Optional<IdTecnica> tema, Optional<LocalDate> hasta, Instant ahora);

    /** El docente del grupo (o el administrador) prende o apaga la lista de nombres en clase y el modelo en clase. */
    boolean cambiarOpciones(Actor actor, UUID grupoId, boolean listaEnClase, boolean iaEnClase);

    /**
     * Crea la cuenta de un estudiante (sin clave, sin código todavía) y la suma al grupo, si el actor administra el grupo.
     * Vacío si ya hay alguien con esa clave de nombre en la institución o si el actor no administra el grupo.
     */
    Optional<UUID> crearEstudiante(Actor actor, UUID grupoId, UUID id, String nombre);

    /** Los estudiantes del grupo por nombre; vacío si el actor no administra el grupo. */
    List<Miembro> miembros(Actor actor, UUID grupoId);

    /**
     * Abre una clase del grupo con su código (solo el hash) hasta {@code cierraEn} (60 minutos como máximo) y termina la que
     * estuviera abierta; vacío si el actor no administra el grupo.
     */
    Optional<ClaseDeAula> abrirClase(Actor actor, UUID grupoId, String codigoHash, Instant ahora, Instant cierraEn);

    /** La clase abierta del grupo en ese instante, si el actor ve el grupo (docente, administrador o estudiante del grupo). */
    Optional<ClaseDeAula> claseAbierta(Actor actor, UUID grupoId, Instant ahora);

    /** "Terminar clase": false si no había una abierta o el actor no administra el grupo. */
    boolean terminarClase(Actor actor, UUID grupoId, Instant ahora);

    /** Antes de entrar (QR o código): la clase abierta con ese código en esa institución. */
    Optional<ClasePorCodigo> clasePorCodigo(UUID institucionId, String codigoHash, Instant ahora);

    /** Los nombres del grupo de esa clase: solo mientras está abierta y solo si su docente activó la lista. */
    List<String> listaDeClase(UUID institucionId, String codigoHash, Instant ahora);

    /** Los agregados del grupo; vacío si el actor no administra el grupo. Nunca un texto de un estudiante. */
    Optional<PanelGrupo> panel(Actor actor, UUID grupoId, Periodo periodo);
}
