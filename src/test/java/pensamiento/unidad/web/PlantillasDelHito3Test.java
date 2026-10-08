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
import pensamiento.tecnicas.f1.EjecutorAnalogia;
import pensamiento.tecnicas.f1.EjecutorCer;
import pensamiento.tecnicas.f1.EjecutorPaulElder;
import pensamiento.tecnicas.f1.EjecutorValidez;
import pensamiento.tecnicas.f3.EjecutorHechoInferencia;
import pensamiento.tecnicas.f3.EjecutorListaDecision;
import pensamiento.tecnicas.f3.EjecutorOpuesto;
import pensamiento.tecnicas.f3.EjecutorSesgos;
import pensamiento.tecnicas.f3.EjecutorTasasBase;
import pensamiento.tecnicas.f4.EjecutorTriangulacion;
import pensamiento.tecnicas.f6.EjecutorSteelman;
import pensamiento.testutil.builders.Contextos;
import pensamiento.web.patrones.Modo;
import pensamiento.web.patrones.RenderizadorAnalogia;
import pensamiento.web.patrones.RenderizadorCer;
import pensamiento.web.patrones.RenderizadorHechoInferencia;
import pensamiento.web.patrones.RenderizadorListaDecision;
import pensamiento.web.patrones.RenderizadorOpuesto;
import pensamiento.web.patrones.RenderizadorPaulElder;
import pensamiento.web.patrones.RenderizadorResultado;
import pensamiento.web.patrones.RenderizadorSesgos;
import pensamiento.web.patrones.RenderizadorSteelman;
import pensamiento.web.patrones.RenderizadorTasasBase;
import pensamiento.web.patrones.RenderizadorTriangulacion;
import pensamiento.web.patrones.RenderizadorValidez;
import pensamiento.web.patrones.Renderizadores;

/**
 * Pruebas de plantilla del hito 3 (RNF-06): cada ejemplo de las técnicas nuevas, pintado con su renderizador real a
 * través de Renderizadores, cumple el contrato del fragmento de patrón (raíz res-{id}, data-patron del catálogo,
 * título enlazado) y las reglas de accesibilidad: estados con texto, tarjeta con role=status, sin style ni fill.
 */
class PlantillasDelHito3Test {

    private static final TemplateEngine PLANTILLAS = TemplateEngine.createPrecompiled(ContentType.Html);
    private static final CatalogoJson CATALOGO = new CatalogoJson();
    private static final Renderizadores RENDERIZADORES = new Renderizadores(List.<RenderizadorResultado<?>>of(new RenderizadorCer(PLANTILLAS),
            new RenderizadorPaulElder(PLANTILLAS), new RenderizadorValidez(PLANTILLAS), new RenderizadorAnalogia(PLANTILLAS),
            new RenderizadorSesgos(PLANTILLAS), new RenderizadorOpuesto(PLANTILLAS), new RenderizadorListaDecision(PLANTILLAS),
            new RenderizadorHechoInferencia(PLANTILLAS), new RenderizadorTasasBase(PLANTILLAS), new RenderizadorTriangulacion(PLANTILLAS),
            new RenderizadorSteelman(PLANTILLAS)));
    private static final List<Ejecutor<?, ?, ?>> EJECUTORES = List.of(new EjecutorCer(), new EjecutorPaulElder(), new EjecutorValidez(),
            new EjecutorAnalogia(), new EjecutorSesgos(), new EjecutorOpuesto(), new EjecutorListaDecision(), new EjecutorHechoInferencia(),
            new EjecutorTasasBase(), new EjecutorTriangulacion(), new EjecutorSteelman());
    private static final UUID ID = UUID.fromString("01a118b3-94cc-76f0-afe1-daab7018e19a");

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

    private static <C, E, R> Document pintar(Ejecutor<C, E, R> e, Ejemplo ej, Modo modo) {
        R valor = e.ejecutar(MapeadorJson.leer(ej.config(), e.tipos().config()), MapeadorJson.leer(ej.datos(), e.tipos().entrada()),
                Contextos.sinIa()).valor();
        StringOutput salida = new StringOutput();
        RENDERIZADORES.render(tecnica(e), Optional.of(ID), "", valor, modo).writeTo(salida);
        return Jsoup.parseBodyFragment(salida.toString());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("ejemplos")
    void cada_ejemplo_cumple_el_contrato_del_fragmento_y_la_accesibilidad(String nombre, Ejecutor<?, ?, ?> e, Ejemplo ej) {
        Document d = pintar(e, ej, Modo.LECTURA);

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
        assertThat(d.select("meter")).allSatisfy(m -> assertThat(d.select("label[for=" + m.id() + "]").text()).isNotBlank());
        assertThat(d.select("a[href=/ejecuciones/" + ID + "]").text()).isEqualTo("Abrir en la técnica");
        assertThat(d.text().toLowerCase()).doesNotContain("válido", "verdadero", "correcto");
    }

    @Test
    void v08_dibuja_el_grafico_como_svg_de_servidor_con_los_numeros_en_una_tabla() {
        Ejemplo examen = CATALOGO.ejemplosDe(EjecutorTasasBase.ID).getFirst();
        Document d = pintar(new EjecutorTasasBase(), examen, Modo.COMPLETO);
        Element svg = d.selectFirst("figure.grafico-frecuencias svg");
        assertThat(svg.attr("role")).isEqualTo("img");
        assertThat(svg.select("rect.barra")).hasSize(2);
        assertThat(svg.select("[data-barra=falsos] .valor-barra").text()).isEqualTo("10");
        assertThat(svg.select("[data-barra=detectados] .valor-barra").text()).isEqualTo("1");
        assertThat(d.select("figcaption").text()).isEqualTo("De cada 11 positivos, 1 tiene la condición: 9%, no 99%.");
        assertThat(d.select("table.tabla-frecuencias caption").text()).isEqualTo("Los números del cálculo");
        assertThat(d.select("table.tabla-frecuencias tr")).hasSize(8);
    }

    @Test
    void v02_de_t16_dice_con_texto_que_el_guardado_esta_bloqueado() {
        Ejemplo arriendo = CATALOGO.ejemplosDe(EjecutorListaDecision.ID).getFirst();
        Document d = pintar(new EjecutorListaDecision(), arriendo, Modo.COMPLETO);
        assertThat(d.select(".aviso-aviso").text()).isEqualTo("bloqueado Guardado bloqueado: faltan 2 ítems obligatorios.");
        assertThat(d.select("li.parte .chip").eachText()).containsExactly("respondido", "respondido", "falta (obligatorio)", "respondido",
                "falta (obligatorio)");
    }

    @Test
    void v10_de_t22_tiene_la_barra_de_fuerza_neta_y_el_estado_con_su_motivo() {
        Ejemplo robos = CATALOGO.ejemplosDe(EjecutorTriangulacion.ID).get(1);
        Document d = pintar(new EjecutorTriangulacion(), robos, Modo.COMPLETO);
        Element meter = d.selectFirst("meter");
        assertThat(meter.attr("min")).isEqualTo("-8");
        assertThat(meter.attr("value")).isEqualTo("6");
        assertThat(d.select(".veredicto .chip").text()).isEqualTo("en verificación");
        assertThat(d.select("li[data-item=F3] .chip").text()).isEqualTo("sin etiquetar · no cuenta");
    }

    @Test
    void v05_de_t17_marca_cada_oracion_con_su_grupo() {
        Ejemplo robos = CATALOGO.ejemplosDe(EjecutorHechoInferencia.ID).getFirst();
        Document d = pintar(new EjecutorHechoInferencia(), robos, Modo.COMPLETO);
        assertThat(d.select("mark.marca-texto")).extracting(m -> m.className())
                .containsExactly("marca-texto grupo-hecho", "marca-texto grupo-inferencia", "marca-texto grupo-juicio");
        assertThat(d.select("blockquote.texto-marcado").text()).contains("Hubo 12 robos en la cuadra este año.");
    }

    @Test
    void v13a_de_t14_pone_primero_los_sesgos_probables() {
        Ejemplo anuncio = CATALOGO.ejemplosDe(EjecutorSesgos.ID).get(1);
        Document d = pintar(new EjecutorSesgos(), anuncio, Modo.COMPLETO);
        assertThat(d.select("ol.lista-priorizada li .chip").eachText()).containsExactly("probable", "probable", "probable", "sin señal", "sin señal");
        assertThat(d.select("ul.acciones-sugeridas li")).hasSize(3);
    }
}
