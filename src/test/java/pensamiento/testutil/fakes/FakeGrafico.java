package pensamiento.testutil.fakes;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import pensamiento.graficos.SaneadorSvg;
import pensamiento.nucleo.puertos.Grafico;

/**
 * Fake del puerto Grafico: no dibuja, pero produce un SVG con un grupo por nodo (con el id y la class del
 * DOT, como hace Graphviz) y la etiqueta como texto, y lo pasa por el mismo saneador que el real, para que el
 * contrato "etiqueta hostil produce SVG inerte" lo cubra. Certificado por FakeGraficoContractTest.
 */
public final class FakeGrafico implements Grafico {

    private static final Pattern NODO = Pattern.compile("(\\w+)\\s*\\[([^\\]]*)\\]");
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
        Matcher m = NODO.matcher(dot);
        int y = 20;
        while (m.find()) {
            String atributos = m.group(2);
            String id = atributo(atributos, "id").orElse(m.group(1));
            String clase = atributo(atributos, "class").orElse("node");
            String etiqueta = atributo(atributos, "label").orElse(m.group(1));
            svg.append("<g id=\"").append(escapar(id)).append("\" class=\"node ").append(escapar(clase)).append("\"><text x=\"10\" y=\"").append(y).append("\">")
               .append(escapar(etiqueta)).append("</text></g>");
            y += 20;
        }
        svg.append("</svg>");
        return SaneadorSvg.sanear(svg.toString());
    }

    private static java.util.Optional<String> atributo(String atributos, String nombre) {
        Matcher m = Pattern.compile("\\b" + nombre + "\\s*=\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(atributos);
        return m.find() ? java.util.Optional.of(m.group(1).replace("\\\"", "\"")) : java.util.Optional.empty();
    }

    private static String escapar(String texto) {
        return texto.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
