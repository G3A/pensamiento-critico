package pensamiento.unidad.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import pensamiento.catalogo.CatalogoJson;
import pensamiento.catalogo.MapeadorJson;
import pensamiento.nucleo.Ejecutor;
import pensamiento.nucleo.Ejemplo;
import pensamiento.nucleo.Tecnica;
import pensamiento.tecnicas.f4.EjecutorCraap;
import pensamiento.tecnicas.f4.EjecutorJerarquia;
import pensamiento.tecnicas.f4.EjecutorLecturaLateral;
import pensamiento.tecnicas.f4.EjecutorSift;
import pensamiento.testutil.builders.Contextos;
import pensamiento.web.patrones.Modo;
import pensamiento.web.patrones.RenderizadorCraap;
import pensamiento.web.patrones.RenderizadorJerarquia;
import pensamiento.web.patrones.RenderizadorLecturaLateral;
import pensamiento.web.patrones.RenderizadorResultado;
import pensamiento.web.patrones.RenderizadorSift;
import pensamiento.web.patrones.Renderizadores;

/**
 * Pruebas de plantilla del hito 6 (RNF-06): cada ejemplo de T19, T20, T21 y T23, pintado con su renderizador real a través
 * de Renderizadores, cumple el contrato del fragmento de patrón (V02 y V10) y la accesibilidad, y nunca dice "verdadero".
 */
class PlantillasDelHito6Test {

    private static final TemplateEngine PLANTILLAS = TemplateEngine.createPrecompiled(ContentType.Html);
    private static final CatalogoJson CATALOGO = new CatalogoJson();
    private static final Renderizadores RENDERIZADORES = new Renderizadores(List.<RenderizadorResultado<?>>of(
            new RenderizadorSift(PLANTILLAS), new RenderizadorLecturaLateral(PLANTILLAS), new RenderizadorCraap(PLANTILLAS),
            new RenderizadorJerarquia(PLANTILLAS)));
    private static final List<Ejecutor<?, ?, ?>> EJECUTORES = List.of(new EjecutorSift(), new EjecutorLecturaLateral(), new EjecutorCraap(),
            new EjecutorJerarquia());
    private static final UUID ID = UUID.fromString("01a118b3-94cc-76f0-afe1-daab7018e19c");

    static Stream<Arguments> ejemplos() {
        List<Arguments> casos = new ArrayList<>();
        for (Ejecutor<?, ?, ?> e : EJECUTORES) {
            for (Ejemplo ej : CATALOGO.ejemplosDe(e.id())) {
                casos.add(Arguments.of(e.id() + " · " + ej.titulo(), e, ej));
            }
        }
        return casos.stream();
    }

    private static Tecnica tecnica(Ejecutor<?, ?, ?> e) {
        return CATALOGO.tecnicas().stream().filter(t -> t.id().equals(e.id())).findFirst().orElseThrow();
    }

    private static <C, E, R> R valor(Ejecutor<C, E, R> e, Ejemplo ej) {
        return e.ejecutar(MapeadorJson.leer(ej.config(), e.tipos().config()), MapeadorJson.leer(ej.datos(), e.tipos().entrada()),
                Contextos.sinIa()).valor();
    }

    private static Document pintar(Ejecutor<?, ?, ?> e, Object valor, Modo modo) {
        StringOutput salida = new StringOutput();
        RENDERIZADORES.render(tecnica(e), Optional.of(ID), "", valor, modo).writeTo(salida);
        return Jsoup.parseBodyFragment(salida.toString());
    }

    private static Ejemplo ejemplo(Ejecutor<?, ?, ?> e, String titulo) {
        return CATALOGO.ejemplosDe(e.id()).stream().filter(x -> x.titulo().equals(titulo)).findFirst().orElseThrow();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("ejemplos")
    void cada_ejemplo_cumple_el_contrato_del_fragmento_y_la_accesibilidad(String nombre, Ejecutor<?, ?, ?> e, Ejemplo ej) {
        Document d = pintar(e, valor(e, ej), Modo.LECTURA);

        Element raiz = d.getElementById("res-" + ID);
        assertThat(raiz).as(nombre).isNotNull();
        assertThat(raiz.attr("data-patron")).isEqualTo(tecnica(e).patron());
        assertThat(raiz.attr("data-modo")).isEqualTo("lectura");
        assertThat(raiz.attr("aria-labelledby")).isEqualTo("res-" + ID + "-titulo");
        assertThat(d.getElementById("res-" + ID + "-titulo").text()).isNotBlank();
        assertThat(d.select(".tarjeta-resultado").attr("role")).isEqualTo("status");
        assertThat(d.select(".tarjeta-resultado .titular").text()).isNotBlank();
        assertThat(d.select("[style], [fill], [stroke], script")).as("sin style, fill ni scripts").isEmpty();
        assertThat(d.select(".chip")).allSatisfy(chip -> assertThat(chip.text()).as("todo estado lleva texto").isNotBlank());
        assertThat(d.select("meter")).allSatisfy(m -> assertThat(d.select("label[for=" + m.id() + "]").text()).as("medidor con etiqueta").isNotBlank());
        assertThat(d.select("a[href=/ejecuciones/" + ID + "]").text()).isEqualTo("Abrir en la técnica");
        assertThat(d.text().toLowerCase()).doesNotContain("válido", "verdadero", "verdadera", "correcto", "probado", "bien hecho");
    }

    @Test
    void v02_de_t19_dice_la_senal_como_alerta_con_texto_y_el_paso_pendiente() {
        EjecutorSift t19 = new EjecutorSift();
        Document d = pintar(t19, valor(t19, ejemplo(t19, "Las cámaras de Villa Norte")), Modo.COMPLETO);

        assertThat(d.select(".aviso-alerta .chip").text()).isEqualTo("alerta");
        assertThat(d.select(".aviso-alerta").text()).contains("Señal: fuente interesada.");
        assertThat(d.select("[data-parte=origen] .chip").text()).isEqualTo("pendiente");
        assertThat(d.text()).contains("Pasos hechos 3 de 4", "Hasta 5 minutos por paso.");
    }

    @Test
    void v10_de_t20_dice_cuales_fuentes_externas_no_cuentan() {
        EjecutorLecturaLateral t20 = new EjecutorLecturaLateral();
        Document d = pintar(t20, valor(t20, ejemplo(t20, "El 70% de las cámaras")), Modo.COMPLETO);

        assertThat(d.select("[data-item=E3] .chip").text()).isEqualTo("la confirma · no cuenta");
        assertThat(d.select(".veredicto .chip").text()).isEqualTo("en duda");
        assertThat(d.text()).contains("No cuenta por no ser independiente: Blog del fabricante.");
    }

    @Test
    void v10_de_t21_con_una_fuente_muestra_una_barra_por_criterio_y_el_puntaje() {
        EjecutorCraap t21 = new EjecutorCraap();
        Document d = pintar(t21, valor(t21, ejemplo(t21, "La página de las cámaras")), Modo.COMPLETO);

        assertThat(d.select("meter")).hasSize(6);
        assertThat(d.select("[data-barra=puntaje] meter").attr("value")).isEqualTo("11");
        assertThat(d.select(".veredicto .chip").text()).isEqualTo("no aprobada");
    }

    @Test
    void v10_de_t23_marca_los_niveles_sin_evidencia_y_la_neta_negativa() {
        EjecutorJerarquia t23 = new EjecutorJerarquia();
        Document d = pintar(t23, valor(t23, ejemplo(t23, "La dieta sin gluten")), Modo.COMPLETO);

        assertThat(d.select("[data-item=nivel-1] .chip").text()).isEqualTo("ninguna encontrada");
        assertThat(d.select("[data-barra=neta] meter").attr("value")).isEqualTo("-3");
        assertThat(d.text()).contains("Fuerza neta -3 (media)", "media en contra");
    }
}
