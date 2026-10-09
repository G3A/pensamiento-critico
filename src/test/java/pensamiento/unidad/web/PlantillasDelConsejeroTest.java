package pensamiento.unidad.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
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

import pensamiento.nucleo.Json;
import pensamiento.nucleo.SesionConsejero;
import pensamiento.nucleo.TurnoConsejero;
import pensamiento.web.consejero.VistaConsejero;
import pensamiento.web.patrones.V09;

/**
 * Pruebas de plantilla del Consejero socrático (RNF-06, RNF-09): la burbuja que espera al modelo abre su conexión SSE,
 * dice su tiempo y la cola con texto y tiene Cancelar; la transcripción es un log; la entrada se deshabilita mientras el
 * modelo redacta y su textarea tiene etiqueta; al terminar la falsación aparece el cierre; el panel es un aside con nombre y
 * cada propuesta del modelo dice que no cuenta hasta adoptarla.
 */
class PlantillasDelConsejeroTest {

    private static final TemplateEngine PLANTILLAS = TemplateEngine.createPrecompiled(ContentType.Html);
    private static final UUID SESION = UUID.fromString("01a118b3-94cc-76f0-afe1-daab7018e1a0");
    private static final UUID TURNO = UUID.fromString("01a118b3-94cc-76f0-afe1-daab7018e1a1");

    private static Document pintar(String plantilla, Map<String, Object> parametros) {
        StringOutput salida = new StringOutput();
        PLANTILLAS.render(plantilla, parametros, salida);
        return Jsoup.parseBodyFragment(salida.toString());
    }

    private static SesionConsejero sesion(SesionConsejero.Estado estado) {
        return new SesionConsejero(SESION, UUID.randomUUID(), UUID.randomUUID(), Optional.empty(), SesionConsejero.Modo.DECISION,
                "Conviene abrir los domingos.", List.of(), new Json("{}"), true, Optional.of(80), false, estado, Optional.empty(), Optional.empty(),
                Optional.empty(), Instant.parse("2026-10-09T15:00:00Z"), estado == SesionConsejero.Estado.CERRADA ? Optional.of(Instant.parse("2026-10-09T16:00:00Z"))
                : Optional.empty());
    }

    private static VistaConsejero.Sesion vista(String estado, List<VistaConsejero.PropuestaElemento> propuestas) {
        V09.SeccionPanel elementos = new V09.SeccionPanel("Elementos (1 de 8)", List.of(
                new V09.ItemPanel("proposito", "Propósito", "lleno", "chip chip-ok", "Vender más.", null)));
        return new VistaConsejero.Sesion(sesion("cerrada".equals(estado) ? SesionConsejero.Estado.CERRADA : SesionConsejero.Estado.ABIERTA),
                "Conviene abrir los domingos.", List.of(), new VistaConsejero.Panel("Elementos del razonamiento (Paul-Elder)", List.of(elementos), propuestas),
                estado, true, Optional.empty(), List.of(), List.of(), "", Optional.empty());
    }

    @Test
    void la_burbuja_que_espera_al_modelo_abre_su_sse_dice_el_tiempo_y_la_cola_y_tiene_cancelar() {
        VistaConsejero.Burbuja b = new VistaConsejero.Burbuja(SESION, TURNO, 3, "consejero", "consejero · supuestos · supuestos",
                "¿Qué estás dando por sentado para que eso sea cierto?", List.of("redactando"), "Siguiente elemento pendiente.", true, 2);

        Document d = pintar("fragmentos/consejero/burbuja.jte", Map.of("b", b));

        Element li = d.getElementById("turno-" + TURNO);
        assertThat(li.attr("sse-connect")).isEqualTo("/consejero/turnos/" + TURNO + "/flujo");
        assertThat(li.attr("sse-close")).isEqualTo("fin");
        assertThat(li.attr("aria-busy")).isEqualTo("true");
        assertThat(li.select("[sse-swap=tiempo]").text()).isEqualTo("0 s");
        assertThat(li.select("[role=status]").text()).contains("Esperando al modelo", "En cola: 2 pedidos antes que el tuyo.");
        assertThat(li.select("[sse-swap=token]").attr("aria-live")).isEqualTo("off");
        assertThat(li.select("[sse-swap=fin]").attr("hx-target")).isEqualTo("#turno-" + TURNO);
        assertThat(li.select("button[data-accion=cancelar]").attr("hx-post")).isEqualTo("/consejero/turnos/" + TURNO + "/cancelar?sesion=" + SESION);
    }

    @Test
    void la_burbuja_terminada_dice_de_donde_salio_y_por_que_la_eligio_el_motor() {
        VistaConsejero.Burbuja b = new VistaConsejero.Burbuja(SESION, TURNO, 3, "consejero", "consejero · supuestos · supuestos",
                "¿Qué das por sentado sobre los domingos?", List.of("redactada por el modelo · validada"), "Siguiente elemento pendiente.", false, 0);

        Document d = pintar("fragmentos/consejero/burbuja.jte", Map.of("b", b));

        assertThat(d.select("[sse-connect]")).isEmpty();
        assertThat(d.select(".autor").text()).contains("consejero · supuestos · supuestos", "redactada por el modelo · validada");
        assertThat(d.select(".detalle").text()).isEqualTo("Siguiente elemento pendiente.");
    }

    @Test
    void la_entrada_se_deshabilita_mientras_el_modelo_redacta_y_tiene_etiqueta() {
        Document redactando = pintar("fragmentos/consejero/entrada.jte", Map.of("v", vista("redactando", List.of()), "oob", true));
        Document esperando = pintar("fragmentos/consejero/entrada.jte", Map.of("v", vista("esperando", List.of()), "oob", false));

        assertThat(redactando.getElementById("entrada-consejero").attr("hx-swap-oob")).isEqualTo("true");
        assertThat(redactando.select("textarea").hasAttr("disabled")).isTrue();
        assertThat(redactando.select("button[data-accion=enviar]").hasAttr("disabled")).isTrue();
        assertThat(esperando.select("textarea").hasAttr("disabled")).isFalse();
        assertThat(esperando.select("label[for=texto-respuesta]").text()).isEqualTo("Tu respuesta");
        assertThat(esperando.select("form").attr("hx-target")).isEqualTo("#dialogo");
        assertThat(esperando.select("button[data-accion=ir-al-cierre]").attr("hx-post")).isEqualTo("/consejero/sesiones/" + SESION + "/cierre");
    }

    @Test
    void despues_de_la_falsacion_aparece_el_cierre_con_la_reflexion_y_la_confianza() {
        Document lista = pintar("fragmentos/consejero/entrada.jte", Map.of("v", vista("lista", List.of()), "oob", false));
        Document cerrada = pintar("fragmentos/consejero/entrada.jte", Map.of("v", vista("cerrada", List.of()), "oob", false));

        assertThat(lista.select("form.cerrar-sesion").attr("hx-post")).isEqualTo("/consejero/sesiones/" + SESION + "/cerrar");
        assertThat(lista.select("label[for=reflexion]").text()).isEqualTo("¿Qué cambió en lo que piensas? (opcional)");
        assertThat(lista.select("input[name=causa]")).hasSize(3);
        assertThat(cerrada.select("[role=status]").text()).contains("Esta sesión está cerrada");
    }

    @Test
    void el_panel_es_un_aside_con_nombre_y_la_propuesta_del_modelo_no_cuenta_hasta_adoptarla() {
        VistaConsejero.PropuestaElemento p = new VistaConsejero.PropuestaElemento(TURNO, "supuestos", "Doy por hecho que hay clientes.",
                "Dice «doy por hecho».", false);

        Document d = pintar("fragmentos/consejero/panel.jte", Map.of("v", vista("esperando", List.of(p)), "oob", false));

        Element aside = d.selectFirst("aside#panel-consejero");
        assertThat(aside.attr("aria-label")).isEqualTo("Elementos del razonamiento (Paul-Elder)");
        assertThat(aside.select("li[data-item=proposito] .chip").text()).isEqualTo("lleno");
        assertThat(aside.select("li[data-propuesta=" + TURNO + "] .chip").text()).isEqualTo("propuesta del modelo · sin adoptar · no cuenta");
        assertThat(aside.select("button[data-accion=adoptar-elemento]").attr("hx-post")).isEqualTo("/consejero/turnos/" + TURNO + "/elemento?sesion=" + SESION);
        assertThat(d.select(".chip")).allSatisfy(chip -> assertThat(chip.text()).isNotBlank());
    }

    @Test
    void la_burbuja_de_la_persona_y_la_del_equipo_rojo_tienen_su_clase() {
        VistaConsejero.Burbuja persona = VistaConsejero.burbuja(sesion(SesionConsejero.Estado.ABIERTA), new TurnoConsejero(TURNO, SESION, 2,
                TurnoConsejero.Rol.PERSONA, "A1", "No lo conté.", TurnoConsejero.Origen.PERSONA, TurnoConsejero.Estado.LISTO, 0, Optional.empty(),
                Optional.empty(), Optional.empty(), false, Instant.parse("2026-10-09T15:00:00Z")), false, 0);
        VistaConsejero.Burbuja ataque = VistaConsejero.burbuja(sesion(SesionConsejero.Estado.ABIERTA), new TurnoConsejero(UUID.randomUUID(), SESION, 1,
                TurnoConsejero.Rol.CONSEJERO, "A1", "¿Contaste cuántos lo hacen?", TurnoConsejero.Origen.BANCO, TurnoConsejero.Estado.LISTO, 0, Optional.empty(),
                Optional.empty(), Optional.of("Debilidad: opinion_popular, pregunta 2."), false, Instant.parse("2026-10-09T15:00:00Z")), false, 0);

        assertThat(pintar("fragmentos/consejero/burbuja.jte", Map.of("b", persona)).select("li").hasClass("burbuja-persona")).isTrue();
        Document d = pintar("fragmentos/consejero/burbuja.jte", Map.of("b", ataque));
        assertThat(d.select("li").hasClass("burbuja-equipo-rojo")).isTrue();
        assertThat(d.select(".autor").text()).contains("equipo rojo · ataque 1", "del banco");
    }
}
