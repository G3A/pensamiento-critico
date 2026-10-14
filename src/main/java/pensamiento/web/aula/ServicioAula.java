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

import pensamiento.nucleo.ClaseDeAula;
import pensamiento.nucleo.Grupo;
import pensamiento.nucleo.PanelGrupo;
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

    /** Largo del código de clase: corto para dictarlo o copiarlo del tablero; dura lo que la clase. */
    public static final int LARGO_CODIGO_CLASE = 6;
    /** La sesión de un estudiante fuera de una clase abierta (RF-02). */
    public static final Duration SESION_SIN_CLASE = Duration.ofMinutes(10);

    /** Una clase recién abierta con su código en claro: solo lo ve su docente, mientras la proyecta. */
    public record ClaseAbierta(ClaseDeAula clase, String codigo) {
    }

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

    /**
     * El docente (o el administrador) abre la clase del grupo con un código corto, por {@code minutos} (entre 10 y 60); si había
     * otra abierta, la termina. El código se guarda solo como hash.
     */
    public ClaseAbierta abrirClase(Actor actor, UUID grupoId, int minutos) {
        return enTransaccion(() -> {
            grupoQueAdministra(actor, grupoId);
            Duration dura = Duration.ofMinutes(Math.clamp(minutos, 10, ClaseDeAula.DURACION_MAXIMA.toMinutes()));
            String codigo = codigos.nuevo().substring(0, LARGO_CODIGO_CLASE);
            Instant ahora = reloj.ahora();
            ClaseDeAula clase = aula.abrirClase(actor, grupoId, CodigoDeUnUso.hash(codigo), ahora, ahora.plus(dura))
                    .orElseThrow(() -> new ObjetoNoEncontrado("grupo"));
            registrar(actor, RegistroAuditoria.Accion.ABRIR_CLASE, grupoId);
            return new ClaseAbierta(clase, codigo);
        });
    }

    /**
     * El docente del grupo (o el administrador) decide si la entrada con el código de su clase muestra la lista de nombres del
     * grupo y si sus estudiantes pueden usar el modelo durante la clase (las dos, apagadas por defecto).
     */
    public void cambiarOpciones(Actor actor, UUID grupoId, boolean listaEnClase, boolean iaEnClase) {
        enTransaccion(() -> {
            grupoQueAdministra(actor, grupoId);
            aula.cambiarOpciones(actor, grupoId, listaEnClase, iaEnClase);
            auditoria.registrar(new RegistroAuditoria.Evento(Optional.of(actor.id()), actor.institucionId(), RegistroAuditoria.Accion.CAMBIAR_GRUPO,
                    "grupo", Optional.of(grupoId), reloj.ahora(), Optional.of("lista:" + (listaEnClase ? "si" : "no") + " ia:" + (iaEnClase ? "si" : "no"))));
            return null;
        });
    }

    /** El docente del grupo (o el administrador) asigna un tema del Dojo (T13, T14, T18 o T19), con fecha opcional; vacío lo quita. */
    public void asignarTema(Actor actor, UUID grupoId, Optional<pensamiento.nucleo.IdTecnica> tema, Optional<java.time.LocalDate> hasta) {
        enTransaccion(() -> {
            grupoQueAdministra(actor, grupoId);
            hasta.filter(h -> h.isBefore(reloj.hoy())).ifPresent(h -> {
                throw new ServicioUsuarios.DatosInvalidos("La fecha ya pasó: elige hoy o un día que venga.");
            });
            aula.asignarTema(actor, grupoId, tema, hasta, reloj.ahora());
            auditoria.registrar(new RegistroAuditoria.Evento(Optional.of(actor.id()), actor.institucionId(), RegistroAuditoria.Accion.ASIGNAR_TEMA,
                    "grupo", Optional.of(grupoId), reloj.ahora(), Optional.of(tema.map(pensamiento.nucleo.IdTecnica::valor).orElse("ninguno"))));
            return null;
        });
    }

    /** "Reto de hoy" de un estudiante: el primer grupo suyo (por nombre) con un tema vigente hoy. */
    public Optional<Grupo> grupoConReto(Actor actor) {
        java.time.LocalDate hoy = reloj.hoy();
        return enTransaccion(() -> aula.grupos(actor).stream().filter(g -> g.temaVigente(hoy).isPresent()).findFirst());
    }

    /** Desde cuántos días sin practicar el panel nombra a alguien. */
    public static final int DIAS_SIN_PRACTICAR = 3;

    /** El panel con su grupo y su clase abierta, si hay. */
    public record Panel(Grupo grupo, Optional<ClaseDeAula> clase, PanelGrupo agregados) {
    }

    /**
     * El panel del grupo (entregable 7): solo agregados del día, de la clase abierta y del tema asignado, y quién lleva 3 días o
     * más sin practicar. Sale de funciones SECURITY DEFINER que devuelven números; ningún texto de un estudiante.
     */
    public Panel panel(Actor actor, UUID grupoId) {
        return enTransaccion(() -> {
            Grupo g = grupoQueAdministra(actor, grupoId);
            Instant ahora = reloj.ahora();
            Optional<ClaseDeAula> clase = aula.claseAbierta(actor, grupoId, ahora);
            RepositorioAula.Periodo periodo = new RepositorioAula.Periodo(reloj.hoy().atStartOfDay(reloj.zona()).toInstant(),
                    clase.map(ClaseDeAula::abiertaEn), reloj.hoy(), g.tema(), DIAS_SIN_PRACTICAR);
            return new Panel(g, clase, aula.panel(actor, grupoId, periodo).orElseThrow(() -> new ObjetoNoEncontrado("grupo")));
        });
    }

    /** La clase abierta del grupo, si el actor ve el grupo. */
    public Optional<ClaseDeAula> claseAbierta(Actor actor, UUID grupoId) {
        return enTransaccion(() -> aula.claseAbierta(actor, grupoId, reloj.ahora()));
    }

    /** "Terminar clase": la cierra y cierra las sesiones de todos los estudiantes del grupo. Devuelve cuántas cuentas tocó. */
    public int terminarClase(Actor actor, UUID grupoId) {
        List<UUID> estudiantes = enTransaccion(() -> {
            grupoQueAdministra(actor, grupoId);
            aula.terminarClase(actor, grupoId, reloj.ahora());
            registrar(actor, RegistroAuditoria.Accion.TERMINAR_CLASE, grupoId);
            return aula.miembros(actor, grupoId).stream().map(RepositorioAula.Miembro::id).toList();
        });
        estudiantes.forEach(cerrarSesiones);
        return estudiantes.size();
    }

    /**
     * Cuánto dura sin actividad la sesión de un estudiante: lo que le queda a la clase abierta de alguno de sus grupos (hasta 60
     * minutos) o, sin clase abierta, 10 minutos. Nunca menos de 10.
     */
    public Duration duracionDeSesion(Actor actor) {
        if (actor.rol() != Usuario.RolGlobal.ESTUDIANTE) {
            return SESION_SIN_CLASE;
        }
        Instant ahora = reloj.ahora();
        Duration clase = enTransaccion(() -> aula.grupos(actor).stream().map(g -> aula.claseAbierta(actor, g.id(), ahora))
                .flatMap(Optional::stream).map(c -> c.quedan(ahora)).max(Duration::compareTo).orElse(Duration.ZERO));
        return clase.compareTo(SESION_SIN_CLASE) > 0 ? clase : SESION_SIN_CLASE;
    }

    /** Antes de entrar (QR o código escrito): la clase abierta con ese código en esa institución. */
    public Optional<RepositorioAula.ClasePorCodigo> clasePorCodigo(UUID institucion, String codigoEscrito) {
        String codigo = CodigoDeUnUso.normalizarLargo(codigoEscrito == null ? "" : codigoEscrito, LARGO_CODIGO_CLASE);
        if (codigo.isEmpty()) {
            return Optional.empty();
        }
        return enTransaccion(() -> aula.clasePorCodigo(institucion, CodigoDeUnUso.hash(codigo), reloj.ahora()));
    }

    /** Los nombres del grupo de esa clase, solo si está abierta y su docente activó la lista; si no, vacía. */
    public List<String> listaDeClase(UUID institucion, String codigoEscrito) {
        String codigo = CodigoDeUnUso.normalizarLargo(codigoEscrito == null ? "" : codigoEscrito, LARGO_CODIGO_CLASE);
        if (codigo.isEmpty()) {
            return List.of();
        }
        return enTransaccion(() -> aula.listaDeClase(institucion, CodigoDeUnUso.hash(codigo), reloj.ahora()));
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
