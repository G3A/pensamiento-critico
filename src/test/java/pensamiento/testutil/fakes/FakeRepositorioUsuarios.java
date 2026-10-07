package pensamiento.testutil.fakes;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import pensamiento.nucleo.Usuario;
import pensamiento.nucleo.puertos.RepositorioUsuarios;

/** Cuentas en memoria. Certificado por FakeRepositorioUsuariosContractTest. */
public final class FakeRepositorioUsuarios implements RepositorioUsuarios {

    private UUID institucion;
    private final Map<UUID, Usuario> usuarios = new LinkedHashMap<>();

    @Override
    public Optional<UUID> institucionUnica() {
        return Optional.ofNullable(institucion);
    }

    @Override
    public UUID crearInstitucion(String nombre) {
        if (institucion == null) {
            institucion = UUID.randomUUID();
        }
        return institucion;
    }

    @Override
    public Usuario guardar(Usuario usuario) {
        boolean nombreRepetido = usuarios.values().stream()
                .anyMatch(u -> !u.id().equals(usuario.id()) && u.institucionId().equals(usuario.institucionId()) && u.nombre().equals(usuario.nombre()));
        if (nombreRepetido) {
            throw new NombreRepetido(usuario.nombre());
        }
        usuarios.put(usuario.id(), usuario);
        return usuario;
    }

    @Override
    public Optional<Usuario> porId(UUID institucionId, UUID id) {
        return Optional.ofNullable(usuarios.get(id)).filter(u -> u.institucionId().equals(institucionId));
    }

    @Override
    public Optional<Usuario> porNombre(UUID institucionId, String nombre) {
        return usuarios.values().stream().filter(u -> u.institucionId().equals(institucionId) && u.nombre().equals(nombre)).findFirst();
    }

    @Override
    public List<Usuario> todos(UUID institucionId) {
        return usuarios.values().stream().filter(u -> u.institucionId().equals(institucionId))
                .sorted(Comparator.comparing(Usuario::nombre)).toList();
    }

    @Override
    public long contar(UUID institucionId) {
        return usuarios.values().stream().filter(u -> u.institucionId().equals(institucionId)).count();
    }
}
