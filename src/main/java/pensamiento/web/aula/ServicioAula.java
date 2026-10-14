package pensamiento.web.aula;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import pensamiento.nucleo.Grupo;
import pensamiento.nucleo.Usuario;
import pensamiento.nucleo.puertos.RegistroAuditoria;
import pensamiento.nucleo.puertos.Reloj;
import pensamiento.nucleo.puertos.RepositorioAula;
import pensamiento.nucleo.puertos.RepositorioAula.Actor;
import pensamiento.nucleo.puertos.RepositorioUsuarios;
import pensamiento.nucleo.puertos.RepositorioUsuarios.TipoCodigo;
import pensamiento.web.ObjetoNoEncontrado;
import pensamiento.web.usuarios.CodigoDeUnUso;
import pensamiento.web.usuarios.ServicioUsuarios;
import pensamiento.web.usuarios.ServicioUsuarios.CodigoEmitido;

/**
 * El aula (hito 10, RF-19): el administrador crea los grupos y asigna su docente; un docente puede tener varios grupos y ve y
 * administra solo los suyos. Lo que el actor no ve responde igual que lo que no existe (404). La transacción vive aquí y cada
 * cambio queda en la auditoría.
 *
 * <p>Alta en lote (entregable 3): el docente de un grupo (o el administrador) pega una lista o sube un CSV de hasta 500 filas,
 * ve antes qué filas se crean y cuáles no (repetidas, inválidas o que ya existen), y las crea en una sola transacción sin
 * excepciones por repetidos (ON CONFLICT DO NOTHING). Cada cuenta nueva recibe su código de alta, que solo se muestra en la
 * hoja de tarjetas. También genera códigos nuevos para su grupo, de a uno o en lote: cierran las sesiones abiertas.
 */
public class ServicioAula {

    private final RepositorioAula aula;
    private final RepositorioUsuarios usuarios;
    private final RegistroAuditoria auditoria;
    private final Reloj reloj;
    private final TransactionTemplate tx;
    private final CodigoDeUnUso codigos;
    private final Consumer<UUID> cerrarSesiones;

    /** Lo que se crearía con una lista, antes de crear nada. */
    public record VistaPrevia(Grupo grupo, List<ListaDeEstudiantes.Fila> filas) {
        public long nuevas() {
            return filas.stream().filter(f -> f.estado() == ListaDeEstudiantes.Estado.NUEVA).count();
        }
    }

    /** Lo que dejó el alta en lote: las cuentas creadas con su código (solo para la hoja de tarjetas) y las filas que no. */
    public record AltaEnLote(Grupo grupo, List<CodigoEmitido> creadas, List<ListaDeEstudiantes.Fila> sinCrear) {
    }

    public ServicioAula(RepositorioAula aula, RepositorioUsuarios usuarios, RegistroAuditoria auditoria, Reloj reloj, PlatformTransactionManager gestor,
                        CodigoDeUnUso codigos, Consumer<UUID> cerrarSesiones) {
        this.aula = aula;
        this.usuarios = usuarios;
        this.auditoria = auditoria;
        this.reloj = reloj;
        this.tx = new TransactionTemplate(gestor);
        this.codigos = codigos;
        this.cerrarSesiones = cerrarSesiones;
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

    /** Qué pasaría con cada fila de la lista, sin crear nada. */
    public VistaPrevia vistaPrevia(Actor actor, UUID grupoId, String texto) {
        return enTransaccion(() -> previa(actor, grupoId, texto));
    }

    /**
     * Crea, en una sola transacción, una cuenta de estudiante por cada fila nueva y la suma al grupo, con su código de alta. Lo
     * que se volvió repetido entre la vista previa y este momento no se crea y no lanza.
     */
    public AltaEnLote altaEnLote(Actor actor, UUID grupoId, String texto) {
        return enTransaccion(() -> {
            VistaPrevia previa = previa(actor, grupoId, texto);
            List<CodigoEmitido> creadas = new ArrayList<>();
            List<ListaDeEstudiantes.Fila> sinCrear = new ArrayList<>();
            for (ListaDeEstudiantes.Fila f : previa.filas()) {
                if (f.estado() != ListaDeEstudiantes.Estado.NUEVA) {
                    sinCrear.add(f);
                    continue;
                }
                Optional<UUID> id = aula.crearEstudiante(actor, grupoId, UUID.randomUUID(), f.nombre());
                if (id.isEmpty()) {
                    sinCrear.add(f.conEstado(ListaDeEstudiantes.Estado.YA_EXISTE));
                    continue;
                }
                Usuario cuenta = Usuario.nueva(id.get(), actor.institucionId(), f.nombre(), Usuario.RolGlobal.ESTUDIANTE);
                registrarCuenta(actor, RegistroAuditoria.Accion.CREAR, cuenta.id());
                creadas.add(emitir(actor, cuenta, TipoCodigo.ALTA, ServicioUsuarios.VIGENCIA_ALTA));
            }
            auditoria.registrar(new RegistroAuditoria.Evento(Optional.of(actor.id()), actor.institucionId(), RegistroAuditoria.Accion.ALTA_EN_LOTE,
                    "grupo", Optional.of(grupoId), reloj.ahora(), Optional.of(creadas.size() + " creadas, " + sinCrear.size() + " sin crear")));
            return new AltaEnLote(previa.grupo(), creadas, sinCrear);
        });
    }

    /**
     * Códigos nuevos para estudiantes del grupo, de a uno o en lote: de restablecimiento si ya tienen contraseña (72 h) o de alta
     * si todavía no la eligieron (14 días). Cierra sus sesiones abiertas. Una cuenta que no es del grupo o está desactivada no
     * recibe código.
     */
    public List<CodigoEmitido> codigosNuevos(Actor actor, UUID grupoId, List<UUID> cuentas) {
        List<CodigoEmitido> emitidos = enTransaccion(() -> {
            grupoQueAdministra(actor, grupoId);
            Set<UUID> pedidas = Set.copyOf(cuentas);
            List<CodigoEmitido> lista = new ArrayList<>();
            for (RepositorioAula.Miembro m : aula.miembros(actor, grupoId)) {
                if (!pedidas.contains(m.id()) || !m.activo()) {
                    continue;
                }
                Usuario cuenta = usuarios.porId(actor.institucionId(), m.id()).orElseThrow(() -> new ObjetoNoEncontrado("estudiante"));
                lista.add(cuenta.tieneClave() ? emitir(actor, cuenta, TipoCodigo.RESTABLECER, ServicioUsuarios.VIGENCIA_RESTABLECER)
                        : emitir(actor, cuenta, TipoCodigo.ALTA, ServicioUsuarios.VIGENCIA_ALTA));
            }
            if (lista.isEmpty()) {
                throw new ServicioUsuarios.DatosInvalidos("Elige al menos un estudiante activo del grupo.");
            }
            return lista;
        });
        emitidos.forEach(e -> cerrarSesiones.accept(e.cuenta().id()));
        return emitidos;
    }

    private VistaPrevia previa(Actor actor, UUID grupoId, String texto) {
        Grupo g = grupoQueAdministra(actor, grupoId);
        List<ListaDeEstudiantes.Fila> leidas;
        try {
            leidas = ListaDeEstudiantes.leer(texto);
        } catch (ListaDeEstudiantes.Demasiadas e) {
            throw new ServicioUsuarios.DatosInvalidos(e.getMessage());
        }
        if (leidas.isEmpty()) {
            throw new ServicioUsuarios.DatosInvalidos("La lista está vacía: escribe un nombre por línea.");
        }
        return new VistaPrevia(g, leidas.stream().map(f -> f.estado() == ListaDeEstudiantes.Estado.NUEVA
                && usuarios.porNombre(actor.institucionId(), f.nombre()).isPresent() ? f.conEstado(ListaDeEstudiantes.Estado.YA_EXISTE) : f)
                .collect(Collectors.toList()));
    }

    private CodigoEmitido emitir(Actor actor, Usuario cuenta, TipoCodigo tipo, Duration vigencia) {
        String codigo = codigos.nuevo();
        Instant caduca = reloj.ahora().plus(vigencia);
        usuarios.guardarCodigo(actor.institucionId(), cuenta.id(), tipo, CodigoDeUnUso.hash(codigo), caduca, Optional.of(actor.id()));
        registrarCuenta(actor, tipo == TipoCodigo.ALTA ? RegistroAuditoria.Accion.CODIGO_ALTA : RegistroAuditoria.Accion.CODIGO_RESTABLECER, cuenta.id());
        return new CodigoEmitido(cuenta, tipo, codigo, caduca);
    }

    private void registrarCuenta(Actor actor, RegistroAuditoria.Accion accion, UUID cuenta) {
        auditoria.registrar(new RegistroAuditoria.Evento(Optional.of(actor.id()), actor.institucionId(), accion, "usuario", Optional.of(cuenta),
                reloj.ahora()));
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
    public Grupo grupoQueAdministro(Actor actor, UUID grupoId) {
        return enTransaccion(() -> grupoQueAdministra(actor, grupoId));
    }

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
