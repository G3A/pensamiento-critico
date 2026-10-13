package pensamiento.nucleo.puertos;

import java.util.Optional;
import java.util.UUID;

import pensamiento.nucleo.Institucion;

/**
 * Instituciones vistas desde la aplicación (hito 8). Antes de entrar solo se sabe la ruta: {@link #activaPorRuta}
 * devuelve el id de una institución activa y nada más. Con sesión, cada persona ve solo la suya.
 */
public interface RepositorioInstituciones {

    /** El id de la institución activa con esa ruta; vacío si no existe o está suspendida. */
    Optional<UUID> activaPorRuta(String ruta);

    /** La institución de la sesión actual (bajo RLS solo se ve la propia). */
    Optional<Institucion> porId(UUID id);
}
