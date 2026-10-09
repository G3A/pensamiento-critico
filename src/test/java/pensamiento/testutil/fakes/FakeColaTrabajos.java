package pensamiento.testutil.fakes;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import pensamiento.nucleo.Json;
import pensamiento.nucleo.Trabajo;
import pensamiento.nucleo.puertos.ColaTrabajos;

/** Fake en memoria de la cola de trabajos, en orden de llegada. Certificado por FakeColaTrabajosContractTest. */
public final class FakeColaTrabajos implements ColaTrabajos {

    private final Map<UUID, Trabajo> trabajos = new LinkedHashMap<>();
    private Instant creacion = Instant.parse("2026-10-09T12:00:00Z");

    @Override
    public UUID encolar(String tipo, Json payload, Instant disponibleEn) {
        UUID id = UUID.randomUUID();
        creacion = creacion.plusMillis(1);
        trabajos.put(id, new Trabajo(id, tipo, Trabajo.Estado.PENDIENTE, 0, payload, Optional.empty(), creacion, disponibleEn));
        return id;
    }

    @Override
    public Optional<Trabajo> tomar(Instant ahora, Set<String> tipos) {
        Optional<Trabajo> siguiente = trabajos.values().stream()
                .filter(t -> t.estado() == Trabajo.Estado.PENDIENTE && !t.disponibleEn().isAfter(ahora) && tipos.contains(t.tipo())).findFirst();
        siguiente.ifPresent(t -> poner(new Trabajo(t.id(), t.tipo(), Trabajo.Estado.EN_PROCESO, t.intentos() + 1, t.payload(), t.error(), t.creadoEn(),
                t.disponibleEn())));
        return siguiente.map(t -> trabajos.get(t.id()));
    }

    @Override
    public void terminar(UUID id) {
        cambiar(id, Trabajo.Estado.HECHO, Optional.empty(), null);
    }

    @Override
    public void reintentar(UUID id, Instant disponibleEn, String motivo) {
        cambiar(id, Trabajo.Estado.PENDIENTE, Optional.of(motivo), disponibleEn);
    }

    @Override
    public void fallar(UUID id, String motivo) {
        cambiar(id, Trabajo.Estado.ERROR, Optional.of(motivo), null);
    }

    @Override
    public int reencolarEnProceso(Set<String> tipos) {
        int n = 0;
        for (Trabajo t : java.util.List.copyOf(trabajos.values())) {
            if (t.estado() == Trabajo.Estado.EN_PROCESO && tipos.contains(t.tipo())) {
                cambiar(t.id(), Trabajo.Estado.PENDIENTE, t.error(), null);
                n++;
            }
        }
        return n;
    }

    @Override
    public Optional<Trabajo> porId(UUID id) {
        return Optional.ofNullable(trabajos.get(id));
    }

    private void cambiar(UUID id, Trabajo.Estado estado, Optional<String> error, Instant disponibleEn) {
        Trabajo t = trabajos.get(id);
        if (t != null) {
            poner(new Trabajo(id, t.tipo(), estado, t.intentos(), t.payload(), error, t.creadoEn(), disponibleEn == null ? t.disponibleEn() : disponibleEn));
        }
    }

    private void poner(Trabajo t) {
        trabajos.put(t.id(), t);
    }
}
