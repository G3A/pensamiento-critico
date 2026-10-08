package pensamiento.testutil.fakes;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import pensamiento.nucleo.AfirmacionConRol;
import pensamiento.nucleo.Prediccion;
import pensamiento.nucleo.PrediccionDeclarada;
import pensamiento.nucleo.puertos.RepositorioPredicciones;

/**
 * Fake en memoria de las predicciones de cada persona. Como el real, toma el texto de la afirmación ya guardada con la
 * ejecución (del Fake de ejecuciones). Certificado por FakeRepositorioPrediccionesContractTest.
 */
public final class FakeRepositorioPredicciones implements RepositorioPredicciones {

    private record Fila(UUID usuarioId, Prediccion prediccion) {
    }

    private final FakeRepositorioEjecucion ejecuciones;
    private final Map<UUID, Fila> porId = new LinkedHashMap<>();

    public FakeRepositorioPredicciones(FakeRepositorioEjecucion ejecuciones) {
        this.ejecuciones = ejecuciones;
    }

    @Override
    public void guardar(UUID usuarioId, UUID institucionId, UUID ejecucionId, List<PrediccionDeclarada> predicciones) {
        for (PrediccionDeclarada p : predicciones) {
            String texto = ejecuciones.afirmacionesDe(usuarioId, ejecucionId).stream().filter(a -> a.afirmacionId().equals(p.afirmacionId()))
                    .map(AfirmacionConRol::texto).findFirst()
                    .orElseThrow(() -> new IllegalStateException("La afirmación de la predicción no está guardada con su ejecución"));
            porId.putIfAbsent(p.id(), new Fila(usuarioId, new Prediccion(p.id(), ejecucionId, p.afirmacionId(), texto, p.confianza(),
                    p.fechaRevision(), Prediccion.Estado.PENDIENTE, Optional.empty())));
        }
    }

    @Override
    public Optional<Prediccion> porId(UUID usuarioId, UUID prediccionId) {
        return Optional.ofNullable(porId.get(prediccionId)).filter(f -> f.usuarioId().equals(usuarioId)).map(Fila::prediccion);
    }

    @Override
    public List<Prediccion> deUsuario(UUID usuarioId) {
        return porId.values().stream().filter(f -> f.usuarioId().equals(usuarioId)).map(Fila::prediccion)
                .sorted(Comparator.comparing(Prediccion::fechaRevision).thenComparing(Prediccion::id)).toList();
    }

    @Override
    public List<Prediccion> deEjecucion(UUID usuarioId, UUID ejecucionId) {
        return porId.values().stream().filter(f -> f.usuarioId().equals(usuarioId) && f.prediccion().ejecucionId().equals(ejecucionId))
                .map(Fila::prediccion).toList();
    }

    @Override
    public Optional<Prediccion> resolver(UUID usuarioId, UUID prediccionId, boolean seCumplio, Instant cuando) {
        Optional<Prediccion> actual = porId(usuarioId, prediccionId);
        actual.ifPresent(p -> porId.put(p.id(), new Fila(usuarioId, p.resolver(seCumplio, cuando))));
        return actual.flatMap(p -> porId(usuarioId, p.id()));
    }
}
