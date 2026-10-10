package pensamiento.testutil.fakes;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import pensamiento.nucleo.AfirmacionConRol;
import pensamiento.nucleo.Ejecucion;
import pensamiento.nucleo.Expediente;
import pensamiento.nucleo.RolAfirmacion;
import pensamiento.nucleo.puertos.RegistroDeRazonamiento;

/**
 * Lectura del diario y del registro de cambios sobre los Fakes de ejecuciones, expedientes y cambios de opinión, como el real
 * la hace con SQL sobre sus tablas. Certificado por FakeRegistroDeRazonamientoContractTest.
 */
public final class FakeRegistroDeRazonamiento implements RegistroDeRazonamiento {

    private final FakeRepositorioEjecucion ejecuciones;
    private final FakeRepositorioExpediente expedientes;
    private final FakeRepositorioCambiosOpinion cambios;

    public FakeRegistroDeRazonamiento(FakeRepositorioEjecucion ejecuciones, FakeRepositorioExpediente expedientes,
                                      FakeRepositorioCambiosOpinion cambios) {
        this.ejecuciones = ejecuciones;
        this.expedientes = expedientes;
        this.cambios = cambios;
    }

    private List<Ejecucion> deUsuario(UUID usuarioId) {
        return ejecuciones.recientes(usuarioId, Integer.MAX_VALUE).stream()
                .sorted(Comparator.comparing(Ejecucion::creadaEn).thenComparing(Ejecucion::id)).toList();
    }

    @Override
    public List<EjecucionEnDiario> ejecucionesDesde(UUID usuarioId, Instant desde) {
        return deUsuario(usuarioId).stream().filter(e -> !e.creadaEn().isBefore(desde))
                .map(e -> new EjecucionEnDiario(e.id(), e.tecnica(), e.resumen(),
                        e.expedienteId().flatMap(x -> expedientes.porId(usuarioId, x)).map(Expediente::nombre),
                        cambios.deEjecucion(usuarioId, e.id()).size(), e.creadaEn()))
                .toList();
    }

    @Override
    public List<CambioRegistrado> cambios(UUID usuarioId) {
        return cambios.deUsuario(usuarioId).stream()
                .map(c -> new CambioRegistrado(c, c.ejecucionId().flatMap(e -> ejecuciones.porId(usuarioId, e)).map(Ejecucion::tecnica)))
                .toList();
    }

    @Override
    public List<PosturaRegistrada> posturas(UUID usuarioId) {
        Map<UUID, PosturaRegistrada> porAfirmacion = new LinkedHashMap<>();
        for (Ejecucion e : deUsuario(usuarioId)) {
            for (AfirmacionConRol a : ejecuciones.afirmacionesDe(usuarioId, e.id())) {
                if (a.rol() == RolAfirmacion.POSTURA) {
                    String texto = Optional.ofNullable(porAfirmacion.get(a.afirmacionId())).map(PosturaRegistrada::texto).orElse(a.texto());
                    porAfirmacion.put(a.afirmacionId(), new PosturaRegistrada(a.afirmacionId(), texto, e.creadaEn()));
                }
            }
        }
        return porAfirmacion.values().stream()
                .sorted(Comparator.comparing(PosturaRegistrada::ultimaVez).thenComparing(PosturaRegistrada::afirmacionId)).toList();
    }
}
