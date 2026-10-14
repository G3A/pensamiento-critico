package pensamiento.testutil.fakes;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import pensamiento.nucleo.ClaseDeAula;
import pensamiento.nucleo.Competencia;
import pensamiento.nucleo.Grupo;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.IntentoDojo;
import pensamiento.nucleo.NivelBloom;
import pensamiento.nucleo.PanelGrupo;
import pensamiento.nucleo.Usuario;
import pensamiento.nucleo.puertos.RegistroAuditoria;
import pensamiento.nucleo.puertos.RepositorioAula;

/**
 * El aula en memoria, en espejo de la RLS por rol de V17: lo que cada actor ve sale de su rol, como en PostgreSQL. Las cuentas
 * van al {@link FakeRepositorioUsuarios} compartido y el panel suma los intentos del {@link FakeRepositorioDojo} y las entradas
 * del {@link FakeRegistroAuditoria}. Certificado por FakeRepositorioAulaContractTest.
 */
public final class FakeRepositorioAula implements RepositorioAula {

    private record Clase(UUID id, UUID institucion, UUID grupo, String hash, Instant abierta, Instant cierra, Optional<Instant> terminada) {
        ClaseDeAula comoClase() {
            return new ClaseDeAula(id, grupo, abierta, cierra, terminada);
        }

        boolean abiertaEn(Instant ahora) {
            return terminada.isEmpty() && ahora.isBefore(cierra);
        }
    }

    private final FakeRepositorioUsuarios usuarios;
    private final FakeRepositorioDojo dojo;
    private final FakeRegistroAuditoria auditoria;
    private final Map<UUID, Grupo> grupos = new LinkedHashMap<>();
    private final Map<UUID, Set<UUID>> miembros = new LinkedHashMap<>();
    private final List<Clase> clases = new ArrayList<>();

    public FakeRepositorioAula(FakeRepositorioUsuarios usuarios, FakeRepositorioDojo dojo, FakeRegistroAuditoria auditoria) {
        this.usuarios = usuarios;
        this.dojo = dojo;
        this.auditoria = auditoria;
    }

    @Override
    public Optional<Grupo> crearGrupo(Actor actor, Grupo grupo) {
        boolean repetido = grupos.containsKey(grupo.id()) || grupos.values().stream()
                .anyMatch(g -> g.institucionId().equals(actor.institucionId()) && g.nombre().equalsIgnoreCase(grupo.nombre()));
        if (actor.rol() != Usuario.RolGlobal.ADMINISTRADOR || repetido) {
            return Optional.empty();
        }
        Grupo creado = new Grupo(grupo.id(), actor.institucionId(), grupo.nombre(), grupo.docenteId(), true, Optional.empty(),
                Optional.empty(), false, false);
        grupos.put(creado.id(), creado);
        miembros.put(creado.id(), new LinkedHashSet<>());
        return Optional.of(creado);
    }

    @Override
    public boolean asignarDocente(Actor actor, UUID grupoId, Optional<UUID> docenteId) {
        Grupo g = grupos.get(grupoId);
        if (actor.rol() != Usuario.RolGlobal.ADMINISTRADOR || g == null || !g.institucionId().equals(actor.institucionId())) {
            return false;
        }
        grupos.put(grupoId, new Grupo(g.id(), g.institucionId(), g.nombre(), docenteId, g.activo(), g.tema(), g.temaHasta(), g.listaEnClase(),
                g.iaEnClase()));
        return true;
    }

    @Override
    public List<Grupo> grupos(Actor actor) {
        return grupos.values().stream().filter(g -> ve(actor, g))
                .sorted(Comparator.comparing((Grupo g) -> g.nombre().toLowerCase()).thenComparing(Grupo::id)).toList();
    }

    @Override
    public Optional<Grupo> grupo(Actor actor, UUID grupoId) {
        return Optional.ofNullable(grupos.get(grupoId)).filter(g -> ve(actor, g));
    }

    @Override
    public boolean asignarTema(Actor actor, UUID grupoId, Optional<IdTecnica> tema, Optional<LocalDate> hasta, Instant ahora) {
        Grupo g = grupos.get(grupoId);
        if (g == null || !administra(actor, g)) {
            return false;
        }
        grupos.put(grupoId, new Grupo(g.id(), g.institucionId(), g.nombre(), g.docenteId(), g.activo(), tema,
                tema.isPresent() ? hasta : Optional.empty(), g.listaEnClase(), g.iaEnClase()));
        return true;
    }

    @Override
    public boolean cambiarOpciones(Actor actor, UUID grupoId, boolean listaEnClase, boolean iaEnClase) {
        Grupo g = grupos.get(grupoId);
        if (g == null || !administra(actor, g)) {
            return false;
        }
        grupos.put(grupoId, new Grupo(g.id(), g.institucionId(), g.nombre(), g.docenteId(), g.activo(), g.tema(), g.temaHasta(), listaEnClase,
                iaEnClase));
        return true;
    }

    @Override
    public Optional<UUID> crearEstudiante(Actor actor, UUID grupoId, UUID id, String nombre) {
        Grupo g = grupos.get(grupoId);
        if (g == null || !administra(actor, g)) {
            return Optional.empty();
        }
        Optional<UUID> creado = usuarios.crear(Usuario.nueva(id, actor.institucionId(), nombre, Usuario.RolGlobal.ESTUDIANTE)).map(Usuario::id);
        creado.ifPresent(u -> miembros.get(grupoId).add(u));
        return creado;
    }

    @Override
    public List<Miembro> miembros(Actor actor, UUID grupoId) {
        Grupo g = grupos.get(grupoId);
        if (g == null || !administra(actor, g)) {
            return List.of();
        }
        return miembros.get(grupoId).stream().map(u -> usuarios.porId(g.institucionId(), u)).flatMap(Optional::stream)
                .sorted(Comparator.comparing((Usuario u) -> Usuario.claveDeNombre(u.nombre())).thenComparing(Usuario::id))
                .map(u -> new Miembro(u.id(), u.nombre(), u.activo(), u.tieneClave())).toList();
    }

    @Override
    public Optional<ClaseDeAula> abrirClase(Actor actor, UUID grupoId, String codigoHash, Instant ahora, Instant cierraEn) {
        Grupo g = grupos.get(grupoId);
        if (g == null || !administra(actor, g)) {
            return Optional.empty();
        }
        if (!cierraEn.isAfter(ahora) || cierraEn.isAfter(ahora.plus(ClaseDeAula.DURACION_MAXIMA))) {
            throw new IllegalArgumentException("Una clase dura hasta 60 minutos");
        }
        terminarClase(actor, grupoId, ahora);
        Clase nueva = new Clase(UUID.randomUUID(), actor.institucionId(), grupoId, codigoHash, ahora, cierraEn, Optional.empty());
        clases.add(nueva);
        return Optional.of(nueva.comoClase());
    }

    @Override
    public Optional<ClaseDeAula> claseAbierta(Actor actor, UUID grupoId, Instant ahora) {
        Grupo g = grupos.get(grupoId);
        if (g == null || !ve(actor, g)) {
            return Optional.empty();
        }
        return clases.stream().filter(c -> c.grupo().equals(grupoId) && c.abiertaEn(ahora))
                .max(Comparator.comparing(Clase::abierta)).map(Clase::comoClase);
    }

    @Override
    public boolean terminarClase(Actor actor, UUID grupoId, Instant ahora) {
        Grupo g = grupos.get(grupoId);
        if (g == null || !administra(actor, g)) {
            return false;
        }
        boolean alguna = false;
        for (int i = 0; i < clases.size(); i++) {
            Clase c = clases.get(i);
            if (c.grupo().equals(grupoId) && c.abiertaEn(ahora)) {
                clases.set(i, new Clase(c.id(), c.institucion(), c.grupo(), c.hash(), c.abierta(), c.cierra(), Optional.of(ahora)));
                alguna = true;
            }
        }
        return alguna;
    }

    @Override
    public Optional<ClasePorCodigo> clasePorCodigo(UUID institucionId, String codigoHash, Instant ahora) {
        return clases.stream().filter(c -> c.institucion().equals(institucionId) && c.hash().equals(codigoHash) && c.abiertaEn(ahora))
                .filter(c -> grupos.get(c.grupo()).activo())
                .max(Comparator.comparing(Clase::abierta))
                .map(c -> {
                    Grupo g = grupos.get(c.grupo());
                    return new ClasePorCodigo(c.id(), g.id(), g.nombre(), g.listaEnClase(), c.cierra());
                });
    }

    @Override
    public List<String> listaDeClase(UUID institucionId, String codigoHash, Instant ahora) {
        return clasePorCodigo(institucionId, codigoHash, ahora).filter(ClasePorCodigo::lista)
                .map(c -> miembros.get(c.grupoId()).stream().map(u -> usuarios.porId(institucionId, u)).flatMap(Optional::stream)
                        .filter(u -> u.activo() && u.rol() == Usuario.RolGlobal.ESTUDIANTE)
                        .sorted(Comparator.comparing((Usuario u) -> Usuario.claveDeNombre(u.nombre())))
                        .map(Usuario::nombre).toList())
                .orElse(List.of());
    }

    @Override
    public Optional<PanelGrupo> panel(Actor actor, UUID grupoId, Periodo periodo) {
        Grupo g = grupos.get(grupoId);
        if (g == null || !administra(actor, g)) {
            return Optional.empty();
        }
        List<Usuario> activos = miembros.get(grupoId).stream().map(u -> usuarios.porId(g.institucionId(), u)).flatMap(Optional::stream)
                .filter(Usuario::activo).toList();
        Set<UUID> ids = activos.stream().map(Usuario::id).collect(Collectors.toSet());
        List<RegistroAuditoria.Evento> entradas = auditoria.todos().stream()
                .filter(e -> e.accion() == RegistroAuditoria.Accion.ENTRAR && e.usuarioId().map(ids::contains).orElse(false)).toList();
        Map<UUID, List<IntentoDojo>> intentos = new LinkedHashMap<>();
        activos.forEach(u -> intentos.put(u.id(), dojo.intentos(u.id())));
        List<IntentoDojo> todos = intentos.values().stream().flatMap(List::stream).toList();
        String tema = periodo.tema().map(IdTecnica::valor).orElse("");

        int entraronHoy = (int) entradas.stream().filter(e -> !e.fecha().isBefore(periodo.desdeHoy())).map(e -> e.usuarioId().get()).distinct().count();
        int enClase = periodo.desdeClase().map(desde -> (int) entradas.stream().filter(e -> !e.fecha().isBefore(desde))
                .map(e -> e.usuarioId().get()).distinct().count()).orElse(0);
        int retosHoy = (int) todos.stream().filter(i -> i.dia().equals(periodo.hoy())).count();
        int respondieronHoy = (int) intentos.entrySet().stream().filter(e -> e.getValue().stream().anyMatch(i -> i.dia().equals(periodo.hoy()))).count();
        int delTema = (int) todos.stream().filter(i -> i.tecnica().valor().equals(tema)).count();

        record Clave(String concepto, boolean crear) {
        }
        Map<Clave, List<UUID>> porConcepto = new LinkedHashMap<>();
        Map<Clave, int[]> cuentas = new LinkedHashMap<>();
        intentos.forEach((usuario, lista) -> lista.stream().filter(i -> i.tecnica().valor().equals(tema)).forEach(i -> {
            Clave k = new Clave(i.concepto(), i.nivel() == NivelBloom.CREAR);
            porConcepto.computeIfAbsent(k, x -> new ArrayList<>()).add(usuario);
            int[] c = cuentas.computeIfAbsent(k, x -> new int[2]);
            c[0]++;
            c[1] += i.acierto() ? 1 : 0;
        }));
        List<PanelGrupo.Concepto> conceptos = cuentas.entrySet().stream()
                .sorted(Comparator.comparing((Map.Entry<Clave, int[]> e) -> e.getKey().concepto()).thenComparing(e -> e.getKey().crear()))
                .map(e -> new PanelGrupo.Concepto(e.getKey().concepto(), e.getKey().crear(), e.getValue()[0], e.getValue()[1],
                        (int) porConcepto.get(e.getKey()).stream().distinct().count())).toList();

        Map<String, Integer> niveles = new LinkedHashMap<>();
        activos.forEach(u -> niveles.merge(dojo.competencias(u.id()).stream().filter(c -> c.tecnica().valor().equals(tema))
                .map(Competencia::nivel).map(NivelBloom::toString).findFirst().orElse("sin empezar"), 1, Integer::sum));
        List<PanelGrupo.Nivel> bloom = niveles.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(e -> new PanelGrupo.Nivel(e.getKey(), e.getValue())).toList();

        record Ultima(Usuario usuario, Optional<LocalDate> dia) {
        }
        List<PanelGrupo.SinPracticar> sinPracticar = activos.stream()
                .map(u -> new Ultima(u, intentos.get(u.id()).stream().map(IntentoDojo::dia).max(Comparator.naturalOrder())))
                .filter(x -> x.dia().map(d -> ChronoUnit.DAYS.between(d, periodo.hoy()) >= periodo.diasSinPracticar()).orElse(true))
                .sorted(Comparator.comparing((Ultima x) -> x.dia().orElse(LocalDate.MIN)).thenComparing(x -> Usuario.claveDeNombre(x.usuario().nombre())))
                .map(x -> new PanelGrupo.SinPracticar(x.usuario().nombre(), x.dia().map(d -> (int) ChronoUnit.DAYS.between(d, periodo.hoy()))))
                .toList();

        return Optional.of(new PanelGrupo(activos.size(), entraronHoy, enClase, retosHoy, respondieronHoy, delTema, conceptos, bloom, sinPracticar));
    }

    /** El grupo es de su institución y el actor es su administrador o su docente. */
    private static boolean administra(Actor actor, Grupo g) {
        return g.institucionId().equals(actor.institucionId()) && (actor.rol() == Usuario.RolGlobal.ADMINISTRADOR
                || actor.rol() == Usuario.RolGlobal.DOCENTE && g.docenteId().map(actor.id()::equals).orElse(false));
    }

    private boolean ve(Actor actor, Grupo g) {
        return administra(actor, g) || g.institucionId().equals(actor.institucionId()) && miembros.get(g.id()).contains(actor.id())
                && Objects.equals(actor.rol(), Usuario.RolGlobal.ESTUDIANTE);
    }
}
