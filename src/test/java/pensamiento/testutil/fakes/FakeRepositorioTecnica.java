package pensamiento.testutil.fakes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

import pensamiento.nucleo.Ejemplo;
import pensamiento.nucleo.Familia;
import pensamiento.nucleo.RelacionTecnica;
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

    private final List<Ejemplo> ejemplos = new ArrayList<>();
    private final List<RelacionTecnica> relaciones = new ArrayList<>();

    public void agregarEjemplo(Ejemplo ejemplo) {
        ejemplos.removeIf(e -> e.id().equals(ejemplo.id()));
        ejemplos.add(ejemplo);
    }

    public void agregarRelacion(RelacionTecnica relacion) {
        if (!relaciones.contains(relacion)) {
            relaciones.add(relacion);
        }
    }

    @Override
    public List<Ejemplo> ejemplos(IdTecnica tecnica) {
        return ejemplos.stream().filter(e -> e.tecnica().equals(tecnica))
                .sorted(Comparator.comparingInt(Ejemplo::orden).thenComparing(Ejemplo::titulo)).toList();
    }

    @Override
    public Optional<Ejemplo> ejemplo(UUID id) {
        return ejemplos.stream().filter(e -> e.id().equals(id)).findFirst();
    }

    @Override
    public List<RelacionTecnica> relaciones(IdTecnica tecnica) {
        return relaciones.stream().filter(r -> r.origen().equals(tecnica) || r.destino().equals(tecnica)).toList();
    }
}
