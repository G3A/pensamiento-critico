package pensamiento.aceptacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

/**
 * Aceptación por HTTP del manual por familia (definición de hecho del hito 7: "el manual de cada familia se genera y se
 * descarga sin red"): los ocho capítulos se leen en la app y se descargan como un archivo HTML autocontenido, con el CSS
 * adentro, sin scripts ni recursos externos, con cada técnica de la familia y cada ejemplo pintado con su patrón.
 */
class ManualPorFamiliaIT {

    /** Sección 5b: F1 7, F2 5, F3 6, F4 5, F5 10, F6 6, F7 5, F8 5. */
    private static final Map<String, Integer> TECNICAS = Map.of("F1", 7, "F2", 5, "F3", 6, "F4", 5, "F5", 10, "F6", 6, "F7", 5, "F8", 5);

    @Test
    void los_ocho_capitulos_se_descargan_autocontenidos_con_cada_tecnica_y_sus_ejemplos() {
        ClienteApp admin = Instalacion.administrador();
        String lectora = Instalacion.personaNueva(admin, "mamá", "1234");
        ClienteApp persona = Instalacion.entraComo(lectora, "1234");

        Document indice = Jsoup.parse(persona.get("/manual").cuerpo());
        assertThat(indice.select("li[data-familia]")).hasSize(8);
        assertThat(indice.select("a[href=/manual/F3/descargar]").attr("download")).isEqualTo("manual-F3.html");

        int ejemplos = 0;
        for (Map.Entry<String, Integer> f : TECNICAS.entrySet()) {
            ClienteApp.Respuesta r = persona.get("/manual/" + f.getKey() + "/descargar");
            assertThat(r.estado()).as(f.getKey()).isEqualTo(200);
            assertThat(r.cabecera("Content-Disposition").orElse("")).contains("attachment", "manual-" + f.getKey() + ".html");
            String html = r.cuerpo();
            assertThat(html).startsWith("<!doctype html>").contains("<style>", "--fondo");
            Document d = Jsoup.parse(html);
            assertThat(d.select("script")).as("sin scripts").isEmpty();
            assertThat(d.select("link[rel=stylesheet], img[src], [src^=http], [href^=http]")).as("sin recursos externos").isEmpty();
            assertThat(d.select("section.manual-tecnica")).as("técnicas de " + f.getKey()).hasSize(f.getValue());
            assertThat(d.select("article.manual-ejemplo")).as("cada ejemplo con su resultado pintado")
                    .allSatisfy(e -> assertThat(e.select("[data-patron]")).isNotEmpty());
            assertThat(d.select("section.manual-tecnica")).allSatisfy(s -> assertThat(s.select("article.manual-ejemplo").size()).isGreaterThanOrEqualTo(3));
            ejemplos += d.select("article.manual-ejemplo").size();
        }
        // 49 técnicas por 3 ejemplos, más el caso adicional de T28.
        assertThat(ejemplos).isEqualTo(148);

        Document capitulo = Jsoup.parse(persona.get("/manual/F8").cuerpo());
        assertThat(capitulo.select("#capitulo-titulo").text()).isEqualTo("F8 · Metacognición y hábito");
        assertThat(capitulo.select("section#tecnica-T49 h2").text()).isEqualTo("T49 · Repetición espaciada");
        assertThat(persona.get("/manual/F9").estado()).isEqualTo(404);
    }
}
