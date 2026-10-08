package pensamiento.nucleo.puertos;

import java.util.List;
import java.util.Optional;

import pensamiento.nucleo.Esquema;

/** Catálogo compartido de esquemas de Walton (tabla esquema_walton, sembrada desde catalogo/esquemas.json). */
public interface RepositorioEsquemas {

    /** Todos, ordenados por identificador. */
    List<Esquema> todos();

    Optional<Esquema> porId(String id);
}
