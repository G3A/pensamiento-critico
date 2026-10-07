package pensamiento.testutil.fakes;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Json;
import pensamiento.nucleo.puertos.RepositorioConfiguracion;

/** Configuración por usuario en memoria. Certificado por FakeRepositorioConfiguracionContractTest. */
public final class FakeRepositorioConfiguracion implements RepositorioConfiguracion {

    private final Map<UUID, TreeMap<IdTecnica, Guardada>> porUsuario = new LinkedHashMap<>();

    @Override
    public Optional<Guardada> de(UUID usuarioId, IdTecnica tecnica) {
        return Optional.ofNullable(porUsuario.getOrDefault(usuarioId, new TreeMap<>()).get(tecnica));
    }

    @Override
    public void guardar(UUID usuarioId, UUID institucionId, IdTecnica tecnica, int versionEsquema, Json valores) {
        porUsuario.computeIfAbsent(usuarioId, u -> new TreeMap<>()).put(tecnica, new Guardada(versionEsquema, valores));
    }

    @Override
    public void restablecer(UUID usuarioId, IdTecnica tecnica) {
        porUsuario.getOrDefault(usuarioId, new TreeMap<>()).remove(tecnica);
    }

    @Override
    public Map<IdTecnica, Guardada> todas(UUID usuarioId) {
        return new LinkedHashMap<>(porUsuario.getOrDefault(usuarioId, new TreeMap<>()));
    }
}
