package pensamiento.testutil.fakes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import pensamiento.nucleo.AfirmacionConRol;
import pensamiento.nucleo.Ejecucion;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Pendiente;
import pensamiento.nucleo.PendienteGuardado;
import pensamiento.nucleo.puertos.RepositorioEjecucion;

/**
 * Historial en memoria, idempotente por clave y filtrado por usuario, con sus afirmaciones y pendientes.
 * Certificado por FakeRepositorioEjecucionContractTest.
 */
public final class FakeRepositorioEjecucion implements RepositorioEjecucion {

    private static final Comparator<Ejecucion> DE_LA_MAS_RECIENTE =
            Comparator.comparing(Ejecucion::creadaEn).reversed().thenComparing(e -> e.id().toString(), Comparator.reverseOrder());

    private final Map<UUID, Ejecucion> ejecuciones = new LinkedHashMap<>();
    private final Map<UUID, List<AfirmacionConRol>> afirmaciones = new LinkedHashMap<>();
    private final List<PendienteGuardado> pendientes = new ArrayList<>();

    @Override
    public Ejecucion guardar(Ejecucion ejecucion, List<AfirmacionConRol> afirmacionesNuevas, List<Pendiente> pendientesNuevos) {
        Optional<Ejecucion> previa = ejecuciones.values().stream()
                .filter(e -> e.claveIdempotencia().equals(ejecucion.claveIdempotencia()))
                .findFirst();
        if (previa.isPresent()) {
            return previa.get();
        }
        ejecuciones.put(ejecucion.id(), ejecucion);
        afirmaciones.put(ejecucion.id(), List.copyOf(afirmacionesNuevas));
        for (Pendiente p : pendientesNuevos) {
            pendientes.add(new PendienteGuardado(UUID.randomUUID(), ejecucion.id(), p, false));
        }
        return ejecucion;
    }

    @Override
    public Optional<Ejecucion> porId(UUID usuarioId, UUID id) {
        return Optional.ofNullable(ejecuciones.get(id)).filter(e -> e.usuarioId().equals(usuarioId));
    }

    @Override
    public List<Ejecucion> porTecnica(UUID usuarioId, IdTecnica tecnica) {
        return deUsuario(usuarioId).filter(e -> e.tecnica().equals(tecnica)).sorted(DE_LA_MAS_RECIENTE).toList();
    }

    @Override
    public List<AfirmacionConRol> afirmacionesDe(UUID usuarioId, UUID ejecucionId) {
        return porId(usuarioId, ejecucionId).map(e -> afirmaciones.getOrDefault(e.id(), List.of())).orElse(List.of());
    }

    @Override
    public List<PendienteGuardado> pendientes(UUID usuarioId) {
        return pendientes.stream().filter(p -> !p.resuelto() && porId(usuarioId, p.ejecucionId()).isPresent()).toList();
    }

    @Override
    public List<Ejecucion> porExpediente(UUID usuarioId, UUID expedienteId) {
        return deUsuario(usuarioId).filter(e -> e.expedienteId().equals(Optional.of(expedienteId))).sorted(DE_LA_MAS_RECIENTE).toList();
    }

    @Override
    public List<Ejecucion> recientes(UUID usuarioId, int limite) {
        return deUsuario(usuarioId).sorted(DE_LA_MAS_RECIENTE).limit(limite).toList();
    }

    @Override
    public int cerrarPendientes(UUID usuarioId, pensamiento.nucleo.TipoPendiente tipo, UUID objetoId) {
        int cerrados = 0;
        for (int i = 0; i < pendientes.size(); i++) {
            PendienteGuardado p = pendientes.get(i);
            if (!p.resuelto() && p.pendiente().tipo() == tipo && p.pendiente().objetoId().equals(Optional.of(objetoId))
                    && porId(usuarioId, p.ejecucionId()).isPresent()) {
                pendientes.set(i, new PendienteGuardado(p.id(), p.ejecucionId(), p.pendiente(), true));
                cerrados++;
            }
        }
        return cerrados;
    }

    @Override
    public boolean asociar(UUID usuarioId, UUID ejecucionId, Optional<UUID> expedienteId) {
        Optional<Ejecucion> ejecucion = porId(usuarioId, ejecucionId);
        ejecucion.ifPresent(e -> ejecuciones.put(e.id(), new Ejecucion(e.id(), e.usuarioId(), e.institucionId(), e.tecnica(), e.versionEsquema(),
                expedienteId, e.config(), e.datos(), e.resultado(), e.resumen(), e.modelo(), e.claveIdempotencia(), e.creadaEn())));
        return ejecucion.isPresent();
    }


    public List<Ejecucion> todas() {
        return List.copyOf(ejecuciones.values());
    }

    private java.util.stream.Stream<Ejecucion> deUsuario(UUID usuarioId) {
        return ejecuciones.values().stream().filter(e -> e.usuarioId().equals(usuarioId));
    }
}
