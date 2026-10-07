package pensamiento.nucleo.puertos;

import java.util.List;
import java.util.Optional;

import pensamiento.nucleo.Familia;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Tecnica;

/** Lectura del catálogo canónico. Es compartido: no se filtra por usuario. */
public interface RepositorioTecnica {

    /** Las familias en su orden de navegación. */
    List<Familia> familias();

    /** Todas las técnicas ordenadas por identificador. */
    List<Tecnica> todas();

    /** Las técnicas de una familia (F1 a F8) ordenadas por identificador; vacía si la familia no existe. */
    List<Tecnica> porFamilia(String codigoFamilia);

    Optional<Tecnica> porId(IdTecnica id);

    long contar();
}
