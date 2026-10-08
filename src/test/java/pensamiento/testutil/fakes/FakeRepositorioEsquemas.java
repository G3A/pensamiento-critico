package pensamiento.testutil.fakes;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import pensamiento.catalogo.CatalogoJson;
import pensamiento.nucleo.Esquema;
import pensamiento.nucleo.puertos.RepositorioEsquemas;

/**
 * Fake del catálogo de esquemas de Walton: en memoria, con el contenido de catalogo/esquemas.json, que es
 * lo mismo que la semilla deja en la tabla esquema_walton. Certificado por FakeRepositorioEsquemasContractTest.
 */
public final class FakeRepositorioEsquemas implements RepositorioEsquemas {

    private final List<Esquema> esquemas;

    public FakeRepositorioEsquemas() {
        this(new CatalogoJson().esquemas());
    }

    public FakeRepositorioEsquemas(List<Esquema> esquemas) {
        this.esquemas = esquemas.stream().sorted(Comparator.comparing(Esquema::id)).toList();
    }

    @Override
    public List<Esquema> todos() {
        return esquemas;
    }

    @Override
    public Optional<Esquema> porId(String id) {
        return esquemas.stream().filter(e -> e.id().equals(id)).findFirst();
    }
}
