package pensamiento.web.operador;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.function.IntUnaryOperator;

/**
 * Código de invitación de 8 caracteres en base32 de Crockford (sin I, L, O ni U, que se confunden con 1, 0 y V). Se
 * muestra en dos grupos de cuatro ("7KQ2-M9XD") y al canjearlo se aceptan minúsculas, guiones, espacios y las letras
 * que se confunden (la I y la L cuentan como 1, la O como 0).
 */
public final class CodigoDeInvitacion {

    public static final String ALFABETO = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    public static final int LARGO = 8;

    private final IntUnaryOperator azar;

    /** {@code azar} recibe el tamaño del alfabeto y devuelve un índice entre 0 y ese tamaño (sin incluirlo). */
    public CodigoDeInvitacion(IntUnaryOperator azar) {
        this.azar = azar;
    }

    public static CodigoDeInvitacion aleatorio() {
        SecureRandom fuente = new SecureRandom();
        return new CodigoDeInvitacion(fuente::nextInt);
    }

    /** Un código nuevo, ya normalizado (8 caracteres del alfabeto, sin guion). */
    public String nuevo() {
        StringBuilder codigo = new StringBuilder(LARGO);
        for (int i = 0; i < LARGO; i++) {
            codigo.append(ALFABETO.charAt(azar.applyAsInt(ALFABETO.length())));
        }
        return codigo.toString();
    }

    /** Para mostrarlo: "7KQ2M9XD" → "7KQ2-M9XD". */
    public static String legible(String codigo) {
        return codigo.substring(0, LARGO / 2) + "-" + codigo.substring(LARGO / 2);
    }

    /** Lo que escribió la persona, llevado a la forma guardada; vacío si no puede ser un código. */
    public static String normalizar(String escrito) {
        if (escrito == null) {
            return "";
        }
        String limpio = escrito.toUpperCase(Locale.ROOT).replaceAll("[\\s-]", "")
                .replace('I', '1').replace('L', '1').replace('O', '0');
        if (limpio.length() != LARGO || !limpio.chars().allMatch(c -> ALFABETO.indexOf(c) >= 0)) {
            return "";
        }
        return limpio;
    }
}
