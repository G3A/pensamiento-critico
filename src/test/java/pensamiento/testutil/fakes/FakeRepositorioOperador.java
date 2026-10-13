package pensamiento.testutil.fakes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import pensamiento.nucleo.Institucion;
import pensamiento.nucleo.puertos.Reloj;
import pensamiento.nucleo.puertos.RepositorioOperador;

/**
 * Operador en memoria. Comparte las instituciones y las cuentas con sus Fakes, como la base comparte las tablas: una
 * institución creada aquí se resuelve por su ruta en FakeRepositorioInstituciones. Certificado por
 * FakeRepositorioOperadorContractTest.
 */
public final class FakeRepositorioOperador implements RepositorioOperador {

    private record Invitacion(UUID id, UUID institucion, String codigoHash, Instant caducaEn, boolean usada) {
    }

    private final FakeRepositorioInstituciones instituciones;
    private final FakeRepositorioUsuarios usuarios;
    private final Reloj reloj;
    private final Map<String, Operador> operadores = new LinkedHashMap<>();
    private final Map<UUID, Instant> creadas = new LinkedHashMap<>();
    private final Map<UUID, Invitacion> invitaciones = new LinkedHashMap<>();
    private final List<Evento> eventos = new ArrayList<>();

    public FakeRepositorioOperador(FakeRepositorioInstituciones instituciones, FakeRepositorioUsuarios usuarios, Reloj reloj) {
        this.instituciones = instituciones;
        this.usuarios = usuarios;
        this.reloj = reloj;
    }

    @Override
    public Optional<Operador> porNombre(String nombre) {
        return Optional.ofNullable(operadores.get(nombre));
    }

    @Override
    public boolean crearSiNoHay(String nombre, String claveHash) {
        if (!operadores.isEmpty()) {
            return false;
        }
        operadores.put(nombre, new Operador(UUID.randomUUID(), claveHash));
        return true;
    }

    @Override
    public List<InstitucionDelOperador> instituciones() {
        return creadas.entrySet().stream()
                .map(e -> new InstitucionDelOperador(instituciones.porId(e.getKey()).orElseThrow(), e.getValue(), usuarios.contar(e.getKey()) > 0))
                .sorted(Comparator.comparing(InstitucionDelOperador::creadaEn)).toList();
    }

    @Override
    public UUID crearInstitucion(UUID operador, String nombre, String ruta) {
        verificar(operador);
        boolean repetida = creadas.keySet().stream().map(id -> instituciones.porId(id).orElseThrow()).anyMatch(i -> i.ruta().equals(ruta));
        if (repetida) {
            throw new RutaRepetida(ruta);
        }
        Institucion nueva = instituciones.agregar(new Institucion(UUID.randomUUID(), nombre, ruta, Institucion.Estado.ACTIVA));
        creadas.put(nueva.id(), reloj.ahora());
        eventos.add(0, new Evento(reloj.ahora(), Accion.CREAR_INSTITUCION, ruta, Optional.of(operador), nueva.id()));
        return nueva.id();
    }

    @Override
    public boolean cambiarEstado(UUID operador, UUID institucion, Institucion.Estado estado) {
        verificar(operador);
        Optional<Institucion> actual = instituciones.porId(institucion);
        if (actual.isEmpty()) {
            return false;
        }
        Institucion i = actual.get();
        instituciones.agregar(new Institucion(i.id(), i.nombre(), i.ruta(), estado));
        eventos.add(0, new Evento(reloj.ahora(), estado == Institucion.Estado.SUSPENDIDA ? Accion.SUSPENDER : Accion.REACTIVAR,
                i.ruta(), Optional.of(operador), institucion));
        return true;
    }

    @Override
    public UUID invitar(UUID operador, UUID institucion, String codigoHash, Instant caducaEn) {
        verificar(operador);
        Institucion i = instituciones.porId(institucion).orElseThrow(() -> new IllegalArgumentException("institución desconocida"));
        invitaciones.replaceAll((id, v) -> v.institucion().equals(institucion) && !v.usada() && v.caducaEn().isAfter(reloj.ahora())
                ? new Invitacion(v.id(), v.institucion(), v.codigoHash(), reloj.ahora(), false) : v);
        UUID id = UUID.randomUUID();
        invitaciones.put(id, new Invitacion(id, institucion, codigoHash, caducaEn, false));
        eventos.add(0, new Evento(reloj.ahora(), Accion.INVITAR, i.ruta(), Optional.of(operador), institucion));
        return id;
    }

    @Override
    public List<InvitacionVigente> vigentes(UUID institucion) {
        boolean activa = instituciones.porId(institucion).map(Institucion::activa).orElse(false);
        return invitaciones.values().stream()
                .filter(v -> activa && v.institucion().equals(institucion) && !v.usada() && v.caducaEn().isAfter(reloj.ahora()))
                .map(v -> new InvitacionVigente(v.id(), v.codigoHash())).toList();
    }

    @Override
    public boolean canjear(UUID invitacion) {
        Invitacion v = invitaciones.get(invitacion);
        if (v == null || v.usada() || !v.caducaEn().isAfter(reloj.ahora())) {
            return false;
        }
        invitaciones.put(invitacion, new Invitacion(v.id(), v.institucion(), v.codigoHash(), v.caducaEn(), true));
        String ruta = instituciones.porId(v.institucion()).map(Institucion::ruta).orElse(null);
        eventos.add(0, new Evento(reloj.ahora(), Accion.CANJEAR_INVITACION, ruta, Optional.empty(), v.institucion()));
        return true;
    }

    @Override
    public List<Evento> auditoria(int limite) {
        return eventos.stream().limit(limite).toList();
    }

    private void verificar(UUID operador) {
        if (operadores.values().stream().noneMatch(o -> o.id().equals(operador))) {
            throw new OperadorDesconocido();
        }
    }
}
