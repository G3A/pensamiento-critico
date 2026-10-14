package pensamiento.web.aula;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import pensamiento.nucleo.Grupo;
import pensamiento.nucleo.Usuario;
import pensamiento.nucleo.puertos.RegistroAuditoria;
import pensamiento.nucleo.puertos.Reloj;
import pensamiento.nucleo.puertos.RepositorioAula;
import pensamiento.nucleo.puertos.RepositorioAula.Actor;
import pensamiento.nucleo.puertos.RepositorioUsuarios;
import pensamiento.web.ObjetoNoEncontrado;
import pensamiento.web.usuarios.ServicioUsuarios;

/**
 * El aula (hito 10, RF-19): el administrador crea los grupos y asigna su docente; un docente puede tener varios grupos y ve y
 * administra solo los suyos. Lo que el actor no ve responde igual que lo que no existe (404). La transacción vive aquí y cada
 * cambio queda en la auditoría.
 */
public class ServicioAula {

    private final RepositorioAula aula;
    private final RepositorioUsuarios usuarios;
    private final RegistroAuditoria auditoria;
    private final Reloj reloj;
    private final TransactionTemplate tx;

    public ServicioAula(RepositorioAula aula, RepositorioUsuarios usuarios, RegistroAuditoria auditoria, Reloj reloj, PlatformTransactionManager gestor) {
        this.aula = aula;
        this.usuarios = usuarios;
        this.auditoria = auditoria;
        this.reloj = reloj;
        this.tx = new TransactionTemplate(gestor);
    }

    /** Los grupos que el actor ve: todos (administrador) o los suyos (docente). */
    public List<Grupo> grupos(Actor actor) {
        return enTransaccion(() -> aula.grupos(actor));
    }

    /** El grupo, si el actor lo ve; si no, 404. */
    public Grupo grupo(Actor actor, UUID grupoId) {
        return enTransaccion(() -> aula.grupo(actor, grupoId)).orElseThrow(() -> new ObjetoNoEncontrado("grupo"));
    }

    /** Los estudiantes del grupo (nombre y estado), si el actor lo administra; si no, 404. */
    public List<RepositorioAula.Miembro> miembros(Actor actor, UUID grupoId) {
        return enTransaccion(() -> {
            grupoQueAdministra(actor, grupoId);
            return aula.miembros(actor, grupoId);
        });
    }

    /** Los docentes activos de la institución, para elegir el de un grupo. */
    public List<Usuario> docentes(Actor actor) {
        return enTransaccion(() -> usuarios.porRol(actor.institucionId(), Usuario.RolGlobal.DOCENTE));
    }

    /** El nombre de una cuenta que el actor ve (el docente de un grupo). */
    public Optional<String> nombreDe(Actor actor, UUID usuarioId) {
        return enTransaccion(() -> usuarios.porId(actor.institucionId(), usuarioId).map(Usuario::nombre));
    }

    /** El administrador crea un grupo con un nombre único en la institución y, si quiere, su docente. */
    public Grupo crearGrupo(Actor actor, String nombre, Optional<UUID> docente) {
        return enTransaccion(() -> {
            exigirAdministrador(actor);
            String limpio = nombreDeGrupo(nombre);
            docente.ifPresent(d -> exigirDocente(actor, d));
            Grupo creado = aula.crearGrupo(actor, Grupo.nuevo(UUID.randomUUID(), actor.institucionId(), limpio, docente))
                    .orElseThrow(() -> new ServicioUsuarios.DatosInvalidos("Ya hay un grupo llamado «" + limpio + "». Usa otro nombre, por ejemplo con el año."));
            registrar(actor, RegistroAuditoria.Accion.CREAR_GRUPO, creado.id());
            return creado;
        });
    }

    /** El administrador asigna (o quita) el docente de un grupo. */
    public void asignarDocente(Actor actor, UUID grupoId, Optional<UUID> docente) {
        enTransaccion(() -> {
            exigirAdministrador(actor);
            docente.ifPresent(d -> exigirDocente(actor, d));
            if (!aula.asignarDocente(actor, grupoId, docente)) {
                throw new ObjetoNoEncontrado("grupo");
            }
            registrar(actor, RegistroAuditoria.Accion.CAMBIAR_GRUPO, grupoId);
            return null;
        });
    }

    /** Nombre de 1 a 60 caracteres, sin espacios al borde. */
    static String nombreDeGrupo(String nombre) {
        String limpio = nombre == null ? "" : nombre.strip();
        if (limpio.isEmpty() || limpio.length() > 60) {
            throw new ServicioUsuarios.DatosInvalidos("El nombre del grupo es obligatorio y tiene como máximo 60 caracteres.");
        }
        return limpio;
    }

    /** El grupo, si el actor lo administra (su docente o el administrador); si no, 404 aunque lo vea. */
    Grupo grupoQueAdministra(Actor actor, UUID grupoId) {
        Grupo g = aula.grupo(actor, grupoId).orElseThrow(() -> new ObjetoNoEncontrado("grupo"));
        boolean administra = actor.rol() == Usuario.RolGlobal.ADMINISTRADOR
                || actor.rol() == Usuario.RolGlobal.DOCENTE && g.docenteId().map(actor.id()::equals).orElse(false);
        if (!administra) {
            throw new ObjetoNoEncontrado("grupo");
        }
        return g;
    }

    private static void exigirAdministrador(Actor actor) {
        if (actor.rol() != Usuario.RolGlobal.ADMINISTRADOR) {
            throw new ObjetoNoEncontrado("grupo");
        }
    }

    private void exigirDocente(Actor actor, UUID docente) {
        Usuario cuenta = usuarios.porId(actor.institucionId(), docente)
                .orElseThrow(() -> new ServicioUsuarios.DatosInvalidos("No existe esa persona."));
        if (cuenta.rol() != Usuario.RolGlobal.DOCENTE || !cuenta.activo()) {
            throw new ServicioUsuarios.DatosInvalidos("«" + cuenta.nombre() + "» no es una cuenta de docente activa.");
        }
    }

    void registrar(Actor actor, RegistroAuditoria.Accion accion, UUID objeto) {
        auditoria.registrar(new RegistroAuditoria.Evento(Optional.of(actor.id()), actor.institucionId(), accion, "grupo", Optional.of(objeto),
                reloj.ahora()));
    }

    <T> T enTransaccion(Supplier<T> accion) {
        return tx.execute(estado -> accion.get());
    }
}
