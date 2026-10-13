package pensamiento.nucleo;

import java.util.UUID;

/** Cuenta por persona. Sin perfil invitado ni autoregistro: la crea el administrador y siempre tiene PIN.
 * El nombre es único dentro de la institución según {@link #claveDeNombre(String)}. */
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

    private static final String CON_TILDE = "ÁÀÂÄÃÉÈÊËÍÌÎÏÓÒÔÖÕÚÙÛÜÑÇáàâäãéèêëíìîïóòôöõúùûüñç";
    private static final String SIN_TILDE = "AAAAAEEEEIIIIOOOOOUUUUNCaaaaaeeeeiiiiooooouuuunc";

    /**
     * Clave del nombre dentro de su institución: sin espacios al borde ni repetidos, sin tildes ni diéresis, en minúsculas.
     * Es la misma regla que app_clave_nombre (V11): "Ana María", "ana  maria" y "ANA MARÍA" son la misma persona.
     */
    public static String claveDeNombre(String nombre) {
        StringBuilder clave = new StringBuilder();
        for (char c : nombre.replaceAll("\\s+", " ").strip().toCharArray()) {
            int i = CON_TILDE.indexOf(c);
            clave.append(i >= 0 ? SIN_TILDE.charAt(i) : c);
        }
        return clave.toString().toLowerCase(java.util.Locale.ROOT);
    }
}
