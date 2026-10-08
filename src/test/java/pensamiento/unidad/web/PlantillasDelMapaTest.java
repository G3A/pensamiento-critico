package pensamiento.unidad.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;

import pensamiento.argdown.ParserArgdown;
import pensamiento.catalogo.CatalogoJson;
import pensamiento.catalogo.MapeadorJson;
import pensamiento.nucleo.Ejemplo;
import pensamiento.nucleo.EstandarPrueba;
import pensamiento.nucleo.puertos.Grafico;
import pensamiento.tecnicas.f1.EjecutorMapa;
import pensamiento.tecnicas.f1.EjecutorPremisasOcultas;
import pensamiento.tecnicas.f1.EjecutorToulmin;
import pensamiento.tecnicas.f1.ResultadoMapa;
import pensamiento.tecnicas.f1.ResultadoToulmin;
import pensamiento.tecnicas.f3.ConfigFalacias;
import pensamiento.tecnicas.f3.EjecutorFalacias;
import pensamiento.tecnicas.f3.EntradaFalacias;
import pensamiento.tecnicas.f3.ResultadoFalacias;
import pensamiento.testutil.builders.Contextos;
import pensamiento.testutil.fakes.FakeGrafico;
import pensamiento.testutil.fakes.FakeRepositorioEsquemas;
import pensamiento.web.patrones.GeneradorDot;
import pensamiento.web.patrones.Modo;
import pensamiento.web.patrones.RenderizadorV01;
import pensamiento.web.patrones.RenderizadorV02;
import pensamiento.web.patrones.RenderizadorV05;

/**
 * Pruebas de plantilla de los patrones del hito 2 (RNF-06): V01 con el SVG del Fake de Grafico (certificado contra
 * Graphviz), V02 y V05, cada uno pintado con los ejemplos del catálogo y revisado con jsoup.
 */
class PlantillasDelMapaTest {

    private static final TemplateEngine PLANTILLAS = TemplateEngine.createPrecompiled(ContentType.Html);
    private static final CatalogoJson CATALOGO = new CatalogoJson();
    private static final ParserArgdown PARSER = new ParserArgdown();

    private static Document pintar(gg.jte.Content contenido) {
        StringOutput salida = new StringOutput();
        contenido.writeTo(salida);
        return Jsoup.parseBodyFragment(salida.toString());
    }

    private static ResultadoMapa mapa(int ejemplo) {
        Ejemplo e = CATALOGO.ejemplosDe(EjecutorMapa.ID).get(ejemplo);
        return new EjecutorMapa(PARSER).ejecutar(MapeadorJson.leer(e.config(), EjecutorMapa.Config.class),
                MapeadorJson.leer(e.datos(), EjecutorMapa.Entrada.class), Contextos.sinIa()).valor();
    }

    // -------------------------------------------------------------------------------------------
    // V01
    // -------------------------------------------------------------------------------------------

    @Test
    void v01_tiene_la_raiz_del_contrato_y_un_nodo_por_afirmacion_con_id_y_clase_por_rol() {
        UUID id = UUID.fromString("01a118b3-94cc-76f0-afe1-daab7018e19b");
        ResultadoMapa valor = mapa(1);
        Document d = pintar(new RenderizadorV01(PLANTILLAS, new FakeGrafico()).render(Optional.of(id), "", valor, Modo.COMPLETO));

        Element raiz = d.getElementById("res-" + id);
        assertThat(raiz).isNotNull();
        assertThat(raiz.attr("data-patron")).isEqualTo("V01");
        assertThat(raiz.attr("aria-labelledby")).isEqualTo("res-" + id + "-titulo");
        for (ResultadoMapa.Nodo n : valor.nodos()) {
            Element g = d.getElementById(n.afirmacionId().toString());
            assertThat(g).as("nodo " + n.codigo()).isNotNull();
            assertThat(g.classNames()).contains("node", n.rol().toString());
        }
        assertThat(d.select("svg g.node")).hasSize(valor.nodos().size());
        assertThat(d.select("[x-data=mapa]").attr("x-on:click")).isEqualTo("elegir");
        assertThat(d.select("button.nodo-lista[data-nodo]")).hasSize(4).allSatisfy(b -> {
            assertThat(b.attr("type")).isEqualTo("button");
            assertThat(b.attr("aria-pressed")).isEqualTo("false");
        });
        assertThat(d.select(".lista-argumentos li").eachText()).anySatisfy(t -> assertThat(t).startsWith("aplicable A1 · Más ventas · a favor de N1 · peso 3"));
        assertThat(d.select(".tarjeta-resultado").attr("role")).isEqualTo("status");
        assertThat(d.select(".tarjeta-resultado").text()).contains("Falta responder la objeción «Falta personal para atender dos locales.»")
                .doesNotContain("válido", "correcto");
        assertThat(d.select("pre.argdown").text()).startsWith("[Sucursal]: Conviene abrir la segunda sucursal en el centro.");
    }

    @Test
    void v01_no_lleva_atributos_fill_ni_style_en_ninguna_parte_del_fragmento() {
        Document d = pintar(new RenderizadorV01(PLANTILLAS, new FakeGrafico()).render(Optional.empty(), "borrador", mapa(0), Modo.COMPLETO));

        assertThat(d.getElementById("res-V01-borrador")).isNotNull();
        for (Element e : d.getAllElements()) {
            assertThat(e.attributes().asList()).extracting(Attribute::getKey).as("<" + e.tagName() + ">").doesNotContain("fill", "style", "stroke");
        }
    }

    @Test
    void v01_con_una_etiqueta_hostil_la_muestra_como_texto_y_sin_elementos_ejecutables() {
        String hostil = "[A]: <script>alert(1)</script> \"comillas\" {llaves} -> \\ barra\n  + Apoyo <b>sin</b> marcado.";
        ResultadoMapa valor = new EjecutorMapa(PARSER).ejecutar(new EjecutorMapa.Config(ResultadoMapa.Direccion.ARRIBA_ABAJO, true, false,
                EstandarPrueba.PREPONDERANCIA), new EjecutorMapa.Entrada(hostil), Contextos.sinIa()).valor();

        Document d = pintar(new RenderizadorV01(PLANTILLAS, new FakeGrafico()).render(Optional.empty(), "hostil", valor, Modo.COMPLETO));

        assertThat(d.select("script, foreignObject, iframe, b")).isEmpty();
        assertThat(d.getElementById(valor.nodos().getFirst().afirmacionId().toString()).text())
                .contains("<script>alert(1)</script> \"comillas\" {llaves} -> \\ barra");
        assertThat(GeneradorDot.dot(valor)).contains(Grafico.cadena(valor.nodos().getFirst().afirmacionId().toString()));
    }

    @Test
    void v01_sin_graphviz_muestra_la_lista_de_nodos_y_lo_dice() {
        Grafico caido = dot -> {
            throw new Grafico.GraficoTiempoAgotado("dot superó el tiempo máximo de 5 s");
        };
        Document d = pintar(new RenderizadorV01(PLANTILLAS, caido).render(Optional.empty(), "x", mapa(2), Modo.LECTURA));

        assertThat(d.select("svg")).isEmpty();
        assertThat(d.select("[role=status]").first().text()).contains("No se pudo dibujar el mapa");
        assertThat(d.select("button.nodo-lista")).hasSize(4);
    }

    @Test
    void v01_de_t06_marca_la_premisa_oculta_en_el_mapa_y_pide_verificarla() {
        Ejemplo e = CATALOGO.ejemplosDe(EjecutorPremisasOcultas.ID).getFirst();
        ResultadoMapa valor = new EjecutorPremisasOcultas(PARSER).ejecutar(MapeadorJson.leer(e.config(), EjecutorPremisasOcultas.Config.class),
                MapeadorJson.leer(e.datos(), EjecutorPremisasOcultas.Entrada.class), Contextos.sinIa()).valor();
        UUID oculta = valor.nodos().get(2).afirmacionId();

        Document d = pintar(new RenderizadorV01(PLANTILLAS, new FakeGrafico()).render(Optional.empty(), "t06", valor, Modo.COMPLETO));

        assertThat(d.getElementById(oculta.toString()).classNames()).contains("oculta");
        assertThat(d.select("li.nodo-oculta").text()).contains("N3 · premisa oculta");
        assertThat(d.select(".tarjeta-resultado").text()).contains("depende de un supuesto que no estaba escrito");
    }

    // -------------------------------------------------------------------------------------------
    // V02
    // -------------------------------------------------------------------------------------------

    @Test
    void v02_dice_el_estado_de_cada_parte_con_texto_y_la_pregunta_que_falta() {
        Ejemplo e = CATALOGO.ejemplosDe(EjecutorToulmin.ID).get(2);
        ResultadoToulmin valor = new EjecutorToulmin().ejecutar(MapeadorJson.leer(e.config(), EjecutorToulmin.Config.class),
                MapeadorJson.leer(e.datos(), EjecutorToulmin.Entrada.class), Contextos.sinIa()).valor();
        UUID id = UUID.randomUUID();

        Document d = pintar(new RenderizadorV02(PLANTILLAS).render(Optional.of(id), "", valor, Modo.LECTURA));

        assertThat(d.getElementById("res-" + id).attr("data-patron")).isEqualTo("V02");
        assertThat(d.select("li.parte")).hasSize(6);
        assertThat(d.select("li.parte .chip").eachText()).containsExactly("completa", "completa", "completa", "sin fuente", "falta", "sin responder");
        assertThat(d.select("li[data-parte=calificador] .falta").text()).startsWith("¿Qué tan seguro estás?");
        assertThat(d.select("meter").attr("value")).isEqualTo("3");
        assertThat(d.select("label[for=" + d.select("meter").attr("id") + "]").text()).isEqualTo("Completitud 3 de 6");
        assertThat(d.select("a[href=/ejecuciones/" + id + "]").text()).isEqualTo("Abrir en la técnica");
    }

    // -------------------------------------------------------------------------------------------
    // V05
    // -------------------------------------------------------------------------------------------

    private static ResultadoFalacias falacias(String texto, String... confirmadas) {
        return new EjecutorFalacias(new FakeRepositorioEsquemas()).ejecutar(ConfigFalacias.porDefecto(),
                new EntradaFalacias(texto, java.util.List.of(confirmadas)), Contextos.sinIa()).valor();
    }

    @Test
    void v05_marca_el_fragmento_en_su_lugar_y_sin_confirmar_ofrece_la_etiqueta_solo_como_propuesta() {
        String texto = "Necesitamos más pan. El proveedor dice que su harina nueva rinde más, así que conviene cambiarnos.";
        Document d = pintar(new RenderizadorV05(PLANTILLAS).render(Optional.empty(), "borrador", falacias(texto), Modo.COMPLETO));

        assertThat(d.getElementById("res-V05-borrador").attr("data-patron")).isEqualTo("V05");
        assertThat(d.select("blockquote.texto-marcado").text()).contains("Necesitamos más pan.");
        assertThat(d.select("mark.marca-texto")).singleElement().satisfies(m -> {
            assertThat(m.classNames()).contains("propuesta");
            assertThat(m.text()).startsWith("M1 El proveedor dice");
        });
        Element marca = d.getElementById("res-V05-borrador-M1");
        assertThat(marca.text()).contains("esquema derrotable · sin confirmar", "¿Quien lo afirma tiene un interés propio en que le creas?",
                "Si confirmas que la pregunta falla, la etiqueta sería: apelación a una autoridad interesada.");
    }

    @Test
    void v05_confirmada_lo_dice_con_texto_y_sin_marcas_no_dice_que_el_texto_este_bien() {
        Document confirmada = pintar(new RenderizadorV05(PLANTILLAS).render(Optional.empty(), "a",
                falacias("Los que se oponen a las cámaras son los que tienen algo que esconder, así que no hay que escucharlos.", "M1"), Modo.COMPLETO));
        Document vacia = pintar(new RenderizadorV05(PLANTILLAS).render(Optional.empty(), "b",
                falacias("La junta se reúne el jueves a las 7."), Modo.COMPLETO));

        assertThat(confirmada.select("mark.confirmada")).hasSize(1);
        assertThat(confirmada.select(".etiqueta-falacia").text()).isEqualTo("Falacia: ad hominem circunstancial.");
        assertThat(vacia.select("mark")).isEmpty();
        assertThat(vacia.select(".tarjeta-resultado").text()).contains("Eso no prueba que el razonamiento sea bueno").doesNotContain("sin falacias");
    }
}
