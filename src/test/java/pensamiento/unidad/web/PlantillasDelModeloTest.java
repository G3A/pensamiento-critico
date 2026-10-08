package pensamiento.unidad.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
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

import pensamiento.catalogo.CatalogoJson;
import pensamiento.catalogo.MapeadorJson;
import pensamiento.nucleo.Ejemplo;
import pensamiento.nucleo.Propuesta;
import pensamiento.nucleo.Tecnica;
import pensamiento.tecnicas.f6.EjecutorSteelman;
import pensamiento.testutil.builders.Contextos;
import pensamiento.web.formulario.LenguajeCampos;
import pensamiento.web.patrones.Modo;
import pensamiento.web.patrones.RenderizadorSteelman;
import pensamiento.web.tecnicas.VistaFormulario;

/**
 * Pruebas de plantilla del modelo local (RNF-06, corrección 11): las propuestas en el formulario, la burbuja de espera
 * con SSE y el patrón V04, revisados con jsoup. Sin servidor: las plantillas precompiladas.
 */
class PlantillasDelModeloTest {

    private static final TemplateEngine PLANTILLAS = TemplateEngine.createPrecompiled(ContentType.Html);
    private static final CatalogoJson CATALOGO = new CatalogoJson();
    private static final Tecnica T34 = CATALOGO.tecnicas().stream().filter(t -> t.id().equals(EjecutorSteelman.ID)).findFirst().orElseThrow();
    private static final String PROPUESTA = "Las cámaras cuestan, vigilan a los vecinos honestos todo el día, y no hay evidencia local "
            + "de que bajen los robos en vez de moverlos.";

    private static Document pintar(String plantilla, Map<String, Object> parametros) {
        StringOutput salida = new StringOutput();
        PLANTILLAS.render(plantilla, parametros, salida);
        return Jsoup.parseBodyFragment(salida.toString());
    }

    private static VistaFormulario formulario(boolean adoptada, VistaFormulario.Modelo modelo) {
        Ejemplo camaras = CATALOGO.ejemplosDe(EjecutorSteelman.ID).getFirst();
        Map<String, Object> datos = new LinkedHashMap<>(LenguajeCampos.mapa(camaras.datos()));
        List<Object> propuestas = new ArrayList<>();
        propuestas.add(MapeadorJson.mapper().convertValue(new Propuesta("IA1", "steelman", "Steelman propuesto", PROPUESTA, "", adoptada,
                "qwen3:4b", "sha256:fake", "t34-steelman.v1"), Map.class));
        datos.put("propuestas", propuestas);
        return VistaFormulario.de(T34, LenguajeCampos.campos(T34.esquemaConfig()), LenguajeCampos.campos(T34.esquemaEntrada()),
                LenguajeCampos.mapa(camaras.config()), datos, Map.of(), "clave-1", "tu configuración").conModelo(modelo);
    }

    @Test
    void una_propuesta_sin_adoptar_viaja_oculta_dice_que_no_cuenta_y_se_adopta_con_un_boton() {
        Document d = pintar("fragmentos/ficha/formulario.jte", Map.of("f", formulario(false, new VistaFormulario.Modelo(true, true, false)), "oob", false));

        Element propuesta = d.selectFirst("li.propuesta[data-propuesta=IA1]");
        assertThat(propuesta.select(".chip").text()).isEqualTo("propuesta del modelo · sin adoptar · no cuenta");
        assertThat(propuesta.select("input[type=hidden]").eachAttr("name")).contains("propuestas[0].codigo", "propuestas[0].valor",
                "propuestas[0].modelo", "propuestas[0].digest", "propuestas[0].prompt").doesNotContain("propuestas[0].adoptada");
        Element adoptar = propuesta.selectFirst("button.adoptar");
        assertThat(adoptar.attr("hx-post")).isEqualTo("/tecnicas/T34/formulario");
        assertThat(adoptar.attr("hx-vals")).isEqualTo("{\"_accion\":\"adoptar:IA1\"}");
        assertThat(adoptar.attr("aria-label")).isEqualTo("Adoptar IA1");
        assertThat(d.select("input[type=hidden][name=origenSteelman]")).hasSize(1);
    }

    @Test
    void adoptada_lo_dice_con_texto_y_ya_no_ofrece_adoptar() {
        Document d = pintar("fragmentos/ficha/formulario.jte", Map.of("f", formulario(true, new VistaFormulario.Modelo(true, true, false)), "oob", false));
        Element propuesta = d.selectFirst("li.propuesta[data-propuesta=IA1]");
        assertThat(propuesta.select(".chip").text()).isEqualTo("adoptada por ti · cuenta");
        assertThat(propuesta.select("input[name=propuestas[0].adoptada]").attr("value")).isEqualTo("true");
        assertThat(propuesta.select("button.adoptar")).isEmpty();
    }

    @Test
    void pedir_propuestas_va_a_la_zona_de_espera_sin_doble_envio_y_sin_ollama_queda_deshabilitado_con_texto() {
        Document conModelo = pintar("fragmentos/ficha/formulario.jte", Map.of("f", formulario(false, new VistaFormulario.Modelo(true, true, false)), "oob", false));
        Element pedir = conModelo.selectFirst("#pedir-T34 button[data-accion=proponer]");
        assertThat(pedir.attr("hx-post")).isEqualTo("/tecnicas/T34/propuestas");
        assertThat(pedir.attr("hx-target")).isEqualTo("#espera-T34");
        assertThat(pedir.attr("hx-disabled-elt")).isEqualTo("this");
        assertThat(pedir.attr("hx-sync")).isEqualTo("closest form:drop");

        Document sinModelo = pintar("fragmentos/ficha/formulario.jte", Map.of("f", formulario(false, new VistaFormulario.Modelo(true, false, true)), "oob", false));
        assertThat(sinModelo.selectFirst("#pedir-T34 button").hasAttr("disabled")).isTrue();
        assertThat(sinModelo.select("#pedir-T34").text()).contains("El modelo no está disponible: sigues en modo plantillas.").contains("experimental");

        Document manual = pintar("fragmentos/ficha/formulario.jte", Map.of("f", formulario(false, VistaFormulario.Modelo.NINGUNO), "oob", false));
        assertThat(manual.select("#pedir-T34")).isEmpty();
    }

    @Test
    void la_espera_abre_una_conexion_sse_por_turno_con_tiempo_provisional_cancelar_y_el_boton_deshabilitado() {
        Document d = pintar("fragmentos/ficha/espera.jte", Map.of("f", formulario(false, new VistaFormulario.Modelo(true, true, false)),
                "turno", "0199a000-0000-7000-8000-000000000001", "tecnica", "T34"));

        Element espera = d.selectFirst(".espera");
        assertThat(espera.attr("hx-ext")).isEqualTo("sse");
        assertThat(espera.attr("sse-connect")).isEqualTo("/ia/turnos/0199a000-0000-7000-8000-000000000001/flujo");
        assertThat(espera.attr("sse-close")).isEqualTo("fin");
        assertThat(espera.attr("aria-busy")).isEqualTo("true");
        assertThat(d.selectFirst(".estado-espera").attr("role")).isEqualTo("status");
        assertThat(d.selectFirst("[sse-swap=tiempo]").text()).isEqualTo("0 s");
        assertThat(d.selectFirst("[sse-swap=token]").attr("hx-swap")).isEqualTo("beforeend");
        assertThat(d.selectFirst("[sse-swap=token]").attr("aria-live")).isEqualTo("off");
        assertThat(d.selectFirst("[sse-swap=fin]").attr("hx-target")).isEqualTo("#espera-T34");
        assertThat(d.selectFirst("button[data-accion=cancelar]").attr("hx-post")).isEqualTo("/ia/turnos/0199a000-0000-7000-8000-000000000001/cancelar");
        Element boton = d.selectFirst("#pedir-T34");
        assertThat(boton.attr("hx-swap-oob")).isEqualTo("true");
        assertThat(boton.selectFirst("button").hasAttr("disabled")).isTrue();
        assertThat(d.select("[style], [fill]")).isEmpty();
    }

    @Test
    void v04_de_t34_tiene_la_raiz_del_contrato_dos_columnas_y_las_preguntas_sin_responder() {
        Ejemplo sucursal = CATALOGO.ejemplosDe(EjecutorSteelman.ID).get(1);
        var valor = new EjecutorSteelman().ejecutar(MapeadorJson.leer(sucursal.config(), EjecutorSteelman.Config.class),
                MapeadorJson.leer(sucursal.datos(), EjecutorSteelman.Entrada.class), Contextos.sinIa()).valor();
        UUID id = UUID.fromString("01a118b3-94cc-76f0-afe1-daab7018e19a");
        StringOutput salida = new StringOutput();
        new RenderizadorSteelman(PLANTILLAS).render(Optional.of(id), "", valor, Modo.LECTURA).writeTo(salida);
        Document d = Jsoup.parseBodyFragment(salida.toString());

        Element raiz = d.getElementById("res-" + id);
        assertThat(raiz.attr("data-patron")).isEqualTo("V04");
        assertThat(raiz.attr("aria-labelledby")).isEqualTo("res-" + id + "-titulo");
        assertThat(d.select(".dos-columnas > section.columna h4").eachText()).containsExactly("Postura contraria", "Steelman");
        assertThat(d.select(".estado-resultado .chip").text()).isEqualTo("por confirmar");
        assertThat(d.select("ol.preguntas-abiertas li")).hasSize(1);
        assertThat(d.select(".tarjeta-resultado").attr("role")).isEqualTo("status");
        assertThat(d.select(".tarjeta-resultado").text()).contains("Steelman de 22 palabras").doesNotContain("correcto");
        assertThat(d.select("a[href=/ejecuciones/" + id + "]").text()).isEqualTo("Abrir en la técnica");
        assertThat(d.select("[style], [fill]")).isEmpty();
    }
}
