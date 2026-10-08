package pensamiento.unidad.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;

import pensamiento.nucleo.Ejecucion;
import pensamiento.nucleo.EstandarPrueba;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Json;
import pensamiento.web.taller.VistaGuardado;
import pensamiento.web.taller.VistaTaller;

/** Pruebas de plantilla del Taller de argumentos (P14, RNF-06): identificadores, hx-* y ARIA con jsoup. */
class PlantillasDelTallerTest {

    private static final TemplateEngine PLANTILLAS = TemplateEngine.createPrecompiled(ContentType.Html);

    private static Document pintar(String plantilla, Map<String, Object> parametros) {
        StringOutput salida = new StringOutput();
        PLANTILLAS.render(plantilla, parametros, salida);
        return Jsoup.parseBodyFragment(salida.toString());
    }

    private static VistaTaller vista(String texto, Map<String, String> errores) {
        return new VistaTaller(texto, EstandarPrueba.PREPONDERANCIA, null, null, "probablemente", "", "6f1d2a4e-0000-4000-8000-000000000001",
                errores, Optional.empty(), Optional.empty(), Optional.empty(), List.of(), List.of(), "");
    }

    @Test
    void el_formulario_evalua_con_hx_post_sobre_si_mismo_y_guarda_en_su_propia_zona() {
        Document d = pintar("fragmentos/taller/formulario.jte", Map.of("t", vista("[A]: Conviene abrir los domingos.", Map.of())));

        Element form = d.getElementById("form-taller");
        assertThat(form.attr("hx-post")).isEqualTo("/taller/evaluar");
        assertThat(form.attr("hx-target")).isEqualTo("this");
        assertThat(form.attr("hx-swap")).isEqualTo("outerHTML");
        assertThat(d.getElementById("clave-taller").val()).isEqualTo("6f1d2a4e-0000-4000-8000-000000000001");
        Element guardar = d.select("button[hx-post=/taller/guardar]").first();
        assertThat(guardar.attr("hx-target")).isEqualTo("#taller-guardado");
        assertThat(d.select("button[name=_linea]").eachAttr("value")).containsExactly("conclusion", "premisa", "objecion", "oculta");
        assertThat(d.select("button[name=_linea]").eachAttr("hx-post")).containsOnly("/taller/linea");
        assertThat(d.getElementById("taller-argdown").attr("aria-describedby")).isEqualTo("taller-argdown-ayuda");
        assertThat(d.getElementById("taller-argdown").hasAttr("aria-invalid")).isFalse();
        assertThat(d.getElementById("taller-calificador").val()).isEqualTo("probablemente");
        for (Element campo : d.select("input:not([type=hidden]), select, textarea")) {
            assertThat(d.select("label[for=" + campo.id() + "]")).as("etiqueta de " + campo.id()).hasSize(1);
        }
        assertThat(d.select("[style], [fill]")).isEmpty();
        assertThat(d.getElementById("taller-resultados").attr("aria-live")).isEqualTo("polite");
    }

    @Test
    void con_error_el_editor_se_marca_invalido_y_apunta_al_mensaje_con_linea_y_columna() {
        Document d = pintar("fragmentos/taller/formulario.jte",
                Map.of("t", vista("Conclusión.\n  * viñeta", Map.of("argdown", "Línea 2, columna 3: Se esperaba + (apoyo) o - (ataque)."))));

        Element editor = d.getElementById("taller-argdown");
        assertThat(editor.attr("aria-invalid")).isEqualTo("true");
        assertThat(editor.attr("aria-describedby")).isEqualTo("taller-argdown-ayuda taller-argdown-error");
        assertThat(d.getElementById("taller-argdown-error").attr("role")).isEqualTo("alert");
        assertThat(d.getElementById("taller-argdown-error").text()).contains("Línea 2, columna 3");
    }

    @Test
    void lo_guardado_lista_cada_ejecucion_y_asocia_todas_juntas_con_una_clave_nueva_fuera_de_banda() {
        UUID t01 = UUID.randomUUID();
        UUID t13 = UUID.randomUUID();
        List<Ejecucion> ejecuciones = List.of(ejecucion(t01, "T01"), ejecucion(t13, "T13"));
        Document d = pintar("fragmentos/taller/guardado.jte", Map.of("g", new VistaGuardado(ejecuciones,
                List.of("T01 · Mapeo de argumentos", "T13 · Falacias como esquemas fallidos"), List.of(), "", "clave-nueva")));

        assertThat(d.select("a[data-ejecucion]").eachAttr("href")).containsExactly("/ejecuciones/" + t01, "/ejecuciones/" + t13);
        Element asociar = d.getElementById("taller-asociar");
        assertThat(asociar.attr("hx-post")).isEqualTo("/taller/expediente");
        assertThat(asociar.attr("hx-target")).isEqualTo("#taller-guardado");
        assertThat(asociar.select("input[name=ejecucion]").eachAttr("value")).containsExactly(t01.toString(), t13.toString());
        assertThat(d.getElementById("taller-asociar-nuevo").attr("aria-describedby")).isEqualTo("taller-asociar-estado");
        assertThat(d.getElementById("clave-taller").attr("hx-swap-oob")).isEqualTo("true");
    }

    private static Ejecucion ejecucion(UUID id, String tecnica) {
        return new Ejecucion(id, UUID.randomUUID(), UUID.randomUUID(), IdTecnica.de(tecnica), 1, Optional.empty(), Json.VACIO, Json.VACIO,
                Json.VACIO, "Resumen", Optional.empty(), "clave", Instant.parse("2026-10-07T15:00:00Z"));
    }
}
