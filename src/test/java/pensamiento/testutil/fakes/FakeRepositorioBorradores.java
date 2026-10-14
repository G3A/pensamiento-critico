package pensamiento.testutil.fakes;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import pensamiento.nucleo.puertos.RepositorioBorradores;

/** Borradores en memoria, uno por persona y formulario. Certificado por FakeRepositorioBorradoresContractTest. */
public final class FakeRepositorioBorradores implements RepositorioBorradores {

    private record Clave(UUID usuario, String clave) {
    }

    private final Map<Clave, Borrador> borradores = new LinkedHashMap<>();

    @Override
    public void guardar(UUID usuarioId, UUID institucionId, String clave, Map<String, String> campos, Instant ahora) {
        borradores.put(new Clave(usuarioId, clave), new Borrador(clave, new LinkedHashMap<>(campos), ahora));
    }

    @Override
    public Optional<Borrador> leer(UUID usuarioId, String clave) {
        return Optional.ofNullable(borradores.get(new Clave(usuarioId, clave)));
    }

    @Override
    public boolean borrar(UUID usuarioId, String clave) {
        return borradores.remove(new Clave(usuarioId, clave)) != null;
    }
}
