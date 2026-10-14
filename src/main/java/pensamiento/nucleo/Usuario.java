package pensamiento.nucleo;

import java.util.UUID;

/**
 * Cuenta por persona. Sin perfil invitado ni autoregistro: la crea el administrador con un nombre y la persona elige su
 * contraseña al canjear su código de alta (hito 9). El nombre es único dentro de la institución según
 * {@link #claveDeNombre(String)}. {@code claveHash} es vacío mientras la cuenta no tiene clave.
 */
public record Usuario(UUID id, UUID institucionId, String nombre, String claveHash, TipoClave tipoClave, RolGlobal rol, boolean activo) {

    /** Qué guarda {@code claveHash}: el PIN de las versiones anteriores, una contraseña o todavía nada. */
    public enum TipoClave {
        PIN, CONTRASENA, NINGUNA;

        public String enBaseDeDatos() {
            return name().toLowerCase();
        }
    }

    /**
     * Rol en la institución. Administrador y docente son adultos que administran a otros: contraseña de 12 caracteres o
     * más y llave de acceso como segundo factor. Persona y estudiante: 10 o más, sin llave (portátiles compartidos).
     * Docente y estudiante existen desde el hito 9 para que el aula (hito 10) no migre dos veces.
     */
    public enum RolGlobal {
        ADMINISTRADOR, DOCENTE, PERSONA, ESTUDIANTE;

        public String enBaseDeDatos() {
            return name().toLowerCase();
        }

        public boolean exigeLlave() {
            return this == ADMINISTRADOR || this == DOCENTE;
        }

        public int largoMinimoDeContrasena() {
            return exigeLlave() ? 12 : 10;
        }
    }

    /** Una cuenta nueva, activa y todavía sin clave. */
    public static Usuario nueva(UUID id, UUID institucionId, String nombre, RolGlobal rol) {
        return new Usuario(id, institucionId, nombre, "", TipoClave.NINGUNA, rol, true);
    }

    public boolean esAdministrador() {
        return rol == RolGlobal.ADMINISTRADOR;
    }

    public boolean tieneClave() {
        return tipoClave != TipoClave.NINGUNA;
    }

    public Usuario conClave(String hash, TipoClave tipo) {
        return new Usuario(id, institucionId, nombre, hash, tipo, rol, activo);
    }

    public Usuario conActivo(boolean valor) {
        return new Usuario(id, institucionId, nombre, claveHash, tipoClave, rol, valor);
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
