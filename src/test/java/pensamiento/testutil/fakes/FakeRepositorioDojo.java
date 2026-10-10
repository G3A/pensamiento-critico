package pensamiento.testutil.fakes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import pensamiento.nucleo.Competencia;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.IntentoDojo;
import pensamiento.nucleo.puertos.RepositorioDojo;

/** Intentos del Dojo y competencia en memoria. Certificado por FakeRepositorioDojoContractTest. */
public final class FakeRepositorioDojo implements RepositorioDojo {

    private record Fila(UUID usuarioId, IntentoDojo intento) {
    }

    private record Clave(UUID usuarioId, IdTecnica tecnica) {
    }

    private final List<Fila> intentos = new ArrayList<>();
    private final Map<Clave, Competencia> competencias = new LinkedHashMap<>();

    @Override
    public boolean guardar(UUID usuarioId, UUID institucionId, IntentoDojo intento, Competencia competencia) {
        if (!intento.tecnica().equals(competencia.tecnica())) {
            throw new IllegalArgumentException("La competencia tiene que ser del tema del intento");
        }
        if (!insertar(usuarioId, intento)) {
            return false;
        }
        restaurar(usuarioId, institucionId, competencia);
        return true;
    }

    private boolean insertar(UUID usuarioId, IntentoDojo intento) {
        boolean existe = intentos.stream().anyMatch(f -> f.intento().id().equals(intento.id())
                || f.usuarioId().equals(usuarioId) && f.intento().clave().equals(intento.clave()));
        if (!existe) {
            intentos.add(new Fila(usuarioId, intento));
        }
        return !existe;
    }

    @Override
    public List<IntentoDojo> intentos(UUID usuarioId) {
        return intentos.stream().filter(f -> f.usuarioId().equals(usuarioId)).map(Fila::intento)
                .sorted(Comparator.comparing(IntentoDojo::creadoEn).thenComparing(IntentoDojo::id)).toList();
    }

    @Override
    public List<Competencia> competencias(UUID usuarioId) {
        return competencias.entrySet().stream().filter(e -> e.getKey().usuarioId().equals(usuarioId)).map(Map.Entry::getValue)
                .sorted(Comparator.comparing(Competencia::tecnica)).toList();
    }

    @Override
    public void restaurar(UUID usuarioId, UUID institucionId, IntentoDojo intento) {
        insertar(usuarioId, intento);
    }

    @Override
    public void restaurar(UUID usuarioId, UUID institucionId, Competencia competencia) {
        competencias.put(new Clave(usuarioId, competencia.tecnica()), competencia);
    }
}
