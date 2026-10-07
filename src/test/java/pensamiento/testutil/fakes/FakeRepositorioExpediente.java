package pensamiento.testutil.fakes;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import pensamiento.nucleo.Expediente;
import pensamiento.nucleo.puertos.RepositorioExpediente;

/** Expedientes en memoria filtrados por usuario. Certificado por FakeRepositorioExpedienteContractTest. */
public final class FakeRepositorioExpediente implements RepositorioExpediente {

    private final Map<UUID, Expediente> expedientes = new LinkedHashMap<>();

    @Override
    public Expediente guardar(Expediente expediente) {
        expedientes.put(expediente.id(), expediente);
        return expediente;
    }

    @Override
    public Optional<Expediente> porId(UUID usuarioId, UUID id) {
        return Optional.ofNullable(expedientes.get(id)).filter(e -> e.usuarioId().equals(usuarioId) && !borrados.contains(e.id()));
    }

    private final java.util.Set<UUID> borrados = new java.util.HashSet<>();

    @Override
    public boolean borrar(UUID usuarioId, UUID id, java.time.Instant cuando) {
        if (porId(usuarioId, id).isEmpty()) {
            return false;
        }
        return borrados.add(id);
    }

    @Override
    public List<Expediente> deUsuario(UUID usuarioId) {
        return expedientes.values().stream()
                .filter(e -> e.usuarioId().equals(usuarioId) && !borrados.contains(e.id()))
                .sorted(Comparator.comparing(Expediente::creadoEn).reversed().thenComparing(e -> e.id().toString(), Comparator.reverseOrder()))
                .toList();
    }
}
