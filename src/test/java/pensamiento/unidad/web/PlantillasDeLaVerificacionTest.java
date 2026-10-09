package pensamiento.unidad.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
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

import pensamiento.argdown.ParserArgdown;
import pensamiento.catalogo.MapeadorJson;
import pensamiento.expediente.GuardadoDeEjecuciones;
import pensamiento.flujos.BuscadorDePasajes;
import pensamiento.flujos.FichaDeVerificacion;
import pensamiento.nucleo.Documento;
import pensamiento.nucleo.Ejecucion;
import pensamiento.nucleo.EstandarPrueba;
import pensamiento.nucleo.Fragmento;
import pensamiento.nucleo.Json;
import pensamiento.nucleo.Resultado;
import pensamiento.nucleo.Uuid7;
import pensamiento.nucleo.puertos.Biblioteca;
import pensamiento.nucleo.puertos.EstadoIa;
import pensamiento.tecnicas.f1.EjecutorMapa;
import pensamiento.tecnicas.f4.EjecutorCraap;
import pensamiento.tecnicas.f4.EjecutorTriangulacion;
import pensamiento.testutil.builders.Contextos;
import pensamiento.testutil.fakes.FakeBiblioteca;
import pensamiento.testutil.fakes.FakeIa;
import pensamiento.testutil.fakes.FakeReloj;
import pensamiento.testutil.fakes.FakeRepositorioArgumentos;
import pensamiento.testutil.fakes.FakeRepositorioCambiosOpinion;
import pensamiento.testutil.fakes.FakeRepositorioEjecucion;
import pensamiento.testutil.fakes.FakeRepositorioEsquemas;
import pensamiento.testutil.fakes.FakeRepositorioEvidencias;
import pensamiento.testutil.fakes.FakeRepositorioPredicciones;
import pensamiento.testutil.fakes.FakeRepositorioVerificaciones;
import pensamiento.web.Pagina;
import pensamiento.web.biblioteca.VistaBiblioteca;
import pensamiento.web.verificacion.VistaFicha;
import pensamiento.web.verificacion.VistaFuente;

/**
 * Pruebas de plantilla del hito 6 (RNF-06, WCAG 2.1 AA): la lista y la búsqueda de la biblioteca, la ficha de verificación y
 * la ficha de fuente. Todo campo con su etiqueta, todo estado con texto, la subida con su progreso, los avisos con rol y
 * nunca "verdadero"; sin scripts ni estilos en línea.
 */
class PlantillasDeLaVerificacionTest {

    private static final TemplateEngine PLANTILLAS = TemplateEngine.createPrecompiled(ContentType.Html);
    private static final UUID YO = Contextos.DUENA_DE_LA_PANADERIA;
    private static final UUID INST = Contextos.INSTITUCION;
    private static final String MAPA = """
            [Sucursal]: Conviene abrir la segunda sucursal en el centro.
              + <Más ventas>: Con más gente pasando, se vende más. {peso: 3}
                + [Tráfico]: El centro tiene más tráfico peatonal que el barrio.
                + [Conversión]: Más tráfico da más ventas. #asumible""";

    private final FakeReloj reloj = new FakeReloj();
    private final FakeRepositorioEjecucion ejecuciones = new FakeRepositorioEjecucion();
    private final FakeRepositorioArgumentos argumentos = new FakeRepositorioArgumentos();
    private final FakeBiblioteca biblioteca = new FakeBiblioteca();
    private final FakeRepositorioEvidencias evidencias = new FakeRepositorioEvidencias(ejecuciones, biblioteca);
    private final FakeRepositorioVerificaciones verificaciones = new FakeRepositorioVerificaciones(ejecuciones);
    private final GuardadoDeEjecuciones guardado = new GuardadoDeEjecuciones(ejecuciones, argumentos, new FakeRepositorioPredicciones(ejecuciones),
            new FakeRepositorioCambiosOpinion(ejecuciones), evidencias);
    private final FichaDeVerificacion fichas = new FichaDeVerificacion(verificaciones, evidencias, argumentos, ejecuciones, new FakeRepositorioEsquemas(),
            biblioteca, guardado, reloj);
    private final FichaDeVerificacion.Configuracion config = new FichaDeVerificacion.Configuracion(
            new EjecutorTriangulacion.Config(2, EjecutorTriangulacion.Modo.PLANTILLAS), EjecutorCraap.Config.porDefecto());
    private final Pagina pagina = new Pagina("Verificación", Optional.empty(), new EstadoIa(true, List.of("qwen3:4b-instruct-2507-q4_K_M"), ""), "csrf");

    private static Document pintar(String plantilla, Map<String, Object> parametros) {
        StringOutput salida = new StringOutput();
        PLANTILLAS.render(plantilla, parametros, salida);
        return Jsoup.parse(salida.toString());
    }

    private static void sinScriptsNiEstilosYTodoEstadoConTexto(Document d) {
        assertThat(d.select("[style], script:not([src]), [onclick], [onchange]")).as("sin estilos ni scripts en línea").isEmpty();
        assertThat(d.select(".chip")).allSatisfy(c -> assertThat(c.text()).as("todo estado lleva texto").isNotBlank());
        assertThat(d.text().toLowerCase()).doesNotContain("verdadero", "verdadera", "falso");
    }

    private static void todoCampoConEtiqueta(Document d) {
        for (Element campo : d.select("input:not([type=hidden]):not([type=radio]):not([type=checkbox]), select, textarea")) {
            assertThat(d.select("label[for=" + campo.id() + "]")).as("etiqueta para #" + campo.id()).isNotEmpty();
        }
        for (Element casilla : d.select("input[type=radio], input[type=checkbox]")) {
            assertThat(casilla.parent().tagName()).as("radio o casilla dentro de su label").isEqualTo("label");
        }
    }

    private UUID trafico() {
        Resultado<?> r = new EjecutorMapa(new ParserArgdown()).ejecutar(new EjecutorMapa.Config(pensamiento.tecnicas.f1.ResultadoMapa.Direccion.ARRIBA_ABAJO,
                true, false, EstandarPrueba.PREPONDERANCIA), new EjecutorMapa.Entrada(MAPA), Contextos.sinIa());
        guardado.guardar(new Ejecucion(Uuid7.en(reloj.ahora()), YO, INST, EjecutorMapa.ID, 1, Optional.empty(), Json.VACIO, Json.VACIO,
                MapeadorJson.escribir(r.valor()), r.resumen(), Optional.empty(), "clave", reloj.ahora()), r);
        return r.afirmaciones().stream().filter(a -> a.texto().startsWith("El centro")).findFirst().orElseThrow().afirmacionId();
    }

    private Documento conteo() {
        Documento d = biblioteca.crear(YO, INST, new Biblioteca.NuevoDocumento(UUID.randomUUID(), "conteo-peatonal-municipio-2025.pdf", Documento.Tipo.PDF,
                "hash", "%PDF-1.4".getBytes(StandardCharsets.ISO_8859_1)));
        biblioteca.indexar(YO, d.id(), List.of(new Fragmento.Nuevo(0, "En el centro pasan 1.200 personas por hora; en el barrio, 300.", Optional.of(2))),
                Optional.of(3));
        return biblioteca.porId(YO, d.id()).orElseThrow();
    }

    @Test
    void la_biblioteca_tiene_la_subida_con_su_progreso_y_cada_documento_dice_su_estado_con_texto() {
        conteo();
        biblioteca.crear(YO, INST, new Biblioteca.NuevoDocumento(UUID.randomUUID(), "acta.txt", Documento.Tipo.TEXTO, "hash-acta",
                "Acta.".getBytes(StandardCharsets.UTF_8)));
        Document d = pintar("biblioteca.jte", Map.of("pagina", pagina, "v", new VistaBiblioteca(YO, biblioteca.visibles(YO))));

        sinScriptsNiEstilosYTodoEstadoConTexto(d);
        todoCampoConEtiqueta(d);
        assertThat(d.select("#form-subida").attr("hx-encoding")).isEqualTo("multipart/form-data");
        assertThat(d.select("#form-subida progress[data-progreso]")).hasSize(1);
        assertThat(d.select("label[for=progreso-subida]").text()).isEqualTo("Subida");
        assertThat(d.select("li[data-estado=indexado] .chip").text()).isEqualTo("vectorizando 0%");
        assertThat(d.select("li[data-estado=en_proceso] .chip").text()).isEqualTo("indexando");
        assertThat(d.select("#lista-documentos").attr("hx-trigger")).as("se refresca mientras algo se procesa").isEqualTo("every 3s");
        assertThat(d.select("form[role=search] input[type=search]").attr("id")).isEqualTo("consulta");
    }

    @Test
    void la_busqueda_desde_la_ficha_ofrece_usar_el_pasaje_y_etiquetarlo_y_dice_como_busco() {
        conteo();
        UUID afirmacion = trafico();
        BuscadorDePasajes.Busqueda b = new BuscadorDePasajes(biblioteca, new FakeIa(), Duration.ofSeconds(1)).buscar(YO, "centro barrio", true);
        Document d = pintar("fragmentos/biblioteca/resultados.jte", Map.of("b", b, "afirmacion", afirmacion.toString()));

        sinScriptsNiEstilosYTodoEstadoConTexto(d);
        assertThat(d.select("#resultados-busqueda").attr("aria-live")).isEqualTo("polite");
        assertThat(d.select(".modo-busqueda").text()).startsWith("Búsqueda por palabras");
        assertThat(d.select("li[data-fragmento] .origen-pasaje").text()).contains("conteo-peatonal-municipio-2025.pdf, p. 2");
        assertThat(d.select("li[data-fragmento] a").text()).isEqualTo("Usar como evidencia");
        assertThat(d.select("li[data-fragmento] button").text()).isEqualTo("Etiquetar con el modelo");
    }

    @Test
    void la_ficha_de_verificacion_tiene_sus_cuatro_pasos_con_encabezados_y_el_veredicto_con_medidor_y_firma() {
        UUID afirmacion = trafico();
        fichas.registrarFuente(YO, INST, afirmacion, new FichaDeVerificacion.BorradorFuente("Conteo peatonal del municipio", null, "2025-03-15",
                "primaria", "no_aplica", "municipio", true, true, 4, 5, 4, 4, 3, "El municipio.", "", "", "apoya", "Pasan 1.200 por hora.", "", "usuario",
                ""), config);
        Document d = pintar("verificacion.jte", Map.of("pagina", pagina, "f",
                new VistaFicha(fichas.abrir(YO, afirmacion, config).orElseThrow(), "", "clave", true)));

        sinScriptsNiEstilosYTodoEstadoConTexto(d);
        todoCampoConEtiqueta(d);
        assertThat(d.select("#ficha h2").eachText()).contains("1 · Tipo de afirmación", "2 · Preguntas críticas", "3 · Evidencia registrada", "4 · Veredicto",
                "Estándar de prueba de los argumentos (R04)");
        assertThat(d.select("#ficha section[aria-labelledby]")).allSatisfy(s -> assertThat(d.getElementById(s.attr("aria-labelledby"))).isNotNull());
        assertThat(d.select("fieldset legend").eachText()).contains("Qué tipo de afirmación es (T17 · Hecho, inferencia, juicio)");
        assertThat(d.select("[data-estado-calculado]").text()).isEqualTo("en verificación");
        assertThat(d.select(".veredicto-ficha meter")).allSatisfy(m -> assertThat(d.select("label[for=" + m.id() + "]").text()).startsWith("Fuerza neta"));
        assertThat(d.select("li[data-evidencia] .fuerza").text()).isEqualTo("fuerza 6 (R01)");
        assertThat(d.select(".r04 [data-aceptable]").attr("data-aceptable")).isEqualTo("false");
        assertThat(d.select("aside.panel-biblioteca").attr("aria-labelledby")).isEqualTo("panel-biblioteca-titulo");
    }

    @Test
    void la_ficha_de_fuente_tiene_cada_campo_con_su_etiqueta_y_el_panel_de_lo_que_aporta_con_rol_de_estado() {
        UUID afirmacion = trafico();
        Documento d0 = conteo();
        Fragmento f = biblioteca.fragmentos(YO, d0.id()).getFirst();
        VistaFuente v = VistaFuente.nueva(fichas.abrir(YO, afirmacion, config).orElseThrow(), f.id().toString(), "apoya", "apoya",
                "Da 1.200 contra 300.", true, EjecutorCraap.Config.porDefecto());
        v.desdeBiblioteca(biblioteca.cita(YO, f.id()).orElseThrow());
        Document d = pintar("fuente.jte", Map.of("pagina", pagina, "v", v));

        sinScriptsNiEstilosYTodoEstadoConTexto(d);
        todoCampoConEtiqueta(d);
        assertThat(d.select("#f-pasaje").text()).isEqualTo("En el centro pasan 1.200 personas por hora; en el barrio, 300.");
        assertThat(d.select(".propuesta .chip").text()).isEqualTo("propuesta del modelo");
        assertThat(d.select("input[name=postura][value=apoya]").hasAttr("checked")).isTrue();
        assertThat(d.select("#previa").attr("aria-live")).isEqualTo("polite");
        assertThat(d.select("fieldset legend").eachText()).contains("Datos de la fuente", "SIFT", "Independencia y acceso (lectura lateral)",
                "CRAAP (0 a 5 cada criterio; los cinco o ninguno)");
    }
}
