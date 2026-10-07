package pensamiento.nucleo.puertos;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import pensamiento.nucleo.Ejecucion;
import pensamiento.nucleo.IdTecnica;

/**
 * Historial de ejecuciones. Todo método recibe el usuario de la sesión y solo devuelve lo suyo;
 * la implementación real además corre bajo RLS, que es la segunda cerradura.
 */
public interface RepositorioEjecucion {

    /** Guarda; si ya existe una ejecución con la misma clave de idempotencia, devuelve esa sin duplicar. */
    Ejecucion guardar(Ejecucion ejecucion);

    Optional<Ejecucion> porId(UUID usuarioId, UUID id);

    /** De la más reciente a la más antigua. */
    List<Ejecucion> porTecnica(UUID usuarioId, IdTecnica tecnica);
}
