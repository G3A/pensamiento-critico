package pensamiento.nucleo;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Una institución del servicio (un colegio, una organización). Se entra por su ruta: {@code /<ruta>}. Una institución
 * suspendida no deja entrar a nadie, pero conserva sus datos.
 */
public record Institucion(UUID id, String nombre, String ruta, Estado estado) {

    /** Minúsculas, dígitos y guiones, sin tildes; de 3 a 40 caracteres; sin guion al principio ni al final (V11). */
    public static final Pattern RUTA_VALIDA = Pattern.compile("^[a-z0-9][a-z0-9-]{1,38}[a-z0-9]$");

    public enum Estado {
        ACTIVA, SUSPENDIDA;

        public String enBaseDeDatos() {
            return name().toLowerCase();
        }
    }

    public boolean activa() {
        return estado == Estado.ACTIVA;
    }

    /** La ruta tal como llega en una dirección, si tiene la forma de una ruta de institución. */
    public static Optional<String> rutaValida(String ruta) {
        return Optional.ofNullable(ruta).filter(r -> RUTA_VALIDA.matcher(r).matches());
    }
}
