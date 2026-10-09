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
import pensamiento.tecnicas.f2.EjecutorCincoPorques;
import pensamiento.tecnicas.f2.EjecutorEscalera;
import pensamiento.tecnicas.f2.EjecutorFalsacion;
import pensamiento.tecnicas.f2.EjecutorPreguntasSocraticas;
import pensamiento.tecnicas.f2.EjecutorTerminos;
import pensamiento.tecnicas.f2.ResultadoCincoPorques;
import pensamiento.tecnicas.f2.ResultadoPreguntasSocraticas;
import pensamiento.tecnicas.f6.EjecutorDoubleCrux;
import pensamiento.tecnicas.f6.EjecutorEquipoRojo;
import pensamiento.tecnicas.f6.EjecutorEtico;
import pensamiento.tecnicas.f6.EjecutorSeisSombreros;
import pensamiento.tecnicas.f6.EjecutorTuring;
import pensamiento.tecnicas.f6.ResultadoEquipoRojo;
import pensamiento.testutil.builders.Contextos;
import pensamiento.testutil.fakes.FakeGrafico;
import pensamiento.testutil.fakes.FakeRepositorioEsquemas;
import pensamiento.web.patrones.Modo;
import pensamiento.web.patrones.RenderizadorCincoPorques;
import pensamiento.web.patrones.RenderizadorEquipoRojo;
import pensamiento.web.patrones.RenderizadorPreguntasSocraticas;
import pensamiento.web.patrones.RenderizadorResultado;
import pensamiento.web.patrones.Renderizadores;
import pensamiento.web.patrones.RenderizadoresF2F6;

/**
 * Pruebas de plantilla del hito 5 (RNF-06): cada ejemplo de T08 a T12 y T35 a T39, pintado con su renderizador real a
 * través de Renderizadores, cumple el contrato del fragmento de patrón y la accesibilidad. V09 es nuevo: la transcripción
 * es un role="log" y el panel lateral un aside con nombre; V06 de T09 conserva el id de cada afirmación y su clase.
 */
class PlantillasDelHito5Test {

    private static final TemplateEngine PLANTILLAS = TemplateEngine.createPrecompiled(ContentType.Html);
    private static final CatalogoJson CATALOGO = new CatalogoJson();
    private static final FakeGrafico GRAFICO = new FakeGrafico();
    private static final Renderizadores RENDERIZADORES = new Renderizadores(List.<RenderizadorResultado<?>>of(
            new RenderizadorPreguntasSocraticas(PLANTILLAS), new RenderizadorCincoPorques(PLANTILLAS, GRAFICO), new RenderizadoresF2F6.Escalera(PLANTILLAS),
            new RenderizadoresF2F6.Falsacion(PLANTILLAS), new RenderizadoresF2F6.Terminos(PLANTILLAS), new RenderizadoresF2F6.SeisSombreros(PLANTILLAS),
            new RenderizadorEquipoRojo(PLANTILLAS), new RenderizadoresF2F6.Turing(PLANTILLAS), new RenderizadoresF2F6.DoubleCrux(PLANTILLAS),
            new RenderizadoresF2F6.Etico(PLANTILLAS)));
    private static final List<Ejecutor<?, ?, ?>> EJECUTORES = List.of(new EjecutorPreguntasSocraticas(), new EjecutorCincoPorques(), new EjecutorEscalera(),
            new EjecutorFalsacion(), new EjecutorTerminos(), new EjecutorSeisSombreros(), new EjecutorEquipoRojo(new FakeRepositorioEsquemas()),
            new EjecutorTuring(), new EjecutorDoubleCrux(), new EjecutorEtico());
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
        assertThat(d.select("a[href=/ejecuciones/" + ID + "]").text()).isEqualTo("Abrir en la técnica");
        assertThat(d.text().toLowerCase()).doesNotContain("válido", "verdadero", "correcto", "probado", "bien hecho");
    }

    @Test
    void v09_de_t08_es_una_transcripcion_con_rol_log_y_un_panel_lateral_con_nombre() {
        Ejemplo camaras = CATALOGO.ejemplosDe(EjecutorPreguntasSocraticas.ID).getFirst();
        ResultadoPreguntasSocraticas r = valor(new EjecutorPreguntasSocraticas(), camaras);
        Document d = pintar(new EjecutorPreguntasSocraticas(), r, Modo.COMPLETO);

        Element log = d.selectFirst("ol.transcripcion");
        assertThat(log.attr("role")).isEqualTo("log");
        assertThat(log.attr("aria-label")).isEqualTo("Transcripción");
        assertThat(d.select("ol.transcripcion li.burbuja-consejero")).hasSize(4);
        assertThat(d.select("ol.transcripcion li.burbuja-persona")).hasSize(5);
        assertThat(d.select("li[data-turno=p2] .autor").text()).contains("consejero · supuestos · supuestos", "del banco", "rama: absoluta");
        assertThat(d.select("li[data-turno=p2] .detalle").text()).isEqualTo("Adaptativo: «nunca» es una afirmación absoluta y los supuestos están pendientes.");
        assertThat(d.select(".burbuja-siguiente .texto-burbuja").text()).startsWith("¿Cuál es la pregunta que de verdad");
        assertThat(d.select(".burbuja-siguiente .chip").text()).isEqualTo("por responder");
        Element panel = d.selectFirst("aside.panel-lateral");
        assertThat(panel.attr("aria-label")).isEqualTo("Elementos del razonamiento (Paul-Elder)");
        assertThat(panel.select("li[data-item=informacion] .chip").text()).isEqualTo("lleno");
        assertThat(panel.select("li[data-item=proposito] .chip").text()).isEqualTo("pendiente");
        assertThat(panel.select("li[data-item=exactitud] .chip").text()).isEqualTo("a medias · 5");
    }

    @Test
    void v09_de_t36_dice_quien_ataca_a_que_razon_y_lo_que_falta_responder() {
        Ejemplo vendedor = CATALOGO.ejemplosDe(EjecutorEquipoRojo.ID).getFirst();
        EjecutorEquipoRojo t36 = new EjecutorEquipoRojo(new FakeRepositorioEsquemas());
        ResultadoEquipoRojo r = valor(t36, vendedor);
        Document d = pintar(t36, r, Modo.COMPLETO);

        assertThat(d.select("li[data-turno=A1] .autor").text()).contains("equipo rojo · ataque 1 de 3 · a R1", "del banco");
        assertThat(d.select("li[data-turno=r-A2] .chip").text()).isEqualTo("sin responder");
        assertThat(d.select("aside.panel-lateral").attr("aria-label")).isEqualTo("Debilidades que identificó el código");
        assertThat(d.select("aside li[data-item=R1-2] .detalle").text()).contains("apelación a una autoridad interesada");
    }

    @Test
    void v06_de_t09_dibuja_la_cadena_con_el_id_de_cada_afirmacion_y_su_clase() {
        Ejemplo pan = CATALOGO.ejemplosDe(EjecutorCincoPorques.ID).getFirst();
        ResultadoCincoPorques r = valor(new EjecutorCincoPorques(), pan);
        Document d = pintar(new EjecutorCincoPorques(), r, Modo.COMPLETO);

        assertThat(d.getElementById(r.problemaId().toString()).classNames()).contains("node", "problema");
        ResultadoCincoPorques.Porque raiz = r.porques().getLast();
        assertThat(d.getElementById(raiz.afirmacionId().toString()).classNames()).contains("porque", "raiz");
        assertThat(d.select("svg g.node")).hasSize(r.porques().size() + 1);
        assertThat(d.select("ol.lista-arbol li")).hasSize(r.porques().size() + 1);
        assertThat(d.select("ol.lista-arbol li[data-nodo=" + raiz.afirmacionId() + "] .chip").text()).isEqualTo("causa raíz");
    }

    @Test
    void v03b_de_t39_no_tiene_columna_de_puesto_ni_pesos_y_titula_los_conflictos() {
        Ejemplo camaras = CATALOGO.ejemplosDe(EjecutorEtico.ID).getFirst();
        Document d = pintar(new EjecutorEtico(), valor(new EjecutorEtico(), camaras), Modo.COMPLETO);

        assertThat(d.select("table thead th").eachText()).containsExactly("Marco", "Vecinos", "Visitantes", "Empleadas domésticas", "Balance");
        assertThat(d.select("table tbody tr").first().select("td.total").text()).isEqualTo("0");
        assertThat(d.select("h4.subtitulo-resultado").eachText()).contains("Conflictos entre marcos");
        assertThat(d.select("ul.sensibilidad li").eachText())
                .containsExactly("Reparto desigual: beneficia a Vecinos y perjudica a Visitantes y Empleadas domésticas.");
    }

    @Test
    void v10_de_t37_muestra_el_puntaje_de_la_rubrica_como_medidor_con_etiqueta() {
        Ejemplo socia = CATALOGO.ejemplosDe(EjecutorTuring.ID).get(1);
        Document d = pintar(new EjecutorTuring(), valor(new EjecutorTuring(), socia), Modo.COMPLETO);

        assertThat(d.select("meter").first().attr("value")).isEqualTo("30");
        assertThat(d.text()).contains("Rúbrica 30 de 100 (umbral 70)", "no aprueba la rúbrica", "posible caricatura", "tono burlón");
    }
}
