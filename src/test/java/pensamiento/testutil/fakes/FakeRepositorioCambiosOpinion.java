package pensamiento.testutil.fakes;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import pensamiento.nucleo.AfirmacionConRol;
import pensamiento.nucleo.CambioOpinion;
import pensamiento.nucleo.puertos.RepositorioCambiosOpinion;

/**
 * Fake en memoria de los cambios de opinión. Como el real, toma el texto de la afirmación ya guardada con la ejecución
 * (del Fake de ejecuciones). Certificado por FakeRepositorioCambiosOpinionContractTest.
 */
public final class FakeRepositorioCambiosOpinion implements RepositorioCambiosOpinion {

    private record Fila(UUID usuarioId, CambioOpinion cambio) {
    }

    private final FakeRepositorioEjecucion ejecuciones;
    private final Map<UUID, Fila> porId = new LinkedHashMap<>();

    public FakeRepositorioCambiosOpinion(FakeRepositorioEjecucion ejecuciones) {
        this.ejecuciones = ejecuciones;
    }

    @Override
    public void guardar(UUID usuarioId, UUID institucionId, UUID ejecucionId, List<CambioOpinion.Declarado> cambios, Instant cuando) {
        for (CambioOpinion.Declarado c : cambios) {
            porId.putIfAbsent(c.id(), new Fila(usuarioId, new CambioOpinion(c.id(), c.afirmacionId(), texto(usuarioId, ejecucionId, c.afirmacionId()),
                    c.confianzaAntes(), c.confianzaDespues(), c.causa(), Optional.of(ejecucionId), cuando)));
        }
    }

    @Override
    public void restaurar(UUID usuarioId, UUID institucionId, CambioOpinion c) {
        String texto = c.ejecucionId().map(e -> texto(usuarioId, e, c.afirmacionId())).orElse(c.texto());
        porId.putIfAbsent(c.id(), new Fila(usuarioId, new CambioOpinion(c.id(), c.afirmacionId(), texto, c.confianzaAntes(), c.confianzaDespues(),
                c.causa(), c.ejecucionId(), c.creadoEn())));
    }

    private String texto(UUID usuarioId, UUID ejecucionId, UUID afirmacionId) {
        return ejecuciones.afirmacionesDe(usuarioId, ejecucionId).stream().filter(a -> a.afirmacionId().equals(afirmacionId))
                .map(AfirmacionConRol::texto).findFirst()
                .orElseThrow(() -> new IllegalStateException("La afirmación del cambio no está guardada con su ejecución"));
    }

    @Override
    public List<CambioOpinion> deUsuario(UUID usuarioId) {
        return porId.values().stream().filter(f -> f.usuarioId().equals(usuarioId)).map(Fila::cambio)
                .sorted(Comparator.comparing(CambioOpinion::creadoEn).thenComparing(CambioOpinion::id)).toList();
    }

    @Override
    public List<CambioOpinion> deEjecucion(UUID usuarioId, UUID ejecucionId) {
        return deUsuario(usuarioId).stream().filter(c -> c.ejecucionId().equals(Optional.of(ejecucionId))).toList();
    }
}
