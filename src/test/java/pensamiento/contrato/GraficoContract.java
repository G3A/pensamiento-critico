package pensamiento.contrato;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import pensamiento.nucleo.puertos.Grafico;

/**
 * Contrato del puerto Grafico: SVG válido con identificador y clase por nodo, DOT inválido mapeado a error
 * de dominio, y la dimensión de seguridad "etiqueta con script y href produce SVG inerte" (RNF-05).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class GraficoContract {

    protected abstract Grafico crearSut();

    private static final String DOT_SIMPLE = """
            digraph G {
              conclusion [id="afirmacion-1", class="conclusion", label="Abrir la segunda sucursal"];
              premisa [id="afirmacion-2", class="premisa", label="El centro tiene más tráfico"];
              premisa -> conclusion;
            }
            """;

    @Test
    void un_dot_valido_produce_un_svg_con_el_identificador_y_la_clase_de_cada_nodo() {
        Document svg = parsear(crearSut().svg(DOT_SIMPLE));
        assertThat(svg.selectFirst("svg")).isNotNull();
        Element conclusion = svg.getElementById("afirmacion-1");
        Element premisa = svg.getElementById("afirmacion-2");
        assertThat(conclusion).isNotNull();
        assertThat(premisa).isNotNull();
        assertThat(conclusion.classNames()).contains("conclusion");
        assertThat(premisa.classNames()).contains("premisa");
        assertThat(conclusion.text()).contains("Abrir la segunda sucursal");
        assertThat(premisa.text()).contains("El centro tiene más tráfico");
    }

    @Test
    void un_dot_invalido_lanza_grafico_invalido() {
        assertThatThrownBy(() -> crearSut().svg("esto no es dot ->> {"))
                .isInstanceOf(Grafico.GraficoInvalido.class);
    }

    @Test
    void un_dot_vacio_lanza_grafico_invalido() {
        assertThatThrownBy(() -> crearSut().svg("   "))
                .isInstanceOf(Grafico.GraficoInvalido.class);
    }

    @Test
    void una_etiqueta_con_script_y_href_produce_un_svg_inerte() {
        String hostil = """
                digraph G {
                  n1 [id="afirmacion-9", class="premisa", label="<script>alert(1)</script> <a href=\\"javascript:alert(2)\\">x</a>", URL="javascript:alert(3)", tooltip="\\" onload=\\"alert(4)"];
                }
                """;
        Document svg = parsear(crearSut().svg(hostil));
        assertThat(svg.selectFirst("svg")).isNotNull();
        assertThat(svg.select("script, foreignObject, a, iframe, style")).as("sin elementos ejecutables ni enlaces").isEmpty();
        for (Element e : svg.getAllElements()) {
            for (Attribute a : e.attributes()) {
                String nombre = a.getKey().toLowerCase();
                assertThat(nombre).as("atributo en <" + e.tagName() + ">").doesNotStartWith("on").isNotIn("href", "xlink:href", "style");
                assertThat(a.getValue().toLowerCase()).as("valor de " + nombre).doesNotContain("javascript:", "url(");
            }
        }
        // La etiqueta sigue como texto (escapado), no como marcado.
        assertThat(svg.getElementById("afirmacion-9")).isNotNull();
    }

    private static Document parsear(String svg) {
        assertThat(svg).startsWith("<svg");
        return Jsoup.parse(svg, "", Parser.xmlParser());
    }
}
