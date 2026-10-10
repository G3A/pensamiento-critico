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
import pensamiento.tecnicas.f8.EjecutorBloom;
import pensamiento.tecnicas.f8.EjecutorCambiosOpinion;
import pensamiento.tecnicas.f8.EjecutorDiarioRazonamiento;
import pensamiento.tecnicas.f8.EjecutorReflexion;
import pensamiento.tecnicas.f8.EjecutorRepeticion;
import pensamiento.testutil.builders.Contextos;
import pensamiento.web.patrones.Modo;
import pensamiento.web.patrones.RenderizadorResultado;
import pensamiento.web.patrones.Renderizadores;
import pensamiento.web.patrones.RenderizadoresF8;

/**
 * Pruebas de plantilla del hito 7 (RNF-06): cada ejemplo de T45 a T49, pintado con su renderizador real a través de
 * Renderizadores, cumple el contrato del fragmento de patrón (raíz res-{id}, data-patron del catálogo, título enlazado) y la
 * accesibilidad: estados con texto, barras con su número visible, tarjeta con role=status, sin style ni fill.
 */
class PlantillasDelHito7Test {

    private static final TemplateEngine PLANTILLAS = TemplateEngine.createPrecompiled(ContentType.Html);
    private static final CatalogoJson CATALOGO = new CatalogoJson();
    private static final Renderizadores RENDERIZADORES = new Renderizadores(List.<RenderizadorResultado<?>>of(
            new RenderizadoresF8.DiarioRazonamiento(PLANTILLAS), new RenderizadoresF8.CambiosOpinion(PLANTILLAS),
            new RenderizadoresF8.Reflexion(PLANTILLAS), new RenderizadoresF8.Bloom(PLANTILLAS), new RenderizadoresF8.Repeticion(PLANTILLAS)));
    private static final List<Ejecutor<?, ?, ?>> EJECUTORES = List.of(new EjecutorDiarioRazonamiento(), new EjecutorCambiosOpinion(),
            new EjecutorReflexion(), new EjecutorBloom(), new EjecutorRepeticion());
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

    private static Document pintar(Ejecutor<?, ?, ?> e, Ejemplo ej, Modo modo) {
        StringOutput salida = new StringOutput();
        RENDERIZADORES.render(tecnica(e), Optional.of(ID), "", valor(e, ej), modo).writeTo(salida);
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
        assertThat(d.select("meter")).allSatisfy(m -> assertThat(d.select("label[for=" + m.id() + "]").text()).as("barra con etiqueta").isNotBlank());
        assertThat(d.select("a[href=/ejecuciones/" + ID + "]").text()).isEqualTo("Abrir en la técnica");
        assertThat(d.text().toLowerCase()).doesNotContain("válido", "verdadero", "correcto", "probado");
    }

    @Test
    void t45_pinta_cada_semana_con_su_titulo_su_resumen_y_la_tecnica_citada_con_su_nombre() {
        Ejemplo semana = CATALOGO.ejemplosDe(EjecutorDiarioRazonamiento.ID).getFirst();
        Document d = pintar(new EjecutorDiarioRazonamiento(), semana, Modo.COMPLETO);

        assertThat(d.select("h4.subtitulo-resultado").eachText()).containsExactly("Semana del 5 al 11 de octubre de 2026",
                "Semana del 28 de septiembre al 4 de octubre de 2026");
        assertThat(d.select(".resumen-grupo").eachText()).containsExactly("3 ejecuciones, 1 expediente, 1 cambio de opinión.",
                "3 ejecuciones, 2 expedientes, 1 cambio de opinión.");
        assertThat(d.select("ol.linea-tiempo li").first().text())
                .isEqualTo("7 de octubre de 2026 · T32 · Diario de decisiones — Abrir en la terminal, no en el centro · expediente «La segunda sucursal»");
        assertThat(d.select("time").first().attr("datetime")).isEqualTo("2026-10-07");
    }

    @Test
    void t46_pinta_el_ano_en_barras_con_su_numero_y_las_posturas_sin_revisar() {
        Ejemplo panaderia = CATALOGO.ejemplosDe(EjecutorCambiosOpinion.ID).getFirst();
        Document d = pintar(new EjecutorCambiosOpinion(), panaderia, Modo.COMPLETO);

        assertThat(d.select("[data-barra]")).hasSize(6);
        assertThat(d.select("[data-barra=evidencia] label").text()).isEqualTo("Evidencia nueva: 2");
        assertThat(d.select("[data-barra=presion] meter").attr("value")).isEqualTo("0");
        assertThat(d.select("ul.lista-aparte li").eachText())
                .containsExactly("«El pan de masa madre no se vende en este barrio» · hace 13 meses (desde el 14 de agosto de 2025)");
        assertThat(d.select("ol.linea-tiempo li").first().text())
                .isEqualTo("3 de octubre de 2026 · «Conviene abrir la segunda sucursal en el centro este año» · 80% → 45% · evidencia nueva · T22 · Triangulación");
    }

    @Test
    void t48_y_t49_pintan_los_niveles_y_el_calendario_con_texto() {
        Document bloom = pintar(new EjecutorBloom(), CATALOGO.ejemplosDe(EjecutorBloom.ID).getFirst(), Modo.COMPLETO);
        assertThat(bloom.select("li.nivel-bloom .chip").eachText()).containsExactly("dominado", "en curso", "bloqueado", "bloqueado");
        assertThat(bloom.select("li[data-nivel=analizar] label").text()).isEqualTo("Analizar · 2 de 3 aciertos · 3 intentos");

        Document calendario = pintar(new EjecutorRepeticion(), CATALOGO.ejemplosDe(EjecutorRepeticion.ID).getFirst(), Modo.COMPLETO);
        assertThat(calendario.select("ol.calendario li")).hasSize(7);
        assertThat(calendario.select("ol.calendario li.dia-hoy").text()).isEqualTo("mié 7 1 repaso hoy");
        assertThat(calendario.select("ul.conceptos-repaso li").first().text())
                .isEqualTo("hoy Rastrea el origen (Fuentes) · 1 repaso · facilidad 2,60 · intervalo 1 día");
    }
}
