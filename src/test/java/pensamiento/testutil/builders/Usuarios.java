package pensamiento.testutil.builders;

import java.util.UUID;

import pensamiento.nucleo.Usuario;

/** Object Mother de cuentas: roles anónimos del universo ficticio. */
public final class Usuarios {

    private Usuarios() {
    }

    public static Usuario administrador(UUID institucion) {
        return new Usuario(UUID.randomUUID(), institucion, "administrador", "hash-admin", Usuario.RolGlobal.ADMINISTRADOR, true);
    }

    public static Usuario persona(UUID institucion, String nombre) {
        return new Usuario(UUID.randomUUID(), institucion, nombre, "hash-" + nombre, Usuario.RolGlobal.PERSONA, true);
    }
}
