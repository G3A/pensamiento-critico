package pensamiento.testutil.fakes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import pensamiento.nucleo.ArgumentoProducido;
import pensamiento.nucleo.puertos.RepositorioArgumentos;

/** Fake en memoria de los argumentos de cada persona. Certificado por FakeRepositorioArgumentosContractTest. */
public final class FakeRepositorioArgumentos implements RepositorioArgumentos {

    private record Fila(UUID usuarioId, ArgumentoGuardado guardado) {
    }

    private final Map<UUID, Fila> porId = new LinkedHashMap<>();

    @Override
    public void guardar(UUID usuarioId, UUID institucionId, UUID ejecucionId, List<ArgumentoProducido> argumentos) {
        for (int i = 0; i < argumentos.size(); i++) {
            ArgumentoProducido a = argumentos.get(i);
            porId.putIfAbsent(a.argumento().id(), new Fila(usuarioId, new ArgumentoGuardado(ejecucionId, i + 1, a)));
        }
    }

    @Override
    public List<ArgumentoGuardado> deEjecucion(UUID usuarioId, UUID ejecucionId) {
        List<ArgumentoGuardado> suyos = new ArrayList<>();
        porId.values().stream().filter(f -> f.usuarioId().equals(usuarioId) && f.guardado().ejecucionId().equals(ejecucionId))
                .map(Fila::guardado).sorted(Comparator.comparingInt(ArgumentoGuardado::orden)).forEach(suyos::add);
        return suyos;
    }

    @Override
    public Optional<ArgumentoGuardado> porId(UUID usuarioId, UUID argumentoId) {
        return Optional.ofNullable(porId.get(argumentoId)).filter(f -> f.usuarioId().equals(usuarioId)).map(Fila::guardado);
    }

    @Override
    public List<ArgumentoGuardado> conPremisa(UUID usuarioId, UUID afirmacionId) {
        return porId.values().stream().filter(f -> f.usuarioId().equals(usuarioId))
                .map(Fila::guardado)
                .filter(g -> g.argumento().argumento().premisas().stream().anyMatch(p -> p.afirmacionId().equals(afirmacionId)))
                .sorted(Comparator.comparing((ArgumentoGuardado g) -> g.ejecucionId()).thenComparingInt(ArgumentoGuardado::orden))
                .toList();
    }

    /** Cuántos argumentos hay guardados en total, de cualquier persona. */
    public int total() {
        return porId.size();
    }
}
