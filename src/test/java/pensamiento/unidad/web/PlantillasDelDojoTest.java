package pensamiento.unidad.web;

import static org.assertj.core.api.Assertions.assertThat;

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
import pensamiento.flujos.DojoDeRazonamiento;
import pensamiento.nucleo.BancoDojo;
import pensamiento.nucleo.NivelBloom;
import pensamiento.nucleo.puertos.EstadoIa;
import pensamiento.tecnicas.f8.EjecutorBloom;
import pensamiento.tecnicas.f8.EjecutorRepeticion;
import pensamiento.tecnicas.f8.TemaDojo;
import pensamiento.testutil.builders.Contextos;
import pensamiento.testutil.fakes.FakeReloj;
import pensamiento.testutil.fakes.FakeRepositorioDojo;
import pensamiento.web.Pagina;
import pensamiento.web.dojo.ControladorDojo;
import pensamiento.web.patrones.Modo;
import pensamiento.web.patrones.RenderizadoresF8;

/**
 * Pruebas de plantilla de P19 · Dojo de razonamiento (RNF-06, RNF-09): el reto es un fragmento htmx con su clave contra el
 * doble clic, las opciones son radios dentro de su etiqueta en un fieldset con leyenda, el error llega con role=alert y queda
 * descrito, la respuesta dice el resultado con texto y lleva el foco a "Siguiente reto", y el filtro marca el tema actual.
 */
class PlantillasDelDojoTest {

    private static final TemplateEngine PLANTILLAS = TemplateEngine.createPrecompiled(ContentType.Html);
    private static final BancoDojo BANCO = new CatalogoJson().bancoDojo();
    private static final DojoDeRazonamiento.Configuracion CONFIG = new DojoDeRazonamiento.Configuracion(
            new EjecutorBloom.Config(List.of(NivelBloom.values()), true, 10), new EjecutorRepeticion.Config(10, "2.5"));
    private final Pagina pagina = new Pagina("Dojo", Optional.empty(), new EstadoIa(false, List.of(), "apagado"), "csrf");
    private final FakeRepositorioDojo repositorio = new FakeRepositorioDojo();
    private final DojoDeRazonamiento dojo = new DojoDeRazonamiento(repositorio, BANCO, new FakeReloj());

    private static Document pintar(String plantilla, Map<String, Object> parametros) {
        StringOutput salida = new StringOutput();
        PLANTILLAS.render(plantilla, parametros, salida);
        return Jsoup.parse(salida.toString());
    }

    private static void accesible(Document d) {
        assertThat(d.select("[style], script:not([src]), [onclick], [onchange]")).as("sin estilos ni scripts en línea").isEmpty();
        assertThat(d.select(".chip")).allSatisfy(c -> assertThat(c.text()).as("todo estado lleva texto").isNotBlank());
        for (Element campo : d.select("input:not([type=hidden]):not([type=radio]), select, textarea")) {
            assertThat(d.select("label[for=" + campo.id() + "]")).as("etiqueta para #" + campo.id()).isNotEmpty();
        }
        assertThat(d.select("input[type=radio]")).allSatisfy(r -> assertThat(r.parent().tagName()).isEqualTo("label"));
    }

    private ControladorDojo.VistaDojo vista(Optional<TemaDojo> tema, String error, Optional<DojoDeRazonamiento.Respuesta> respuesta) {
        DojoDeRazonamiento.Pantalla p = dojo.pantalla(Contextos.DUENA_DE_LA_PANADERIA, tema, Optional.empty(), CONFIG);
        List<ControladorDojo.Tema> temas = List.of(new ControladorDojo.Tema("", "Todos", tema.isEmpty()),
                new ControladorDojo.Tema("falacias", "Falacias", tema.equals(Optional.of(TemaDojo.FALACIAS))));
        return new ControladorDojo.VistaDojo(p, temas, "clave-del-formulario", error, respuesta);
    }

    @Test
    void el_reto_de_opciones_es_un_fragmento_htmx_con_su_clave_y_radios_dentro_de_su_etiqueta() {
        Document d = pintar("dojo.jte", Map.of("pagina", pagina, "v", vista(Optional.of(TemaDojo.FALACIAS), "", Optional.empty())));

        accesible(d);
        Element form = d.selectFirst("#reto form");
        assertThat(form.attr("hx-post")).isEqualTo("/dojo/retos/generalizacion-i1");
        assertThat(form.attr("hx-target")).isEqualTo("#reto");
        assertThat(form.select("input[name=_clave]").val()).isEqualTo("clave-del-formulario");
        assertThat(form.select("input[name=tema]").val()).isEqualTo("falacias");
        assertThat(form.select("fieldset legend").text()).isEqualTo("Elige una opción");
        assertThat(form.select("input[type=radio][name=respuesta]")).hasSize(4);
        assertThat(d.select("#reto-titulo").text()).isEqualTo(BANCO.reto("generalizacion-i1").orElseThrow().pregunta());
        assertThat(d.select("nav.temas-dojo a[aria-current=page]").text()).isEqualTo("Falacias");
        assertThat(d.select(".estado-dojo").text()).contains("racha 0", "Hoy: 0 de 10 retos");
    }

    @Test
    void el_error_vuelve_con_role_alert_y_queda_descrito_en_el_fieldset() {
        Document d = pintar("fragmentos/dojo/reto.jte", Map.of("pagina", pagina, "v", vista(Optional.empty(), "Elige una opción.", Optional.empty())));

        assertThat(d.select("#error-reto").attr("role")).isEqualTo("alert");
        assertThat(d.select("#error-reto").text()).isEqualTo("error Elige una opción.");
        assertThat(d.select("fieldset").attr("aria-describedby")).isEqualTo("error-reto");
    }

    @Test
    void la_respuesta_dice_el_resultado_con_texto_y_lleva_el_foco_a_siguiente_reto() {
        DojoDeRazonamiento.Respuesta r = dojo.responder(Contextos.DUENA_DE_LA_PANADERIA, Contextos.INSTITUCION, "generalizacion-i1", "a",
                UUID.randomUUID().toString(), CONFIG);
        Document d = pintar("fragmentos/dojo/respuesta.jte", Map.of("pagina", pagina, "v", vista(Optional.empty(), "", Optional.of(r))));

        accesible(d);
        assertThat(d.select("#reto").attr("data-resultado")).isEqualTo("error");
        assertThat(d.select("#respuesta-titulo").text()).isEqualTo("error No es esa: la respuesta es «Generalización apresurada».");
        assertThat(d.select(".repaso-reto").text()).isEqualTo("Siguiente repaso: mañana.");
        assertThat(d.select("a[data-accion=siguiente]").hasAttr("autofocus")).isTrue();
        assertThat(d.select("a[data-accion=siguiente]").attr("href")).isEqualTo("/dojo");
    }

    @Test
    void el_reto_de_crear_pide_tu_version_con_su_etiqueta_y_la_respuesta_muestra_cada_chequeo() {
        BancoDojo.Reto reto = BANCO.reto("pendiente_resbaladiza-c1").orElseThrow();
        DojoDeRazonamiento.Respuesta r = dojo.responder(Contextos.DUENA_DE_LA_PANADERIA, Contextos.INSTITUCION, reto.id(), reto.respuestaModelo(),
                UUID.randomUUID().toString(), CONFIG);
        Document d = pintar("fragmentos/dojo/respuesta.jte", Map.of("pagina", pagina, "v", vista(Optional.empty(), "", Optional.of(r))));

        assertThat(d.select("#respuesta-titulo").text()).isEqualTo("acierto Tu versión cumple la rúbrica.");
        assertThat(d.select("ul.chequeos-rubrica li")).hasSize(reto.rubrica().size()).allSatisfy(li -> assertThat(li.text()).startsWith("cumple"));
        assertThat(d.text()).contains("La rúbrica mira palabras, no el sentido.");
    }

    @Test
    void el_progreso_tiene_el_calendario_y_un_nivel_por_tema() {
        DojoDeRazonamiento.Progreso p = dojo.progreso(Contextos.DUENA_DE_LA_PANADERIA, CONFIG);
        var v = new ControladorDojo.VistaProgreso(p.temas().stream().map(t -> RenderizadoresF8.Bloom.vista(Optional.empty(), "dojo-" + t.tema().clave(), t,
                Modo.COMPLETO)).toList(), RenderizadoresF8.Repeticion.vista(Optional.empty(), "dojo", p.calendario(), Modo.COMPLETO), p.racha());
        Document d = pintar("dojo-progreso.jte", Map.of("pagina", pagina, "v", v));

        accesible(d);
        assertThat(d.select("[data-patron=V13c]")).hasSize(1);
        assertThat(d.select("[data-patron=V13b]")).hasSize(4);
        assertThat(d.select("[data-patron=V13b] li[data-nivel=identificar] .chip").eachText()).containsOnly("en curso");
    }
}
