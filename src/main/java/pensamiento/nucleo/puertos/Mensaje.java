package pensamiento.nucleo.puertos;

/** Un turno de la conversación. */
public record Mensaje(Rol rol, String contenido) {

    public enum Rol { SISTEMA, USUARIO, ASISTENTE }

    public static Mensaje sistema(String contenido) {
        return new Mensaje(Rol.SISTEMA, contenido);
    }

    public static Mensaje usuario(String contenido) {
        return new Mensaje(Rol.USUARIO, contenido);
    }

    public static Mensaje asistente(String contenido) {
        return new Mensaje(Rol.ASISTENTE, contenido);
    }
}
