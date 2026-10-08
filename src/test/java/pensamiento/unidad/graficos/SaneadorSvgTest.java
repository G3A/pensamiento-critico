package pensamiento.unidad.graficos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import pensamiento.graficos.SaneadorSvg;

/** El saneador por lista blanca deja el dibujo y quita todo lo ejecutable. */
class SaneadorSvgTest {

    @Test
    void conserva_los_elementos_y_atributos_del_dibujo() {
        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"10\" height=\"10\" viewBox=\"0 0 10 10\">"
                + "<g id=\"n1\" class=\"node premisa\"><ellipse cx=\"5\" cy=\"5\" rx=\"4\" ry=\"2\" fill=\"none\" stroke=\"black\"/>"
                + "<text x=\"1\" y=\"1\" font-size=\"12\">El centro tiene más tráfico</text></g></svg>";
        String limpio = SaneadorSvg.sanear(svg);
        assertThat(limpio).contains("id=\"n1\"", "class=\"node premisa\"", "<ellipse", "El centro tiene más tráfico");
    }

    @Test
    void quita_los_colores_porque_salen_de_clases_css() {
        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><g class=\"node oculta\"><polygon points=\"0,0 1,1\" fill=\"white\" stroke=\"black\" "
                + "fill-opacity=\"1\" stroke-opacity=\"1\" opacity=\"1\" stroke-width=\"2\"/></g></svg>";
        String limpio = SaneadorSvg.sanear(svg);
        assertThat(limpio).doesNotContain("fill", "stroke=", "opacity").contains("stroke-width=\"2\"", "class=\"node oculta\"");
    }

    @Test
    void quita_script_manejadores_de_eventos_enlaces_y_foreignobject() {
        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script>"
                + "<a href=\"javascript:alert(1)\"><text onclick=\"alert(1)\" x=\"1\" y=\"1\">hola</text></a>"
                + "<foreignObject><div>x</div></foreignObject>"
                + "<rect x=\"1\" y=\"1\" width=\"2\" height=\"2\" style=\"fill:url(http://malo)\" fill=\"red\"/></svg>";
        String limpio = SaneadorSvg.sanear(svg);
        assertThat(limpio).doesNotContain("<script", "onclick", "javascript:", "href", "foreignObject", "style=", "url(");
        assertThat(limpio).contains("hola", "<rect");
    }

    @Test
    void un_documento_que_no_es_svg_se_rechaza() {
        assertThatThrownBy(() -> SaneadorSvg.sanear("<html><body>no</body></html>"))
                .isInstanceOf(SaneadorSvg.SvgInvalido.class);
        assertThatThrownBy(() -> SaneadorSvg.sanear("esto no es xml <"))
                .isInstanceOf(SaneadorSvg.SvgInvalido.class);
    }

    @Test
    void no_resuelve_entidades_externas_ni_doctype() {
        String svg = "<?xml version=\"1.0\"?><!DOCTYPE svg [<!ENTITY x SYSTEM \"file:///etc/passwd\">]>"
                + "<svg xmlns=\"http://www.w3.org/2000/svg\"><text x=\"1\" y=\"1\">&x;</text></svg>";
        // Sin DOCTYPE la entidad no existe: o se rechaza o se sanea sin resolverla; nunca se lee el archivo.
        try {
            String limpio = SaneadorSvg.sanear(svg);
            assertThat(limpio).doesNotContain("root:");
        } catch (SaneadorSvg.SvgInvalido esperado) {
            assertThat(esperado).hasMessageContaining("SVG");
        }
    }
}
