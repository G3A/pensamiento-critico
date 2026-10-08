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
import pensamiento.nucleo.puertos.Grafico;
import pensamiento.tecnicas.f7.EjecutorArbolMece;
import pensamiento.tecnicas.f7.EjecutorDefinicionProblema;
import pensamiento.tecnicas.f7.EjecutorIshikawa;
import pensamiento.tecnicas.f7.EjecutorPrimerosPrincipios;
import pensamiento.tecnicas.f7.EjecutorScamper;
import pensamiento.tecnicas.f7.ResultadoArbolMece;
import pensamiento.tecnicas.f7.ResultadoIshikawa;
import pensamiento.testutil.builders.Contextos;
import pensamiento.testutil.fakes.FakeGrafico;
import pensamiento.web.patrones.Modo;
import pensamiento.web.patrones.RenderizadorArbolMece;
import pensamiento.web.patrones.RenderizadorDefinicionProblema;
import pensamiento.web.patrones.RenderizadorIshikawa;
import pensamiento.web.patrones.RenderizadorPrimerosPrincipios;
import pensamiento.web.patrones.RenderizadorResultado;
import pensamiento.web.patrones.RenderizadorScamper;
import pensamiento.web.patrones.Renderizadores;

/**
 * Pruebas de plantilla del hito 4 (RNF-06): cada ejemplo de las técnicas nuevas, pintado con su renderizador real a
 * través de Renderizadores, cumple el contrato del fragmento de patrón (raíz res-{id}, data-patron del catálogo, título
 * enlazado) y la accesibilidad: estados con texto, tarjeta con role=status, sin style ni fill. V06 y V07 dibujan con el
 * Fake de Grafico (certificado contra Graphviz) y conservan el id y la clase de cada nodo.
 */
class PlantillasDelHito4Test {

    private static final TemplateEngine PLANTILLAS = TemplateEngine.createPrecompiled(ContentType.Html);
    private static final CatalogoJson CATALOGO = new CatalogoJson();
    private static final FakeGrafico GRAFICO = new FakeGrafico();
    private static final Renderizadores RENDERIZADORES = new Renderizadores(List.<RenderizadorResultado<?>>of(
            new RenderizadorDefinicionProblema(PLANTILLAS), new RenderizadorPrimerosPrincipios(PLANTILLAS),
            new RenderizadorArbolMece(PLANTILLAS, GRAFICO), new RenderizadorIshikawa(PLANTILLAS, GRAFICO), new RenderizadorScamper(PLANTILLAS)));
    private static final List<Ejecutor<?, ?, ?>> EJECUTORES = List.of(new EjecutorDefinicionProblema(), new EjecutorPrimerosPrincipios(),
            new EjecutorArbolMece(), new EjecutorIshikawa(), new EjecutorScamper());
    private static final UUID ID = UUID.fromString("01a118b3-94cc-76f0-afe1-daab7018e19b");

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
        assertThat(d.text().toLowerCase()).doesNotContain("válido", "verdadero", "correcto", "probado");
    }

    @Test
    void v06_de_t42_dibuja_el_arbol_con_el_id_de_cada_afirmacion_y_su_clase() {
        Ejemplo robos = CATALOGO.ejemplosDe(EjecutorArbolMece.ID).getFirst();
        ResultadoArbolMece arbol = valor(new EjecutorArbolMece(), robos);
        Document d = pintar(new EjecutorArbolMece(), arbol, Modo.COMPLETO);

        assertThat(d.getElementById(arbol.raizId().toString()).classNames()).contains("node", "raiz");
        ResultadoArbolMece.NodoArbol respuesta = arbol.nodos().get(2);
        assertThat(d.getElementById(respuesta.afirmacionId().toString()).classNames()).contains("rama", "vacia");
        assertThat(d.select("svg g.node")).hasSize(arbol.nodos().size() + 1);
        assertThat(d.select("ol.lista-arbol li")).hasSize(arbol.nodos().size() + 1);
        assertThat(d.select("ol.lista-arbol li[data-nodo=" + respuesta.afirmacionId() + "] .chip").text()).isEqualTo("rama vacía");
        assertThat(d.select("ol.lista-arbol li.nivel-2")).hasSize(3);
        assertThat(d.select(".lineas-seccion li").eachText()).contains("Respuesta: rama vacía.");
    }

    @Test
    void v06_sin_graphviz_muestra_la_lista_y_dice_que_no_se_pudo_dibujar() {
        Ejemplo robos = CATALOGO.ejemplosDe(EjecutorArbolMece.ID).getFirst();
        Grafico caido = dot -> {
            throw new Grafico.GraficoTiempoAgotado("dot superó el tiempo máximo de 5 s");
        };
        StringOutput salida = new StringOutput();
        new RenderizadorArbolMece(PLANTILLAS, caido).render(Optional.empty(), "caido", valor(new EjecutorArbolMece(), robos), Modo.COMPLETO)
                .writeTo(salida);
        Document d = Jsoup.parseBodyFragment(salida.toString());

        assertThat(d.select("svg")).isEmpty();
        assertThat(d.select("[role=status]").first().text()).startsWith("No se pudo dibujar el diagrama");
        assertThat(d.select("ol.lista-arbol li")).hasSize(7);
    }

    @Test
    void v07_de_t43_dibuja_la_espina_con_las_categorias_vacias_marcadas_con_texto() {
        Ejemplo pan = CATALOGO.ejemplosDe(EjecutorIshikawa.ID).getFirst();
        ResultadoIshikawa ishikawa = valor(new EjecutorIshikawa(), pan);
        Document d = pintar(new EjecutorIshikawa(), ishikawa, Modo.COMPLETO);

        assertThat(d.getElementById(ishikawa.efectoId().toString()).classNames()).contains("efecto");
        assertThat(d.getElementById("res-" + ID + "-categoria-3").classNames()).contains("categoria", "vacia");
        assertThat(d.getElementById("res-" + ID + "-espina-1").classNames()).contains("espina");
        assertThat(d.getElementById(ishikawa.categorias().getFirst().causas().getFirst().afirmacionId().toString()).classNames()).contains("causa");
        assertThat(d.select("ul.categorias-ishikawa > li .chip").eachText()).containsExactly("vacía", "vacía", "vacía");
    }

    @Test
    void v03c_de_t44_pinta_una_celda_por_operador_activo_y_marca_las_seleccionadas_con_texto() {
        Ejemplo robos = CATALOGO.ejemplosDe(EjecutorScamper.ID).getFirst();
        Document d = pintar(new EjecutorScamper(), valor(new EjecutorScamper(), robos), Modo.COMPLETO);

        assertThat(d.select(".rejilla section.celda")).hasSize(7);
        assertThat(d.select("section.celda[data-celda=modificar] .chip").text()).isEqualTo("sin ideas");
        assertThat(d.select(".chip:containsOwn(seleccionada)")).hasSize(3);
        assertThat(d.select("ul.seleccion li")).hasSize(3);
        assertThat(d.select("p.detalle").text()).contains("Tiempo sugerido: 2 minutos por operador.");
    }
}
