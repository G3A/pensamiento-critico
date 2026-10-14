package pensamiento.testutil.fakes;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import pensamiento.nucleo.puertos.RegistroIntentos;

/** Contadores de intentos en memoria. Certificado por FakeRegistroIntentosContractTest. */
public final class FakeRegistroIntentos implements RegistroIntentos {

    private record Llave(Optional<UUID> institucion, String clave) {
    }

    private final Map<Llave, Estado> estados = new LinkedHashMap<>();

    @Override
    public Optional<Estado> leer(Optional<UUID> institucion, String clave) {
        return Optional.ofNullable(estados.get(new Llave(institucion, clave)));
    }

    @Override
    public void guardar(Optional<UUID> institucion, String clave, Estado estado) {
        estados.put(new Llave(institucion, clave), estado);
    }

    @Override
    public void borrar(Optional<UUID> institucion, String clave) {
        estados.remove(new Llave(institucion, clave));
    }

    @Override
    public List<Espera> esperas(UUID institucion, Instant ahora) {
        return estados.entrySet().stream()
                .filter(e -> e.getKey().institucion().equals(Optional.of(institucion)) && e.getKey().clave().startsWith("cuenta:"))
                .filter(e -> e.getValue().esperaHasta().map(h -> h.isAfter(ahora)).orElse(false))
                .map(e -> new Espera(e.getKey().clave(), e.getValue().esperaHasta().orElseThrow()))
                .sorted(Comparator.comparing(Espera::clave)).toList();
    }
}
