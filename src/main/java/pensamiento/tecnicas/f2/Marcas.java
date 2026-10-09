package pensamiento.tecnicas.f2;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import pensamiento.tecnicas.comun.Textos;

/**
 * Las marcas que buscan T08 · Preguntas socráticas, T10 · Escalera de inferencia, T12 · Definición de términos y
 * T35 · Seis Sombreros: palabras o frases enteras, sin mayúsculas ni tildes, de las listas del banco
 * (catalogo/preguntas-socraticas.json), más la cifra y la frase causal. Devuelven el trozo del texto original.
 */
public final class Marcas {

    private static final Pattern CIFRA = Pattern.compile("\\d+(?:[.,]\\d+)?%?");
    private static final int PALABRAS_CAUSA = 12;

    private Marcas() {
    }

    /** La primera de las frases de la lista (en su orden) que aparece en el texto, como está escrita en el texto. */
    public static Optional<String> primera(String texto, List<String> frases) {
        return Textos.primeraFrase(texto, frases);
    }

    /** El primer número del texto con su "%" pegado, si lo lleva. */
    public static Optional<String> cifra(String texto) {
        if (Textos.vacio(texto)) {
            return Optional.empty();
        }
        Matcher m = CIFRA.matcher(texto);
        return m.find() ? Optional.of(m.group()) : Optional.empty();
    }

    /**
     * La frase causal: desde el primer conector de la lista que aparece en el texto hasta el final de su oración, sin
     * punto final, con 12 palabras como máximo.
     */
    public static Optional<String> causa(String texto, List<String> conectores) {
        if (Textos.vacio(texto)) {
            return Optional.empty();
        }
        String plegado = Textos.plegar(texto);
        int mejor = -1;
        for (String c : conectores) {
            Matcher m = Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(c) + "(?![\\p{L}\\p{N}])").matcher(plegado);
            if (m.find()) {
                mejor = m.start();
                break;
            }
        }
        if (mejor < 0) {
            return Optional.empty();
        }
        int fin = texto.length();
        for (int i = mejor; i < texto.length(); i++) {
            char ch = texto.charAt(i);
            if (ch == '.' || ch == '!' || ch == '?' || ch == '\n') {
                fin = i;
                break;
            }
        }
        String frase = texto.substring(mejor, fin).strip();
        String[] palabras = frase.split("\\s+");
        if (palabras.length > PALABRAS_CAUSA) {
            frase = String.join(" ", Arrays.copyOf(palabras, PALABRAS_CAUSA));
        }
        return Optional.of(frase);
    }

    /** Todas las frases de la lista que aparecen en el texto, cada una una vez, en el orden de la lista. */
    public static List<String> todas(String texto, List<String> frases) {
        return frases.stream().map(f -> Textos.primeraFrase(texto, List.of(f))).flatMap(Optional::stream).toList();
    }
}
