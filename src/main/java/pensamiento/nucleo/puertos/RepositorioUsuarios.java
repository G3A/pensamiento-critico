package pensamiento.nucleo.puertos;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import pensamiento.nucleo.Usuario;

/**
 * Cuentas de cada institución (hito 8: ya no hay institución única; la fija la ruta de entrada). El nombre es único dentro
 * de la institución sin distinguir mayúsculas, tildes ni espacios repetidos ({@link Usuario#claveDeNombre(String)}).
 */
public interface RepositorioUsuarios {

    /** Inserta o actualiza (por id). Repetir la clave del nombre dentro de la institución lanza NombreRepetido. */
    Usuario guardar(Usuario usuario);

    Optional<Usuario> porId(UUID institucionId, UUID id);

    /** Busca por la clave del nombre: "ANA MARÍA" encuentra a "Ana María". */
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
