package pensamiento.aceptacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;

/**
 * Aceptación por HTTP del flujo C, Consejero socrático (definición de hecho del hito 5), en lenguaje del dominio: una
 * sesión de punta a punta en modo plantillas (la de docs/consejero.md), el debate con el equipo rojo, el steelman y el
 * double crux, guardar en el expediente, exportar e importar; otra persona recibe 404 (RF-03). Con el modelo, el turno
 * llega por SSE y lo que llega a la persona pasó el validador.
 */
class FlujoCConsejeroSocraticoIT {

    private static boolean ollamaDisponible() {
        String salud = new ClienteApp().get("/actuator/health").cuerpo();
        return salud.contains("\"ia\":{") && !salud.contains("\"status\":\"PLANTILLAS\"");
    }

    private static DialogoConsejero nuevaPersona(String rol, String pin) {
        ClienteApp admin = Instalacion.administrador();
        return DialogoConsejero.de(Instalacion.entraComo(Instalacion.personaNueva(admin, rol, pin), pin));
    }

    @Test
    void la_sesion_de_punta_a_punta_en_modo_plantillas_queda_en_el_expediente_con_el_cambio_de_opinion() {
        DialogoConsejero duena = nuevaPersona("dueña de la panadería", "2468");

        UUID id = duena.nuevaSesion("decision", "Conviene abrir la segunda sucursal en el centro este año.", List.of(), "80", false);
        Document sesion = duena.sesion(id);
        assertThat(sesion.select("#dialogo").attr("role")).isEqualTo("log");
        assertThat(sesion.select("#dialogo li.burbuja-consejero .texto-burbuja").eachText())
                .containsExactly("¿Qué quieres lograr con esta decisión? ¿Cómo sabrías que lo lograste?");
        assertThat(sesion.select("#dialogo li.burbuja-consejero .chip").text()).isEqualTo("del banco");
        assertThat(sesion.select("aside#panel-consejero").attr("aria-label")).isEqualTo("Elementos del razonamiento (Paul-Elder)");

        Document turno2 = duena.responder(id, "Quiero vender más, unos 200 panes más por día, sin descuidar el local que ya tenemos.");
        assertThat(turno2.select("li.burbuja-persona .texto-burbuja").text()).startsWith("Quiero vender más");
        assertThat(turno2.select("li.burbuja-consejero .texto-burbuja").text()).isEqualTo("¿Qué estás dando por sentado para que eso sea cierto?");
        assertThat(turno2.select("aside#panel-consejero[hx-swap-oob] li[data-item=proposito] .chip").text()).isEqualTo("lleno");
        Document turno3 = duena.responder(id, "Que en el centro pasa mucha gente y que la gente que pasa compra pan.");
        assertThat(turno3.select("li.burbuja-consejero .texto-burbuja").text())
                .isEqualTo("¿Qué quieres decir exactamente con «mucha»? ¿Qué ejemplo concreto cuenta y cuál no?");
        assertThat(turno3.select("li.burbuja-consejero .detalle").text())
                .isEqualTo("Adaptativo: «mucha» es un término difuso y los conceptos están pendientes.");

        // Cerrar sin responder la falsación no se puede.
        assertThat(duena.cerrarCrudo(id).estado()).isEqualTo(422);
        Document cierre = duena.irAlCierre(id);
        assertThat(cierre.select("li.burbuja-consejero .texto-burbuja").text())
                .isEqualTo("¿Qué te haría cambiar de opinión sobre «conviene abrir la segunda sucursal en el centro este año»?");
        Document lista = duena.responder(id, "Que el conteo de una semana completa dé menos de 600 personas por mañana.");
        assertThat(lista.select("form.cerrar-sesion")).as("después de la falsación aparece el cierre").isNotEmpty();
        duena.cerrar(id, "Necesito contar una semana entera antes de firmar.", "60", "evidencia", List.of());

        Document cerrada = duena.sesion(id);
        assertThat(cerrada.select("#entrada-consejero").text()).contains("Esta sesión está cerrada");
        assertThat(cerrada.select(".resultado-sesion [data-patron=V09] .tarjeta-resultado .titular").text())
                .isEqualTo("2 turnos · 2 de 8 elementos · cierre respondido.");
        assertThat(cerrada.select(".resultado-sesion").text()).contains("Qué cambió: Necesito contar una semana entera antes de firmar.",
                "Confianza: 80% → 60% (causa: evidencia).");
        String expediente = cerrada.select(".consejero-sesion > p.detalle a[href^=/expedientes/]").attr("href");
        assertThat(expediente).startsWith("/expedientes/");
        assertThat(duena.cliente().get(expediente).cuerpo()).contains("T08 · Preguntas socráticas");
        assertThat(duena.inicio().select(".lista-sesiones li").text()).contains("Conviene abrir la segunda sucursal", "cerrada");
    }

    @Test
    void el_debate_ataca_con_el_banco_y_sigue_con_el_steelman_y_el_double_crux_en_el_expediente_de_la_sesion() {
        DialogoConsejero vecino = nuevaPersona("vecino de la junta", "1593");

        UUID id = vecino.nuevaSesion("debate", "Hay que comprar las cámaras que ofrece el vendedor.",
                List.of(new String[] {"El vendedor dice que bajan los robos un 70%.", "no_se"},
                        new String[] {"En el barrio vecino bajaron los robos después de ponerlas.", "causa"}), "90", false);
        Document sesion = vecino.sesion(id);
        Element ataque = sesion.selectFirst("#dialogo li.burbuja-equipo-rojo");
        assertThat(ataque.select(".texto-burbuja").text())
                .isEqualTo("Eso lo dice alguien que gana si le crees. ¿Tienes un solo dato que no venga de esa persona?");
        assertThat(sesion.select("aside#panel-consejero").attr("aria-label")).isEqualTo("Debilidades que identificó el código");
        Document ataque2 = vecino.responder(id, "El municipio reporta 12% de baja en el barrio vecino. Es menos, pero es independiente.");
        assertThat(ataque2.select("li.burbuja-equipo-rojo .autor").text()).contains("equipo rojo · ataque 2");

        // Después del equipo rojo: el steelman (T34) y el double crux (T38) se guardan en el expediente de la sesión.
        Taller.Formulario t34 = vecino.tecnicaDelDebate(id, "T34");
        UUID steelman = vecino.guardar(t34, Map.of("posturaOriginal", "Los que no quieren cámaras no les importa el barrio.",
                "cita", "Prefiero que no me graben cada vez que salgo de mi casa.",
                "steelman", "Las cámaras cuestan, vigilan a los vecinos honestos todo el día, y no hay evidencia local de que bajen los robos."));
        Taller.Formulario t38 = vecino.tecnicaDelDebate(id, "T38");
        assertThat(t38.form().select("[name=posturaA]").val()).isEqualTo("Hay que comprar las cámaras que ofrece el vendedor.");
        assertThat(t38.form().select("[name=posturaB]").val()).startsWith("Las cámaras cuestan");
        UUID crux = vecino.guardar(t38, Map.of("dependeB[0].texto", "Las cámaras no bajan los robos, los mueven.",
                "cruxes[0].hecho", "En barrios parecidos, las cámaras bajaron los robos sin moverlos a otras cuadras.",
                "cruxes[0].cambiaA", "true", "cruxes[0].cambiaB", "true", "cruxes[0].verificable", "true"));

        vecino.irAlCierre(id);
        vecino.responder(id, "Que el municipio diga que los robos se movieron de cuadra.");
        vecino.cerrar(id, "", "70", "steelman", List.of());

        Document cerrada = vecino.sesion(id);
        assertThat(cerrada.select(".resultado-sesion .tarjeta-resultado .titular").text()).isEqualTo("3 ataques · 1 respondido · 2 sin responder.");
        String expediente = cerrada.select(".consejero-sesion > p.detalle a[href^=/expedientes/]").attr("href");
        String enElExpediente = vecino.cliente().get(expediente).cuerpo();
        assertThat(enElExpediente).contains("T36 · Equipo rojo / abogado del diablo", "T34 · Steelmanning", "T38 · Double crux")
                .contains(steelman.toString(), crux.toString());
    }

    @Test
    void exportar_e_importar_lleva_la_sesion_y_otra_persona_recibe_404() {
        DialogoConsejero duena = nuevaPersona("dueña de la panadería", "3579");
        UUID id = duena.nuevaSesion("escalera", "Mi hija no me quiere llamar.", List.of(), "", false);
        duena.responder(id, "Mi hija no llamó en dos semanas.");
        duena.irAlCierre(id);
        duena.responder(id, "Que me diga que estaba en exámenes.");
        duena.cerrar(id, "", "", "manual", List.of("datos"));

        ClienteApp.Respuesta archivo = duena.cliente().get("/mis-datos/exportar");
        assertThat(archivo.estado()).isEqualTo(200);
        assertThat(archivo.cuerpo()).contains("\"version\" : 6", id.toString(), "Mi hija no llamó en dos semanas.");
        ClienteApp.Respuesta reimportado = duena.cliente().postArchivo("/mis-datos/importar", "archivo", "datos.json", archivo.cuerpo().getBytes());
        assertThat(reimportado.estado()).isLessThan(400);
        assertThat(duena.sesion(id).select("#dialogo li").size()).as("postura, dos preguntas, el cierre y dos respuestas").isEqualTo(6);

        DialogoConsejero otra = nuevaPersona("secretaria de la junta", "8642");
        assertThat(otra.getSesion(id).estado()).isEqualTo(404);
        assertThat(otra.responderCrudo(id, "Me meto.").estado()).isEqualTo(404);
        assertThat(otra.cliente().get("/consejero/sesiones/" + id + "/debate?tecnica=T34").estado()).isEqualTo(404);
        ClienteApp.Respuesta ajeno = otra.cliente().postArchivo("/mis-datos/importar", "archivo", "datos.json", archivo.cuerpo().getBytes());
        assertThat(ajeno.cuerpo()).contains("otra persona");
    }

    @Test
    void sin_el_modelo_una_sesion_que_pide_el_modelo_sigue_con_el_banco() {
        assumeTrue(!ollamaDisponible(), "Ollama responde: esta prueba es para el servicio detenido");
        DialogoConsejero junta = nuevaPersona("presidenta de la junta", "7531");
        assertThat(junta.inicio().select("[role=status]").text()).contains("modo plantillas");

        UUID id = junta.nuevaSesion("sombreros", "¿Ponemos cámaras en la cuadra?", List.of(), "", true);
        Document sesion = junta.sesion(id);
        assertThat(sesion.select("#dialogo [sse-connect]")).as("sin modelo no hay SSE").isEmpty();
        assertThat(sesion.select("#dialogo li.burbuja-consejero .texto-burbuja").text())
                .isEqualTo("Sombrero blanco: ¿qué hechos y datos tienes sobre esto, sin opinar?");
    }

    @Test
    void con_el_modelo_el_turno_llega_por_sse_validado_y_queda_su_registro() {
        assumeTrue(ollamaDisponible(), "Ollama no responde: esta prueba necesita el modelo");
        DialogoConsejero duena = nuevaPersona("dueña de la panadería", "9753");

        UUID id = duena.nuevaSesion("decision", "Conviene abrir los domingos.", List.of(), "", true);
        Document sesion = duena.sesion(id);
        Element burbuja = sesion.selectFirst("#dialogo li.burbuja-consejero");
        String conexion = burbuja.attr("sse-connect");
        if (!conexion.isEmpty()) {
            assertThat(burbuja.select("[role=status]").text()).contains("Esperando al modelo");
            UUID turno = UUID.fromString(conexion.replace("/consejero/turnos/", "").replace("/flujo", ""));
            String flujo = duena.flujo(turno, Duration.ofMinutes(5));
            assertThat(flujo).contains("event:fin");
        }
        Document despues = duena.sesion(id);
        String pregunta = despues.select("#dialogo li.burbuja-consejero .texto-burbuja").first().text();
        assertThat(pregunta).endsWith("?");
        assertThat(pregunta.toLowerCase()).doesNotContain("usted", "tienes razón", "deberías", "lo mejor es");
        assertThat(despues.select("#dialogo li.burbuja-consejero .chip").first().text()).isIn("redactada por el modelo · validada", "del banco");
        assertThat(despues.select("#entrada-consejero textarea").hasAttr("disabled")).as("terminado el turno, se puede responder").isFalse();
    }
}
