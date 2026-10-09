package pensamiento.biblioteca;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import pensamiento.nucleo.Documento;

/**
 * El tipo de un archivo por su contenido, no por su extensión (RNF-08): %PDF- al principio es PDF; si no, tiene que ser texto
 * UTF-8 válido sin bytes nulos ni otros controles; es CSV si sus primeras líneas tienen el mismo número de comas o de puntos
 * y comas (al menos uno), Markdown si alguna línea tiene forma de título, lista o enlace, y texto si no. Reglas en
 * docs/verificacion.md.
 */
public final class DetectorTipo {

    private static final Pattern MARKDOWN = Pattern.compile("(?m)^(#{1,6}\\s|[-*]\\s|\\d+\\.\\s|```)|\\[[^\\]\\n]+\\]\\([^)\\n]+\\)");
    private static final int LINEAS_PARA_CSV = 6;

    private DetectorTipo() {
    }

    /** El tipo, o vacío si no es ninguno de los permitidos. */
    public static Optional<Documento.Tipo> detectar(byte[] contenido) {
        if (contenido.length >= 5 && new String(contenido, 0, 5, StandardCharsets.ISO_8859_1).equals("%PDF-")) {
            return Optional.of(Documento.Tipo.PDF);
        }
        Optional<String> texto = texto(contenido);
        if (texto.isEmpty() || texto.get().isBlank()) {
            return Optional.empty();
        }
        if (separador(texto.get()).isPresent()) {
            return Optional.of(Documento.Tipo.CSV);
        }
        if (MARKDOWN.matcher(texto.get()).find()) {
            return Optional.of(Documento.Tipo.MARKDOWN);
        }
        return Optional.of(Documento.Tipo.TEXTO);
    }

    /** El contenido como texto UTF-8 (sin la marca de orden de bytes), o vacío si no es texto. */
    public static Optional<String> texto(byte[] contenido) {
        String texto;
        try {
            texto = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(contenido)).toString();
        } catch (CharacterCodingException e) {
            return Optional.empty();
        }
        if (texto.startsWith("﻿")) {
            texto = texto.substring(1);
        }
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (c < 32 && c != '\n' && c != '\r' && c != '\t' && c != '\f') {
                return Optional.empty();
            }
        }
        return Optional.of(texto);
    }

    /**
     * La coma o el punto y coma si las primeras líneas (al menos dos) tienen el mismo número, fuera de comillas. Para no
     * confundir prosa con comas: ninguna de esas líneas termina en punto y los nombres del encabezado son cortos.
     */
    public static Optional<Character> separador(String texto) {
        List<String> lineas = texto.lines().filter(l -> !l.isBlank()).limit(LINEAS_PARA_CSV).toList();
        if (lineas.size() < 2 || lineas.stream().anyMatch(l -> l.strip().endsWith("."))) {
            return Optional.empty();
        }
        for (char sep : new char[] {';', ','}) {
            int primera = contar(lineas.getFirst(), sep);
            boolean encabezadoCorto = java.util.Arrays.stream(lineas.getFirst().split(Pattern.quote(String.valueOf(sep)), -1))
                    .allMatch(c -> !c.isBlank() && c.strip().length() <= 60);
            if (primera > 0 && encabezadoCorto && lineas.stream().allMatch(l -> contar(l, sep) == primera)) {
                return Optional.of(sep);
            }
        }
        return Optional.empty();
    }

    private static int contar(String linea, char sep) {
        int n = 0;
        boolean comillas = false;
        for (int i = 0; i < linea.length(); i++) {
            char c = linea.charAt(i);
            if (c == '"') {
                comillas = !comillas;
            } else if (c == sep && !comillas) {
                n++;
            }
        }
        return n;
    }
}
