package pensamiento.nucleo.puertos;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import pensamiento.nucleo.Usuario;

/**
 * Cuentas de la instalación. En la versión 1 hay una sola institución por instalación: el repositorio
 * la conoce y la crea si no existe.
 */
public interface RepositorioUsuarios {

    /** La institución única de la instalación; vacío antes del primer arranque. */
    Optional<UUID> institucionUnica();

    /** Crea la institución única. Si ya existe una, devuelve la existente (idempotente). */
    UUID crearInstitucion(String nombre);

    /** Inserta o actualiza (por id). El nombre es único dentro de la institución: repetirlo lanza NombreRepetido. */
    Usuario guardar(Usuario usuario);

    Optional<Usuario> porId(UUID institucionId, UUID id);

    Optional<Usuario> porNombre(UUID institucionId, String nombre);

    /** Todos los usuarios de la institución, activos o no, ordenados por nombre. */
    List<Usuario> todos(UUID institucionId);

    long contar(UUID institucionId);

    class NombreRepetido extends RuntimeException {
        public NombreRepetido(String nombre) {
            super("Ya existe una persona llamada \"" + nombre + "\"");
        }
    }
}
