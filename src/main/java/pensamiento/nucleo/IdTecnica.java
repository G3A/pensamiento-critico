package pensamiento.nucleo;

import java.util.regex.Pattern;

/** Identificador estable de una técnica del catálogo: T01 a T49. */
public record IdTecnica(String valor) implements Comparable<IdTecnica> {

    private static final Pattern FORMA = Pattern.compile("^T(0[1-9]|[1-4][0-9])$");
    public static final int TOTAL = 49;

    public IdTecnica {
        if (valor == null || !FORMA.matcher(valor).matches()) {
            throw new IllegalArgumentException("Identificador de técnica inválido: " + valor + " (se espera T01 a T49)");
        }
    }

    public static IdTecnica de(String valor) {
        return new IdTecnica(valor);
    }

    public int numero() {
        return Integer.parseInt(valor.substring(1));
    }

    @Override
    public int compareTo(IdTecnica otro) {
        return valor.compareTo(otro.valor);
    }

    @Override
    public String toString() {
        return valor;
    }
}
