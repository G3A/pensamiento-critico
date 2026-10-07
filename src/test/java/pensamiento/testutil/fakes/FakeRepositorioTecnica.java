package pensamiento.testutil.fakes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;

import pensamiento.nucleo.Familia;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Tecnica;
import pensamiento.nucleo.puertos.RepositorioTecnica;

/** Catálogo en memoria. Certificado por FakeRepositorioTecnicaContractTest. */
public final class FakeRepositorioTecnica implements RepositorioTecnica {

    private final TreeMap<IdTecnica, Tecnica> tecnicas = new TreeMap<>();
    private final List<Familia> familias = new ArrayList<>();

    public void agregarFamilia(Familia familia) {
        familias.removeIf(f -> f.codigo().equals(familia.codigo()));
        familias.add(familia);
    }

    public void agregar(Tecnica tecnica) {
        tecnicas.put(tecnica.id(), tecnica);
    }

    public void quitar(IdTecnica id) {
        tecnicas.remove(id);
    }

    @Override
    public List<Familia> familias() {
        return familias.stream().sorted(Comparator.comparingInt(Familia::orden)).toList();
    }

    @Override
    public List<Tecnica> todas() {
        return List.copyOf(tecnicas.values());
    }

    @Override
    public List<Tecnica> porFamilia(String codigoFamilia) {
        return tecnicas.values().stream().filter(t -> t.familia().equals(codigoFamilia)).toList();
    }

    @Override
    public Optional<Tecnica> porId(IdTecnica id) {
        return Optional.ofNullable(tecnicas.get(id));
    }

    @Override
    public long contar() {
        return tecnicas.size();
    }
}
