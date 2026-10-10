package pensamiento.unidad.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;

import pensamiento.nucleo.CambioOpinion;
import pensamiento.nucleo.puertos.EstadoIa;
import pensamiento.tecnicas.f8.EjecutorCambiosOpinion;
import pensamiento.tecnicas.f8.EjecutorDiarioRazonamiento;
import pensamiento.tecnicas.f8.ResultadoCambiosOpinion;
import pensamiento.web.Pagina;
import pensamiento.web.cambios.ControladorCambios;
import pensamiento.web.patrones.Modo;
import pensamiento.web.patrones.RenderizadoresF8;

/**
 * Pruebas de plantilla de P20 · Registro de cambios de opinión y del diario de razonamiento (RNF-06): la línea de tiempo con
 * el año en barras, cada postura sin revisar con su acceso al modo debate, el registro a mano con cada campo etiquetado y su
 * error con role=alert, y el diario por semanas.
 */
class PlantillasDelRegistroTest {

    private static final TemplateEngine PLANTILLAS = TemplateEngine.createPrecompiled(ContentType.Html);
    private final Pagina pagina = new Pagina("Cambios de opinión", Optional.empty(), new EstadoIa(false, List.of(), "apagado"), "csrf");
    private static final java.time.LocalDate HOY = java.time.LocalDate.parse("2026-10-07");

    private static Document pintar(String plantilla, Map<String, Object> parametros) {
        StringOutput salida = new StringOutput();
        PLANTILLAS.render(plantilla, parametros, salida);
        return Jsoup.parse(salida.toString());
    }

    private ControladorCambios.VistaCambios vista(Map<String, String> errores) {
        var config = new EjecutorCambiosOpinion.Config(true, List.of(CambioOpinion.Causa.values()), 12);
        ResultadoCambiosOpinion r = EjecutorCambiosOpinion.calcular(config,
                List.of(new EjecutorCambiosOpinion.Cambio("2026-10-03", "Conviene abrir en el centro", 80, 45, CambioOpinion.Causa.EVIDENCIA, "T22")),
                List.of(new EjecutorCambiosOpinion.Postura("El pan de masa madre no se vende en este barrio", "2025-08-14")), HOY);
        List<ControladorCambios.SinRevisar> sinRevisar = r.sinRevisar().stream().map(s -> new ControladorCambios.SinRevisar(
                RenderizadoresF8.CambiosOpinion.sinRevisar(s), "/consejero?modo=debate&postura=El+pan+de+masa+madre+no+se+vende+en+este+barrio")).toList();
        return new ControladorCambios.VistaCambios(RenderizadoresF8.CambiosOpinion.vista(Optional.empty(), "registro", r, Modo.COMPLETO, false),
                RenderizadoresF8.CambiosOpinion.tituloSinRevisar(r), sinRevisar, List.of("Conviene abrir en el centro"), config.causas(), true, errores,
                new ControladorCambios.Valores("", "80", "", ""), "clave");
    }

    @Test
    void p20_tiene_la_linea_el_ano_las_posturas_sin_revisar_con_su_debate_y_el_registro_a_mano_etiquetado() {
        Document d = pintar("cambios.jte", Map.of("pagina", pagina, "v", vista(Map.of())));

        assertThat(d.select("[style], script:not([src])")).isEmpty();
        assertThat(d.select(".chip")).allSatisfy(c -> assertThat(c.text()).isNotBlank());
        assertThat(d.select("[data-patron=V11] ol.linea-tiempo li")).hasSize(1);
        assertThat(d.select("[data-patron=V11] [data-barra]")).hasSize(6);
        assertThat(d.select("[data-patron=V11] ul.lista-aparte")).as("las posturas sin revisar van en su propia sección").isEmpty();
        assertThat(d.select("#sin-revisar-titulo").text()).isEqualTo("Posturas sin revisar hace más de 12 meses");
        assertThat(d.select("a[href^=/consejero?modo=debate]").text()).isEqualTo("Ponerla a prueba en el modo debate");
        for (Element campo : d.select("form.registro-cambio input:not([type=hidden]), form.registro-cambio select")) {
            assertThat(d.select("label[for=" + campo.id() + "]")).as("etiqueta para #" + campo.id()).isNotEmpty();
        }
        assertThat(d.select("#nueva-causa option")).hasSize(7);
        assertThat(d.select("#nueva-antes").val()).isEqualTo("80");
        assertThat(d.select("datalist#posturas-conocidas option").attr("value")).isEqualTo("Conviene abrir en el centro");
        assertThat(d.text()).contains("R05 · Confianza y calibración");
    }

    @Test
    void el_error_del_registro_a_mano_llega_con_role_alert_y_queda_descrito() {
        Document d = pintar("cambios.jte", Map.of("pagina", pagina, "v", vista(Map.of("nuevaCausa", "Elige una causa de las que tienes activas."))));

        assertThat(d.select("#error-nueva-causa").attr("role")).isEqualTo("alert");
        assertThat(d.select("#nueva-causa").attr("aria-describedby")).isEqualTo("error-nueva-causa");
    }

    @Test
    void el_diario_se_pinta_por_semanas_o_dice_que_todavia_no_hay_ejecuciones() {
        var config = new EjecutorDiarioRazonamiento.Config(List.of("F5"), true, 4);
        var r = EjecutorDiarioRazonamiento.calcular(config, List.of(new EjecutorDiarioRazonamiento.Registro("2026-10-06", "T29",
                "Pre-mortem de la segunda sucursal", "La segunda sucursal", 0)), HOY);
        Document con = pintar("cambios-diario.jte", Map.of("pagina", pagina, "v", new ControladorCambios.VistaDiario(
                Optional.of(RenderizadoresF8.DiarioRazonamiento.vista(Optional.empty(), "diario", r, Modo.COMPLETO)), 4)));
        Document sin = pintar("cambios-diario.jte", Map.of("pagina", pagina, "v", new ControladorCambios.VistaDiario(Optional.empty(), 4)));

        assertThat(con.select("h4.subtitulo-resultado").text()).isEqualTo("Semana del 5 al 11 de octubre de 2026");
        assertThat(sin.select("section.cambios [role=status]").text()).startsWith("Todavía no hay ejecuciones");
    }
}
