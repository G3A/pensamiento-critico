package pensamiento.testutil.fakes;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import pensamiento.graficos.SaneadorSvg;
import pensamiento.nucleo.puertos.Grafico;

/**
 * Fake del puerto Grafico: no dibuja, pero produce un SVG con un grupo por nodo (con el id y la class del
 * DOT, como hace Graphviz) y la etiqueta como texto, y lo pasa por el mismo saneador que el real, para que el
 * contrato "etiqueta hostil produce SVG inerte" lo cubra. Lee una sentencia por línea, como las escribe el
 * generador del servidor. Certificado por FakeGraficoContractTest.
 */
public final class FakeGrafico implements Grafico {

    private static final String CADENA = "\"(?:[^\"\\\\]|\\\\.)*\"";
    private static final Pattern NODO = Pattern.compile("^\\s*(" + CADENA + "|\\w+)\\s*\\[((?:[^\\]\"]|" + CADENA + ")*)\\]");
    private static final Set<String> PALABRAS_CLAVE = Set.of("graph", "node", "edge", "digraph", "subgraph");
    private final List<String> dotsRecibidos = new ArrayList<>();

    public List<String> dotsRecibidos() {
        return List.copyOf(dotsRecibidos);
    }

    @Override
    public String svg(String dot) {
        if (dot == null || dot.isBlank()) {
            throw new GraficoInvalido("El DOT está vacío");
        }
        String recortado = dot.trim();
        if (!(recortado.startsWith("digraph") || recortado.startsWith("graph")) || !recortado.endsWith("}")) {
            throw new GraficoInvalido("DOT inválido");
        }
        dotsRecibidos.add(dot);
        StringBuilder svg = new StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"200\" height=\"100\" viewBox=\"0 0 200 100\">");
        int y = 20;
        for (String linea : dot.split("\n")) {
            // Una arista ("a" -> "b" [...]) no encaja: después del primer nombre viene la flecha, no el corchete.
            Matcher m = NODO.matcher(linea);
            if (!m.find() || PALABRAS_CLAVE.contains(m.group(1))) {
                continue;
            }
            String nombre = sinComillas(m.group(1));
            String atributos = m.group(2);
            String id = atributo(atributos, "id").orElse(nombre);
            String clase = atributo(atributos, "class").orElse("");
            String etiqueta = atributo(atributos, "label").orElse(nombre);
            svg.append("<g id=\"").append(escapar(id)).append("\" class=\"node ").append(escapar(clase)).append("\"><text x=\"10\" y=\"").append(y)
                    .append("\">").append(escapar(etiqueta)).append("</text></g>");
            y += 20;
        }
        svg.append("</svg>");
        return SaneadorSvg.sanear(svg.toString());
    }

    private static Optional<String> atributo(String atributos, String nombre) {
        Matcher m = Pattern.compile("\\b" + nombre + "\\s*=\\s*(" + CADENA + "|[\\w.-]+)").matcher(atributos);
        return m.find() ? Optional.of(sinComillas(m.group(1))) : Optional.empty();
    }

    /** Quita las comillas y deshace los escapes de DOT: \" y \\ vuelven a ser comilla y barra; \n, salto de línea. */
    private static String sinComillas(String valor) {
        if (!valor.startsWith("\"")) {
            return valor;
        }
        String dentro = valor.substring(1, valor.length() - 1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < dentro.length(); i++) {
            char c = dentro.charAt(i);
            if (c == '\\' && i + 1 < dentro.length()) {
                char siguiente = dentro.charAt(++i);
                sb.append(siguiente == 'n' ? ' ' : siguiente);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String escapar(String texto) {
        return texto.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
