package pensamiento.biblioteca;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import pensamiento.nucleo.Fragmento;

/**
 * Parte un documento en fragmentos citables (docs/verificacion.md, reglas de la biblioteca): por página en un PDF y, dentro
 * de cada página, por párrafos juntados hasta unos 800 caracteres; un párrafo de más de 1.600 se parte por oraciones (y una
 * oración sin puntos, por palabras). Las líneas de un párrafo se unen con un espacio. Un CSV da un fragmento por fila con
 * su encabezado: "columna: valor · columna: valor". El troceado nunca pierde ni repite palabras.
 */
public final class Troceador {

    public static final int OBJETIVO = 800;
    public static final int MAXIMO = 1600;

    private static final Pattern PARRAFOS = Pattern.compile("\\n[ \\t\\x0B\\f\\r]*\\n");
    private static final Pattern ORACIONES = Pattern.compile("(?<=[.!?…»])\\s+");
    private static final Pattern ESPACIOS = Pattern.compile("\\s+");

    /** Una página del documento: su número en un PDF, vacío en los demás tipos. */
    public record Pagina(Optional<Integer> numero, String texto) {
    }

    private Troceador() {
    }

    public static List<Fragmento.Nuevo> trocear(List<Pagina> paginas) {
        List<Fragmento.Nuevo> fragmentos = new ArrayList<>();
        for (Pagina p : paginas) {
            StringBuilder actual = new StringBuilder();
            for (String parrafo : parrafos(p.texto())) {
                for (String trozo : partir(parrafo)) {
                    if (!actual.isEmpty() && actual.length() + 2 + trozo.length() > OBJETIVO) {
                        fragmentos.add(new Fragmento.Nuevo(fragmentos.size(), actual.toString(), p.numero()));
                        actual.setLength(0);
                    }
                    if (!actual.isEmpty()) {
                        actual.append("\n\n");
                    }
                    actual.append(trozo);
                }
            }
            if (!actual.isEmpty()) {
                fragmentos.add(new Fragmento.Nuevo(fragmentos.size(), actual.toString(), p.numero()));
            }
        }
        return fragmentos;
    }

    /** Un fragmento por fila con datos, con el encabezado: "columna: valor · columna: valor". */
    public static List<Fragmento.Nuevo> trocearCsv(String texto, char separador) {
        List<String> lineas = texto.lines().filter(l -> !l.isBlank()).toList();
        List<Fragmento.Nuevo> fragmentos = new ArrayList<>();
        if (lineas.isEmpty()) {
            return fragmentos;
        }
        List<String> encabezado = campos(lineas.getFirst(), separador);
        for (String linea : lineas.subList(1, lineas.size())) {
            List<String> valores = campos(linea, separador);
            List<String> partes = new ArrayList<>();
            for (int i = 0; i < Math.max(encabezado.size(), valores.size()); i++) {
                String columna = i < encabezado.size() ? encabezado.get(i) : "columna " + (i + 1);
                partes.add(columna + ": " + (i < valores.size() ? valores.get(i) : ""));
            }
            fragmentos.add(new Fragmento.Nuevo(fragmentos.size(), String.join(" · ", partes).strip(), Optional.empty()));
        }
        return fragmentos;
    }

    /** Los párrafos de una página, cada uno con sus líneas unidas por un espacio. */
    static List<String> parrafos(String texto) {
        List<String> parrafos = new ArrayList<>();
        for (String bloque : PARRAFOS.split(texto.replace("\r\n", "\n").replace('\r', '\n'))) {
            String unido = ESPACIOS.matcher(bloque).replaceAll(" ").strip();
            if (!unido.isEmpty()) {
                parrafos.add(unido);
            }
        }
        return parrafos;
    }

    /** Un párrafo de hasta 1.600 caracteres queda entero; si no, oraciones juntadas hasta 800 (o palabras, si no hay puntos). */
    static List<String> partir(String parrafo) {
        if (parrafo.length() <= MAXIMO) {
            return List.of(parrafo);
        }
        List<String> piezas = new ArrayList<>();
        for (String oracion : ORACIONES.split(parrafo)) {
            if (oracion.length() <= MAXIMO) {
                piezas.add(oracion);
            } else {
                piezas.addAll(List.of(oracion.split(" ")));
            }
        }
        List<String> trozos = new ArrayList<>();
        StringBuilder actual = new StringBuilder();
        for (String pieza : piezas) {
            if (!actual.isEmpty() && actual.length() + 1 + pieza.length() > OBJETIVO) {
                trozos.add(actual.toString());
                actual.setLength(0);
            }
            if (!actual.isEmpty()) {
                actual.append(' ');
            }
            actual.append(pieza);
        }
        if (!actual.isEmpty()) {
            trozos.add(actual.toString());
        }
        return trozos;
    }

    /** Los campos de una línea de CSV, con comillas dobles ("" es una comilla dentro del campo). */
    static List<String> campos(String linea, char separador) {
        List<String> campos = new ArrayList<>();
        StringBuilder actual = new StringBuilder();
        boolean comillas = false;
        for (int i = 0; i < linea.length(); i++) {
            char c = linea.charAt(i);
            if (c == '"') {
                if (comillas && i + 1 < linea.length() && linea.charAt(i + 1) == '"') {
                    actual.append('"');
                    i++;
                } else {
                    comillas = !comillas;
                }
            } else if (c == separador && !comillas) {
                campos.add(actual.toString().strip());
                actual.setLength(0);
            } else {
                actual.append(c);
            }
        }
        campos.add(actual.toString().strip());
        return campos;
    }
}
