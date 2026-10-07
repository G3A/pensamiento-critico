package pensamiento.testutil.fakes;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import pensamiento.nucleo.Ejecucion;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.puertos.RepositorioEjecucion;

/** Historial en memoria, idempotente por clave y filtrado por usuario. Certificado por FakeRepositorioEjecucionContractTest. */
public final class FakeRepositorioEjecucion implements RepositorioEjecucion {

    private final Map<UUID, Ejecucion> ejecuciones = new LinkedHashMap<>();

    @Override
    public Ejecucion guardar(Ejecucion ejecucion) {
        Optional<Ejecucion> previa = ejecuciones.values().stream()
                .filter(e -> e.claveIdempotencia().equals(ejecucion.claveIdempotencia()))
                .findFirst();
        if (previa.isPresent()) {
            return previa.get();
        }
        ejecuciones.put(ejecucion.id(), ejecucion);
        return ejecucion;
    }

    @Override
    public Optional<Ejecucion> porId(UUID usuarioId, UUID id) {
        return Optional.ofNullable(ejecuciones.get(id)).filter(e -> e.usuarioId().equals(usuarioId));
    }

    @Override
    public List<Ejecucion> porTecnica(UUID usuarioId, IdTecnica tecnica) {
        return ejecuciones.values().stream()
                .filter(e -> e.usuarioId().equals(usuarioId) && e.tecnica().equals(tecnica))
                .sorted(Comparator.comparing(Ejecucion::creadaEn).reversed().thenComparing(e -> e.id().toString(), Comparator.reverseOrder()))
                .toList();
    }

    public List<Ejecucion> todas() {
        return List.copyOf(ejecuciones.values());
    }
}
