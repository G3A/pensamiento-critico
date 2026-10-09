package pensamiento.testutil.fakes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import pensamiento.nucleo.AfirmacionConRol;
import pensamiento.nucleo.EvidenciaGuardada;
import pensamiento.nucleo.FichaFuente;
import pensamiento.nucleo.SentidoAfirmacion;
import pensamiento.nucleo.puertos.RepositorioEvidencias;

/**
 * Fake en memoria de las evidencias con sus fuentes. Como el real, solo acepta evidencias sobre afirmaciones que la persona
 * produjo (en el Fake de ejecuciones), comparte la ficha de una fuente entre sus evidencias y, si la biblioteca ya no tiene
 * el documento citado, lo lee como "documento retirado". Certificado por FakeRepositorioEvidenciasContractTest.
 */
public final class FakeRepositorioEvidencias implements RepositorioEvidencias {

    private record Fila(UUID usuarioId, EvidenciaGuardada evidencia) {
    }

    private final FakeRepositorioEjecucion ejecuciones;
    private final FakeBiblioteca biblioteca;
    private final Map<UUID, Fila> porId = new LinkedHashMap<>();
    private final Map<UUID, FichaFuente> fuentes = new LinkedHashMap<>();

    public FakeRepositorioEvidencias(FakeRepositorioEjecucion ejecuciones, FakeBiblioteca biblioteca) {
        this.ejecuciones = ejecuciones;
        this.biblioteca = biblioteca;
    }

    private boolean esSuya(UUID usuarioId, UUID afirmacionId) {
        return ejecuciones.todas().stream().filter(e -> e.usuarioId().equals(usuarioId))
                .flatMap(e -> ejecuciones.afirmacionesDe(usuarioId, e.id()).stream())
                .anyMatch(a -> a.afirmacionId().equals(afirmacionId) && a.sentido() == SentidoAfirmacion.PRODUCIDA);
    }

    @Override
    public void guardar(UUID usuarioId, UUID institucionId, EvidenciaGuardada evidencia) {
        if (!esSuya(usuarioId, evidencia.afirmacionId())) {
            throw new IllegalArgumentException("La afirmación de la evidencia no existe o es de otra persona");
        }
        fuentes.put(evidencia.fuente().id(), evidencia.fuente());
        porId.putIfAbsent(evidencia.id(), new Fila(usuarioId, evidencia));
    }

    private EvidenciaGuardada leida(EvidenciaGuardada e) {
        FichaFuente f = fuentes.get(e.fuente().id());
        if (f.documentoId().isPresent() && !biblioteca.existe(f.documentoId().get())) {
            f = new FichaFuente(f.id(), f.titulo(), f.autor(), f.fecha(), f.tipo(), f.disenoEstudio(), f.grupoOrigen(), f.independiente(),
                    f.accesoOriginal(), f.puntajeCraap(), f.craap(), f.sift(), Optional.empty(), f.documentoNombre(), f.pagina());
        }
        return new EvidenciaGuardada(e.id(), e.afirmacionId(), f, e.fragmentoId(), e.pasaje(), e.postura(), e.fuerza(), e.etiquetadaPor(), e.adoptada());
    }

    @Override
    public List<EvidenciaGuardada> deAfirmacion(UUID usuarioId, UUID afirmacionId) {
        return deUsuario(usuarioId).stream().filter(e -> e.afirmacionId().equals(afirmacionId)).toList();
    }

    @Override
    public Optional<EvidenciaGuardada> porId(UUID usuarioId, UUID id) {
        return Optional.ofNullable(porId.get(id)).filter(f -> f.usuarioId().equals(usuarioId)).map(f -> leida(f.evidencia()));
    }

    @Override
    public boolean quitar(UUID usuarioId, UUID id) {
        Fila f = porId.get(id);
        if (f == null || !f.usuarioId().equals(usuarioId)) {
            return false;
        }
        porId.remove(id);
        UUID fuente = f.evidencia().fuente().id();
        if (porId.values().stream().noneMatch(x -> x.evidencia().fuente().id().equals(fuente))) {
            fuentes.remove(fuente);
        }
        return true;
    }

    @Override
    public List<EvidenciaGuardada> deUsuario(UUID usuarioId) {
        List<EvidenciaGuardada> lista = new ArrayList<>();
        for (Fila f : porId.values()) {
            if (f.usuarioId().equals(usuarioId)) {
                lista.add(leida(f.evidencia()));
            }
        }
        return lista;
    }

    /** Las afirmaciones del usuario que el Fake reconoce, para pruebas que las necesitan. */
    public List<AfirmacionConRol> afirmacionesDe(UUID usuarioId) {
        return ejecuciones.todas().stream().filter(e -> e.usuarioId().equals(usuarioId))
                .flatMap(e -> ejecuciones.afirmacionesDe(usuarioId, e.id()).stream()).toList();
    }
}
