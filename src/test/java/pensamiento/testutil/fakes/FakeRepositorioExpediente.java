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
        return Optional.ofNullable(expedientes.get(id)).filter(e -> e.usuarioId().equals(usuarioId));
    }

    @Override
    public List<Expediente> deUsuario(UUID usuarioId) {
        return expedientes.values().stream()
                .filter(e -> e.usuarioId().equals(usuarioId))
                .sorted(Comparator.comparing(Expediente::creadoEn).reversed().thenComparing(e -> e.id().toString(), Comparator.reverseOrder()))
                .toList();
    }
}
