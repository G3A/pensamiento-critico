package pensamiento.unidad.web.aula;

import static org.assertj.core.api.Assertions.assertThat;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.junit.jupiter.api.Test;

import pensamiento.web.aula.QrSvg;

/** El QR de la clase (hito 10, entregable 4): un SVG sin script, con margen de 4 módulos, que solo depende del texto. */
class QrSvgTest {

    private static final String DIRECCION = "http://localhost:8080/prueba?clase=7KQ2M9";

    @Test
    void es_un_svg_sin_script_con_su_titulo_y_el_lado_de_la_version_mas_el_margen() {
        String svg = QrSvg.de(DIRECCION, "QR de la clase de 10.º A");
        Element raiz = Jsoup.parse(svg, "", Parser.xmlParser()).selectFirst("svg");

        assertThat(raiz).isNotNull();
        assertThat(raiz.attr("aria-label")).isEqualTo("QR de la clase de 10.º A");
        assertThat(svg).doesNotContain("<script", "href", "style=");
        String[] caja = raiz.attr("viewBox").split(" ");
        int lado = Integer.parseInt(caja[2]);
        assertThat((lado - 8 - 17) % 4).as("lado de una versión de QR (17 + 4v) más 8 de margen").isZero();
        assertThat(lado).isGreaterThanOrEqualTo(29);
    }

    @Test
    void el_mismo_texto_da_el_mismo_qr_y_otro_texto_otro() {
        assertThat(QrSvg.de(DIRECCION, "a")).isEqualTo(QrSvg.de(DIRECCION, "a"));
        assertThat(QrSvg.de(DIRECCION, "a")).isNotEqualTo(QrSvg.de(DIRECCION.replace("7KQ2M9", "8ZZ2M9"), "a"));
    }

    @Test
    void el_titulo_se_escapa() {
        assertThat(QrSvg.de(DIRECCION, "<b>\"10.º\" & A</b>")).contains("aria-label=\"&lt;b&gt;&quot;10.º&quot; &amp; A&lt;/b&gt;\"");
    }
}
