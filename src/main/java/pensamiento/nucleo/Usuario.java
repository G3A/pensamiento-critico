package pensamiento.nucleo;

import java.util.UUID;

/** Cuenta por persona. Sin perfil invitado ni autoregistro: la crea el administrador y siempre tiene PIN. */
public record Usuario(UUID id, UUID institucionId, String nombre, String pinHash, RolGlobal rol, boolean activo) {

    public enum RolGlobal {
        ADMINISTRADOR, PERSONA;

        public String enBaseDeDatos() {
            return name().toLowerCase();
        }
    }

    public boolean esAdministrador() {
        return rol == RolGlobal.ADMINISTRADOR;
    }
}
