package pensamiento.unidad.web;

import static org.assertj.core.api.Assertions.assertThat;

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
import pensamiento.catalogo.Intencion;
import pensamiento.catalogo.MapeadorJson;
import pensamiento.nucleo.Ejemplo;
import pensamiento.nucleo.Ejecucion;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Json;
import pensamiento.nucleo.Tecnica;
import pensamiento.tecnicas.f5.ConfigAch;
import pensamiento.tecnicas.f5.EjecutorAch;
import pensamiento.tecnicas.f5.EntradaAch;
import pensamiento.tecnicas.f5.ResultadoAch;
import pensamiento.testutil.builders.Contextos;
import pensamiento.web.ControladorCatalogo;
import pensamiento.web.formulario.Campo;
import pensamiento.web.formulario.ConstructorVista;
import pensamiento.web.formulario.LenguajeCampos;
import pensamiento.web.formulario.VistaCampo;
import pensamiento.web.patrones.Modo;
import pensamiento.web.patrones.V03a;
import pensamiento.web.tecnicas.ControladorTecnicas;
import pensamiento.web.tecnicas.VistaFormulario;

/**
 * Pruebas de plantilla (RNF-06): cada fragmento nuevo se pinta con datos del catálogo y se revisan con jsoup sus
 * identificadores fijos, sus atributos hx-* y sus atributos ARIA. Sin servidor: las plantillas precompiladas.
 */
class PlantillasTest {

    private static final TemplateEngine PLANTILLAS = TemplateEngine.createPrecompiled(ContentType.Html);
    private static final CatalogoJson CATALOGO = new CatalogoJson();
    private static final Tecnica T28 = CATALOGO.tecnicas().stream().filter(t -> t.id().equals(EjecutorAch.ID)).findFirst().orElseThrow();

    private static Document pintar(String plantilla, Map<String, Object> parametros) {
        StringOutput salida = new StringOutput();
        PLANTILLAS.render(plantilla, parametros, salida);
        return Jsoup.parseBodyFragment(salida.toString());
    }

    private static ResultadoAch resultado(int ejemplo) {
        Ejemplo e = CATALOGO.ejemplosDe(EjecutorAch.ID).get(ejemplo);
        return new EjecutorAch().ejecutar(MapeadorJson.leer(e.config(), ConfigAch.class), MapeadorJson.leer(e.datos(), EntradaAch.class),
                Contextos.sinIa()).valor();
    }

    // -------------------------------------------------------------------------------------------
    // Patrón V03a
    // -------------------------------------------------------------------------------------------

    @Test
    void v03a_tiene_la_raiz_del_contrato_del_patron_y_cada_celda_con_texto_ademas_de_color() {
        UUID id = UUID.fromString("01a118b3-94cc-76f0-afe1-daab7018e19a");
        Document d = pintar("tag/v/v03a.jte", Map.of("v", new V03a(Optional.of(id), "", resultado(0), Modo.COMPLETO)));

        Element raiz = d.getElementById("res-" + id);
        assertThat(raiz).isNotNull();
        assertThat(raiz.attr("data-patron")).isEqualTo("V03a");
        assertThat(raiz.attr("aria-labelledby")).isEqualTo("res-" + id + "-titulo");
        assertThat(d.getElementById("res-" + id + "-titulo")).isNotNull();
        assertThat(d.select("table.matriz caption").text()).contains("C consistente");
        assertThat(d.select("thead th[scope=col]")).hasSize(4);
        assertThat(d.select("tbody th[scope=row]")).hasSize(4);
        assertThat(d.select("td.celda")).hasSize(12).allSatisfy(td -> {
            assertThat(td.select(".marca").text()).isNotBlank();
            assertThat(td.select(".palabra").text()).isIn("consistente", "inconsistente", "neutral");
        });
        assertThat(d.select("tfoot td").eachText()).containsExactly("0", "5", "4");
        assertThat(d.select(".tabla-desplazable").attr("role")).isEqualTo("region");
        assertThat(d.select(".tabla-desplazable").attr("tabindex")).isEqualTo("0");
        assertThat(d.select(".tarjeta-resultado").attr("role")).isEqualTo("status");
        assertThat(d.select(".tarjeta-resultado").text()).contains("Menos refutada: H1").contains("ACH elimina, no confirma")
                .doesNotContain("confirmada", "verdadera");
        assertThat(d.select("a[href=/ejecuciones/" + id + "]")).as("en modo completo no hay enlace de lectura").isEmpty();
    }

    @Test
    void v03a_en_lectura_ofrece_abrir_en_la_tecnica_y_la_escala_numerica_lleva_signo_y_palabra() {
        UUID id = UUID.randomUUID();
        Document d = pintar("tag/v/v03a.jte", Map.of("v", new V03a(Optional.of(id), "", resultado(2), Modo.LECTURA)));

        assertThat(d.select("[data-patron=V03a]").attr("data-modo")).isEqualTo("lectura");
        assertThat(d.select("a[href=/ejecuciones/" + id + "]").text()).isEqualTo("Abrir en la técnica");
        assertThat(d.select("td.celda .marca").eachText()).contains("+2", "-2", "0");
        assertThat(d.select("td.celda .palabra").eachText()).contains("muy consistente", "muy inconsistente");
        assertThat(d.select(".tarjeta-resultado").text()).contains("Ninguna evidencia la contradice, pero eso no la confirma");
    }

    @Test
    void v03a_sin_guardar_usa_un_identificador_de_borrador_y_en_empate_lo_dice() {
        Document d = pintar("tag/v/v03a.jte", Map.of("v", new V03a(Optional.empty(), "borrador", resultado(1), Modo.COMPLETO)));
        assertThat(d.getElementById("res-T28-borrador")).isNotNull();
        assertThat(d.select(".titular").text()).startsWith("Empate: H1 y H2");
        assertThat(d.select(".siguientes-pasos li").eachText())
                .contains("H2 no tiene ninguna evidencia a favor: busca una evidencia que la distinga de las demás.");
    }

    // -------------------------------------------------------------------------------------------
    // Lenguaje de campos: los ocho tipos
    // -------------------------------------------------------------------------------------------

    private static Campo campo(String nombre, Campo.Tipo tipo, List<Campo.Opcion> opciones, List<Campo> sub) {
        return new Campo(nombre, tipo, "Etiqueta de " + nombre, "Ayuda de " + nombre, true, 1, 7, 300, opciones, "F", "fila",
                null, null, null, null, null, null, sub);
    }

    private static final List<Campo.Opcion> CUATRO = List.of(new Campo.Opcion("a", "A"), new Campo.Opcion("b", "B"),
            new Campo.Opcion("c", "C"), new Campo.Opcion("d", "D"));
    private static final List<Campo.Opcion> SEIS = List.of(new Campo.Opcion("1", "uno"), new Campo.Opcion("2", "dos"),
            new Campo.Opcion("3", "tres"), new Campo.Opcion("4", "cuatro"), new Campo.Opcion("5", "cinco"), new Campo.Opcion("6", "seis"));

    private static Document pintarCampos(List<Campo> campos, Map<String, Object> valores, Map<String, String> errores) {
        List<VistaCampo> vistas = ConstructorVista.para("PRUEBA", campos, valores, Map.of(), errores, "/tecnicas/T28/formulario")
                .construir(campos, valores, "", "");
        StringBuilder html = new StringBuilder();
        for (VistaCampo v : vistas) {
            StringOutput salida = new StringOutput();
            PLANTILLAS.render("tag/campo/campo.jte", Map.of("c", v), salida);
            html.append(salida);
        }
        return Jsoup.parseBodyFragment(html.toString());
    }

    @Test
    void los_ocho_tipos_de_campo_se_pintan_con_su_control_y_su_etiqueta() {
        List<Campo> campos = List.of(
                campo("interruptor", Campo.Tipo.BOOLEANO, List.of(), List.of()),
                campo("niveles", Campo.Tipo.ENTERO, List.of(), List.of()),
                campo("pocas", Campo.Tipo.ENUMERACION, CUATRO, List.of()),
                campo("muchas", Campo.Tipo.ENUMERACION, SEIS, List.of()),
                campo("tipos", Campo.Tipo.CONJUNTO, CUATRO, List.of()),
                campo("orden", Campo.Tipo.LISTA_ORDENADA, CUATRO, List.of()),
                campo("decision", Campo.Tipo.TEXTO, List.of(), List.of()),
                campo("revision", Campo.Tipo.FECHA, List.of(), List.of()),
                campo("criterios", Campo.Tipo.FILAS, List.of(), List.of(campo("texto", Campo.Tipo.TEXTO, List.of(), List.of()))));
        Map<String, Object> valores = new LinkedHashMap<>();
        valores.put("interruptor", true);
        valores.put("niveles", 5);
        valores.put("criterios", List.of(Map.of("texto", "Tráfico"), Map.of("texto", "Alquiler")));
        Document d = pintarCampos(campos, valores, Map.of());

        assertThat(d.select("[data-campo]").eachAttr("data-campo"))
                .containsExactly("booleano", "entero", "enumeracion", "enumeracion", "conjunto", "lista_ordenada", "texto", "fecha", "filas", "texto", "texto");
        assertThat(d.select("#PRUEBA-interruptor").attr("role")).isEqualTo("switch");
        assertThat(d.select("#PRUEBA-interruptor").hasAttr("checked")).isTrue();
        assertThat(d.select("input[type=hidden][name=interruptor]").val()).isEqualTo("false");
        assertThat(d.select("#PRUEBA-niveles").attr("type")).isEqualTo("number");
        assertThat(d.select("input[type=range][data-espejo=PRUEBA-niveles]")).hasSize(1);
        assertThat(d.select("output[for=PRUEBA-niveles]").text()).isEqualTo("5");
        assertThat(d.select("fieldset#PRUEBA-pocas input[type=radio]")).as("4 opciones o menos: radios").hasSize(4);
        assertThat(d.select("select#PRUEBA-muchas option")).as("más de 4: select").hasSize(7);
        assertThat(d.select("fieldset#PRUEBA-tipos input[type=checkbox]")).hasSize(4);
        assertThat(d.select("#PRUEBA-orden button")).hasSize(8);
        assertThat(d.select("#PRUEBA-orden button").first().hasAttr("disabled")).as("no se sube el primero").isTrue();
        assertThat(d.select("#PRUEBA-orden button").first().attr("hx-post")).isEqualTo("/tecnicas/T28/formulario");
        assertThat(d.select("textarea#PRUEBA-decision")).as("largo máximo 300: textarea").hasSize(1);
        assertThat(d.select("#PRUEBA-revision").attr("type")).isEqualTo("date");
        for (String id : List.of("PRUEBA-niveles", "PRUEBA-muchas", "PRUEBA-decision", "PRUEBA-revision")) {
            assertThat(d.select("label[for=" + id + "]")).as("etiqueta de " + id).hasSize(1);
        }
    }

    @Test
    void las_filas_tienen_quitar_por_fila_y_anadir_como_hx_post_que_reemplaza_el_formulario() {
        List<Campo> campos = List.of(campo("criterios", Campo.Tipo.FILAS, List.of(), List.of(campo("texto", Campo.Tipo.TEXTO, List.of(), List.of()))));
        Map<String, Object> valores = Map.of("criterios", List.of(Map.of("texto", "Tráfico"), Map.of("texto", "Alquiler")));
        Document d = pintarCampos(campos, valores, Map.of());

        assertThat(d.select("fieldset#PRUEBA-criterios legend").text()).contains("(2 de 7)");
        assertThat(d.select("li.fila")).hasSize(2);
        assertThat(d.select("#PRUEBA-criterios-0-texto").attr("name")).isEqualTo("criterios[0].texto");
        Element anadir = d.selectFirst("button.anadir-fila");
        assertThat(anadir.text()).isEqualTo("Añadir fila");
        assertThat(anadir.attr("hx-post")).isEqualTo("/tecnicas/T28/formulario");
        assertThat(anadir.attr("hx-target")).isEqualTo("#form-PRUEBA");
        assertThat(anadir.attr("hx-swap")).isEqualTo("outerHTML");
        assertThat(anadir.attr("hx-include")).isEqualTo("#form-PRUEBA");
        assertThat(anadir.attr("hx-vals")).isEqualTo("{\"_accion\":\"anadir:criterios\"}");
        assertThat(d.select("button.quitar-fila").eachAttr("hx-vals"))
                .containsExactly("{\"_accion\":\"quitar:criterios:0\"}", "{\"_accion\":\"quitar:criterios:1\"}");
        assertThat(d.select("button.quitar-fila").eachAttr("aria-label")).containsExactly("Quitar F1", "Quitar F2");
    }

    @Test
    void un_error_queda_junto_al_campo_y_el_campo_lo_anuncia_con_aria_describedby() {
        List<Campo> campos = List.of(campo("decision", Campo.Tipo.TEXTO, List.of(), List.of()));
        Document d = pintarCampos(campos, Map.of(), Map.of("decision", "Etiqueta de decision: este campo es obligatorio."));

        Element control = d.getElementById("PRUEBA-decision");
        assertThat(control.attr("aria-invalid")).isEqualTo("true");
        assertThat(control.attr("aria-describedby")).isEqualTo("PRUEBA-decision-ayuda PRUEBA-decision-error");
        assertThat(d.getElementById("PRUEBA-decision-error").attr("role")).isEqualTo("alert");
        assertThat(d.getElementById("PRUEBA-decision-error").text()).contains("este campo es obligatorio");
        assertThat(d.getElementById("PRUEBA-decision-ayuda").text()).isEqualTo("Ayuda de decision");
    }

    // -------------------------------------------------------------------------------------------
    // Formulario de T28 y ficha de tres pestañas
    // -------------------------------------------------------------------------------------------

    private static VistaFormulario formularioT28(Map<String, String> errores) {
        Ejemplo ventas = CATALOGO.ejemplosDe(EjecutorAch.ID).getFirst();
        return VistaFormulario.de(T28, LenguajeCampos.campos(T28.esquemaConfig()), LenguajeCampos.campos(T28.esquemaEntrada()),
                LenguajeCampos.mapa(ventas.config()), LenguajeCampos.mapa(ventas.datos()), errores, "clave-1", "tu configuración");
    }

    @Test
    void el_formulario_de_t28_evalua_y_guarda_con_htmx_sin_doble_envio() {
        Document d = pintar("fragmentos/ficha/formulario.jte", Map.of("f", formularioT28(Map.of()), "oob", false));

        Element form = d.getElementById("form-T28");
        assertThat(form.attr("hx-post")).isEqualTo("/tecnicas/T28/evaluar");
        assertThat(form.attr("hx-target")).isEqualTo("#resultado-T28");
        assertThat(form.attr("hx-sync")).isEqualTo("this:replace");
        assertThat(form.hasAttr("hx-swap-oob")).isFalse();
        assertThat(d.select("input[name=_clave]").attr("id")).isEqualTo("clave-T28");
        assertThat(d.select("input[type=hidden][name^=config.]").eachAttr("name"))
                .containsExactly("config.escala", "config.maxHipotesis", "config.pesosActivos");
        Element guardar = d.selectFirst("button[data-accion=guardar]");
        assertThat(guardar.attr("hx-post")).isEqualTo("/tecnicas/T28/ejecuciones");
        assertThat(guardar.attr("hx-disabled-elt")).isEqualTo("this");
        assertThat(guardar.attr("hx-sync")).isEqualTo("closest form:drop");
        assertThat(d.selectFirst("button[data-accion=evaluar]").attr("hx-disabled-elt")).isEqualTo("this");
        // 3 hipótesis y 4 evidencias, cada evidencia con peso y una celda por hipótesis
        assertThat(d.select("#T28-hipotesis li.fila")).hasSize(3);
        assertThat(d.select("#T28-evidencias li.fila")).hasSize(4);
        assertThat(d.select("fieldset[id^=T28-evidencias-0-celdas]")).hasSize(3);
        assertThat(d.select("input[name=evidencias[0].celdas[1]][checked]").val()).isEqualTo("I");
    }

    @Test
    void con_un_error_de_celda_el_formulario_lo_senala_en_esa_celda() {
        Document d = pintar("fragmentos/ficha/formulario.jte",
                Map.of("f", formularioT28(Map.of("evidencias[1].celdas[2]", "Marca esta celda: elige una de las opciones.")), "oob", true));

        assertThat(d.getElementById("form-T28").attr("hx-swap-oob")).isEqualTo("true");
        Element celda = d.getElementById("T28-evidencias-1-celdas-2");
        assertThat(celda.attr("aria-invalid")).isEqualTo("true");
        assertThat(celda.attr("aria-describedby")).isEqualTo("T28-evidencias-1-celdas-2-error");
        assertThat(d.getElementById("T28-evidencias-1-celdas-2-error").text()).contains("Marca esta celda");
    }

    @Test
    void la_ficha_tiene_tres_pestanas_con_roles_aria_y_un_panel() {
        var usar = new ControladorTecnicas.VistaUsar(T28, CATALOGO.ejemplosDe(EjecutorAch.ID), Optional.empty(), Optional.empty(),
                new ControladorTecnicas.VistaConfiguracion("T28", List.of(), "escala: C, I, N", false, ""), formularioT28(Map.of()), Optional.empty());
        var ficha = new ControladorTecnicas.VistaFicha(T28, "F5 · Pensamiento probabilístico y decisiones", ControladorTecnicas.Pestana.USAR,
                true, "escala: C, I, N", 1, List.of(), usar, List.of(), null);
        Document d = pintar("fragmentos/ficha/cuerpo.jte", Map.of("ficha", ficha));

        assertThat(d.select("#ficha-cuerpo").attr("aria-labelledby")).isEqualTo("ficha-titulo");
        Element lista = d.selectFirst("[role=tablist]");
        assertThat(lista.attr("aria-label")).isNotBlank();
        assertThat(lista.select("[role=tab]").eachText()).containsExactly("Qué es", "Usar", "Historial");
        assertThat(lista.select("[role=tab]").eachAttr("aria-selected")).containsExactly("false", "true", "false");
        assertThat(lista.select("[role=tab]").eachAttr("tabindex")).containsExactly("-1", "0", "-1");
        assertThat(lista.select("[role=tab]")).allSatisfy(tab -> {
            assertThat(tab.attr("aria-controls")).isEqualTo("panel-ficha");
            assertThat(tab.attr("hx-target")).isEqualTo("#ficha-cuerpo");
            assertThat(tab.attr("hx-get")).isEqualTo(tab.attr("href"));
        });
        Element panel = d.getElementById("panel-ficha");
        assertThat(panel.attr("role")).isEqualTo("tabpanel");
        assertThat(panel.attr("aria-labelledby")).isEqualTo("pestana-usar");
        assertThat(d.getElementById("resumen-config-T28")).isNotNull();
        assertThat(d.select("nav.barra-ejemplos a.ejemplo")).hasSize(4);
        assertThat(d.select("nav.barra-ejemplos a.ejemplo").first().attr("hx-get")).startsWith("/tecnicas/T28?ejemplo=");
        assertThat(d.getElementById("resultado-T28").attr("aria-live")).isEqualTo("polite");
        assertThat(d.select("form#config-T28").attr("hx-include")).isEqualTo("#form-T28");
    }

    @Test
    void una_tecnica_sin_ejecutor_dice_en_que_hito_llega() {
        Tecnica t01 = CATALOGO.tecnicas().getFirst();
        var ficha = new ControladorTecnicas.VistaFicha(t01, "F1 · Análisis y estructura de argumentos", ControladorTecnicas.Pestana.USAR,
                false, "", 2, List.of(), null, List.of(), null);
        Document d = pintar("fragmentos/ficha/cuerpo.jte", Map.of("ficha", ficha));
        assertThat(d.select("#panel-ficha").text()).contains("llega en el hito 2");
        assertThat(d.getElementById("resumen-config-T01")).isNull();
    }

    // -------------------------------------------------------------------------------------------
    // Catálogo y asociar a un expediente
    // -------------------------------------------------------------------------------------------

    @Test
    void la_rejilla_del_catalogo_filtra_con_htmx_y_marca_la_familia_activa() {
        List<ControladorCatalogo.Tarjeta> tarjetas = CATALOGO.tecnicas().stream().filter(t -> t.familia().equals("F5"))
                .map(t -> new ControladorCatalogo.Tarjeta(t, t.id().equals(EjecutorAch.ID), t.id().equals(EjecutorAch.ID) ? 1 : 4, 0)).toList();
        Document d = pintar("fragmentos/rejilla-catalogo.jte", Map.of("familias", CATALOGO.familias(), "intenciones", Intencion.values(),
                "tarjetas", tarjetas, "filtros", new ControladorCatalogo.Filtros("F5", "", "", ""), "total", 49L));

        assertThat(d.getElementById("catalogo-cuerpo")).isNotNull();
        assertThat(d.select("nav.familias a[aria-current=true]").text()).startsWith("F5");
        assertThat(d.select("form[role=search]").attr("hx-target")).isEqualTo("#resultados-catalogo");
        assertThat(d.select("label[for=buscar-tecnica]")).hasSize(1);
        assertThat(d.select("label[for=filtro-intencion]")).hasSize(1);
        assertThat(d.select("#rejilla [role=status]")).hasSize(1);
        assertThat(d.select("li.tecnica")).hasSize(10);
        assertThat(d.select("#tecnica-T28 a").attr("href")).isEqualTo("/tecnicas/T28");
        assertThat(d.select("#tecnica-T28 .chip-ok").text()).contains("disponible");
        assertThat(d.select("#tecnica-T29 .chip-pendiente").text()).isEqualTo("llega en el hito 4");
    }

    @Test
    void asociar_una_ejecucion_es_un_hx_post_con_su_estado_anunciado() {
        UUID id = UUID.randomUUID();
        Ejecucion e = new Ejecucion(id, Contextos.DUENA_DE_LA_PANADERIA, Contextos.INSTITUCION, IdTecnica.de("T28"), 1, Optional.empty(),
                Json.VACIO, Json.VACIO, Json.VACIO, "resumen", Optional.empty(), "c", java.time.Instant.now());
        Document d = pintar("fragmentos/asociar.jte", Map.of("ejecucion", e, "expedientes", List.of(), "mensaje", "Sin expediente."));

        Element form = d.getElementById("asociar-" + id);
        assertThat(form.attr("hx-post")).isEqualTo("/ejecuciones/" + id + "/expediente");
        assertThat(form.attr("hx-target")).isEqualTo("this");
        assertThat(d.select("label[for=asociar-" + id + "-expediente]")).hasSize(1);
        assertThat(d.getElementById("asociar-" + id + "-estado").attr("role")).isEqualTo("status");
    }
}
