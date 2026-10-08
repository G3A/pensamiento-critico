package pensamiento.tecnicas.comun;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Frases cortas en español para resúmenes y motivos (enumeraciones, conteos con plural) y las utilidades de
 * texto que comparten las técnicas: contar palabras, plegar mayúsculas y tildes, partir en oraciones.
 */
public final class Textos {

    /** Una oración del texto con su posición, en caracteres del texto original. */
    public record Oracion(int inicio, int fin, String texto) {
    }

    private Textos() {
    }

    /** «A», «A» y «B», «A», «B» y «C». */
    public static String enumerarCitas(List<String> textos) {
        return enumerar(textos.stream().map(t -> "«" + t + "»").toList());
    }

    /** A, A y B, A, B y C. Vacío si no hay ninguno. */
    public static String enumerar(List<String> textos) {
        if (textos.isEmpty()) {
            return "";
        }
        if (textos.size() == 1) {
            return textos.getFirst();
        }
        return String.join(", ", textos.subList(0, textos.size() - 1)) + " y " + textos.getLast();
    }

    /** "1 apoyo", "2 apoyos". */
    public static String contar(int n, String singular, String plural) {
        return n + " " + (n == 1 ? singular : plural);
    }

    private static final String[] MESES = {"enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre",
        "noviembre", "diciembre"};

    /** "15 de diciembre de 2026". */
    public static String fecha(java.time.LocalDate fecha) {
        return fecha.getDayOfMonth() + " de " + MESES[fecha.getMonthValue() - 1] + " de " + fecha.getYear();
    }

    /** 7.2 → "7,2"; con signo: "+7,2", "−0,5" y "0,0" para el cero. */
    public static String decimal(java.math.BigDecimal valor, boolean conSigno) {
        String texto = valor.abs().toPlainString().replace('.', ',');
        if (valor.signum() < 0) {
            return "−" + texto;
        }
        return conSigno && valor.signum() > 0 ? "+" + texto : texto;
    }

    /** "Máquina, Método,  Material" pasa a [Máquina, Método, Material]: sin vacíos ni repetidos, en su orden. */
    public static List<String> partesPorComa(String texto) {
        return java.util.Arrays.stream(texto.split(",")).map(String::strip).filter(s -> !s.isEmpty()).distinct().toList();
    }

    /** Vacío o solo espacios. */
    public static boolean vacio(String texto) {
        return texto == null || texto.isBlank();
    }

    /** Trozos separados por espacios: "km/h" y "40" cuentan como una palabra cada uno. */
    public static int palabras(String texto) {
        if (vacio(texto)) {
            return 0;
        }
        return texto.trim().split("\\s+").length;
    }

    /** La primera letra en mayúscula. */
    public static String mayusculaInicial(String texto) {
        if (vacio(texto)) {
            return texto;
        }
        return texto.substring(0, 1).toUpperCase(Locale.ROOT) + texto.substring(1);
    }

    /** "Todo local vende más." pasa a "todo local vende más": sin punto final y con la primera letra en minúscula. */
    public static String comoClausula(String texto) {
        String t = texto.trim();
        while (t.endsWith(".")) {
            t = t.substring(0, t.length() - 1).trim();
        }
        return t.isEmpty() ? t : t.substring(0, 1).toLowerCase(Locale.ROOT) + t.substring(1);
    }

    /** Minúsculas y sin tildes, carácter por carácter, para que las posiciones coincidan con el original. */
    public static String plegar(String texto) {
        StringBuilder sb = new StringBuilder(texto.length());
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (c < 128) {
                sb.append(Character.toLowerCase(c));
            } else {
                String base = Normalizer.normalize(String.valueOf(c), Normalizer.Form.NFD);
                sb.append(base.toLowerCase(Locale.ROOT).charAt(0));
            }
        }
        return sb.toString();
    }

    /** Parte en oraciones por punto, signo de cierre o salto de línea; sin espacios en los bordes. */
    public static List<Oracion> oraciones(String texto) {
        List<Oracion> oraciones = new ArrayList<>();
        int inicio = 0;
        for (int i = 0; i <= texto.length(); i++) {
            boolean fin = i == texto.length();
            boolean corte = fin || texto.charAt(i) == '\n'
                    || (".!?".indexOf(texto.charAt(i)) >= 0 && (i + 1 == texto.length() || Character.isWhitespace(texto.charAt(i + 1))));
            if (!corte) {
                continue;
            }
            int finOracion = fin || texto.charAt(i) == '\n' ? i : i + 1;
            agregar(oraciones, texto, inicio, finOracion);
            inicio = finOracion;
        }
        return oraciones;
    }

    private static void agregar(List<Oracion> oraciones, String texto, int inicio, int fin) {
        while (inicio < fin && Character.isWhitespace(texto.charAt(inicio))) {
            inicio++;
        }
        while (fin > inicio && Character.isWhitespace(texto.charAt(fin - 1))) {
            fin--;
        }
        if (fin > inicio) {
            oraciones.add(new Oracion(inicio, fin, texto.substring(inicio, fin)));
        }
    }

    /**
     * La primera de las frases (escritas en minúscula y sin tildes) que aparece en el texto como palabras enteras,
     * en el orden de la lista; devuelve el trozo del texto original, con sus tildes y mayúsculas.
     */
    public static java.util.Optional<String> primeraFrase(String texto, List<String> frases) {
        if (vacio(texto)) {
            return java.util.Optional.empty();
        }
        String plegado = plegar(texto);
        for (String frase : frases) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?<![\\p{L}\\p{N}])" + java.util.regex.Pattern.quote(frase) + "(?![\\p{L}\\p{N}])")
                    .matcher(plegado);
            if (m.find()) {
                return java.util.Optional.of(texto.substring(m.start(), m.end()));
            }
        }
        return java.util.Optional.empty();
    }
}
