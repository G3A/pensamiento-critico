package pensamiento.aceptacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Aceptación por HTTP del flujo D, Diario de decisiones y calibración (definición de hecho del hito 4), en lenguaje del
 * dominio: la dueña de la panadería abre una decisión, define el problema (T40 y T41), pasa la lista antes de decidir
 * (T16) y registra la decisión con su predicción (T32); con el reloj adelantado la revisión vence, la revisa, el Brier se
 * recalcula y el resultado queda fijo. Lo guarda todo en el expediente de la decisión, lo exporta y lo importa; y otra
 * persona recibe 404 (RF-03).
 */
class FlujoDDiarioDeDecisionesIT {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    private final ClienteApp admin = Instalacion.administrador();

    @AfterEach
    void devolverElRelojALaHoraReal() {
        Diario.adelantarElReloj(admin, 0);
    }

    @Test
    void crea_una_decision_la_revisa_con_el_reloj_adelantado_y_recalcula_brier() {
        Diario.adelantarElReloj(admin, 0);
        String duena = Instalacion.personaNueva(admin, "dueña de la panadería", "1357");
        Diario diario = Diario.de(Instalacion.entraComo(duena, "1357"));

        // P17: el tablero vacío invita a registrar la primera decisión.
        Document vacio = diario.tablero();
        assertThat(vacio.select("#revisiones h2").text()).isEqualTo("Revisiones pendientes (0)");
        assertThat(vacio.select("#calibracion").text()).contains("Cuando revises tu primera decisión");

        // P18, paso 0: sin problema definido no se puede avanzar.
        UUID decision = diario.nuevaDecision("Abrir la segunda sucursal");
        Document paso0 = diario.paso(decision, 0, "T40");
        assertThat(paso0.select("h1").text()).isEqualTo("Abrir la segunda sucursal");
        assertThat(paso0.select(".pasos .paso").eachText()).contains("1 · Contexto primero el paso 0");
        assertThat(diario.paso(decision, 4, "T32").select("[role=alert]").text()).contains("Primero define el problema");

        // T40 · Definición del problema y T41 · Primeros principios, guardadas en la decisión.
        Map<String, String> reformulaciones = new LinkedHashMap<>();
        reformulaciones.put("reformulaciones[0].texto", "¿Dónde abrimos la segunda sucursal sin descuidar la original?");
        reformulaciones.put("reformulaciones[0].elegida", "true");
        reformulaciones.put("reformulaciones[1].texto", "¿Cómo vendemos más pan con lo que ya tenemos?");
        diario.guardar(diario.formulario(paso0, "T40"), reformulaciones);
        Document paso0b = diario.paso(decision, 0, "T41");
        assertThat(paso0b.select("[name=problema]").val()).isEqualTo("¿Dónde abrimos la segunda sucursal sin descuidar la original?");
        diario.guardar(diario.formulario(paso0b, "T41"), Map.of("certezas[0].texto", "La sucursal original vende 260 panes por día.",
                "supuestos[0].texto", "La terminal tiene más gente que el centro.", "supuestos[0].como", "Contar peatones una mañana."));

        // Paso 4: la lista antes de decidir (T16) y el registro (T32) con su predicción.
        Document paso4 = diario.paso(decision, 4, "T16");
        diario.guardar(diario.formulario(paso4, "T16"), Map.of("alternativas", "Centro, terminal o esperar un año.",
                "cifras", "Conteo propio de peatones.", "contraria", "El encargado prefería esperar."));
        Document registro = diario.paso(decision, 4, "T32");
        assertThat(registro.select("[role=alert]")).as("con la lista hecha, el registro se abre").isEmpty();
        LocalDate hoy = LocalDate.now(BOGOTA);
        LocalDate revision = hoy.plusDays(100);
        UUID ejecucion = diario.guardar(diario.formulario(registro, "T32"), Map.of("decision", "Abrir en la terminal, no en el centro.",
                "contexto", "La matriz dio Terminal 29, Esperar 28 y Centro 25.", "alternativas", "Centro · esperar un año.",
                "prediccion", "La sucursal de la terminal cubre sus costos en 6 meses.", "confianza", "70",
                "cambiarOpinion", "Tres meses seguidos por debajo de 200 panes por día.", "fechaRevision", revision.toString()));
        assertThat(diario.paso(decision, 4, "T32").select(".pasos-decision [role=status]").text()).contains("registrada");

        // La decisión queda abierta, no por revisar, y no aparece en el Inicio hasta su fecha.
        Document abierta = diario.tablero();
        assertThat(abierta.select("#revisiones h2").text()).isEqualTo("Revisiones pendientes (0)");
        assertThat(abierta.select("#abiertas-titulo").text()).isEqualTo("Decisiones abiertas (1)");
        assertThat(abierta.select(".lista-decisiones a").eachText()).contains("Abrir en la terminal, no en el centro.");
        assertThat(diario.inicio().select(".revisiones-diario li")).isEmpty();

        // Con el reloj adelantado hasta la fecha de revisión, aparece al entrar al Diario y en el Inicio.
        Diario.adelantarElReloj(admin, 100);
        Document porRevisar = diario.tablero();
        assertThat(porRevisar.select("#revisiones h2").text()).isEqualTo("Revisiones pendientes (1)");
        String prediccion = porRevisar.select("#revisiones li[data-prediccion]").attr("data-prediccion");
        assertThat(diario.inicio().select(".revisiones-diario li").text()).contains("Revisar la decisión: Abrir en la terminal, no en el centro.");

        // La revisa: se cumplió. Brier de una sola predicción al 70% que se cumplió: (70 − 100)² / 10000 = 0,09.
        ClienteApp.Respuesta revisada = diario.revisar(UUID.fromString(prediccion), true);
        assertThat(revisada.estado()).isEqualTo(204);
        assertThat(revisada.cabecera("HX-Redirect")).contains("/diario");
        Document calibrado = diario.tablero();
        assertThat(calibrado.select("#revisiones h2").text()).isEqualTo("Revisiones pendientes (0)");
        assertThat(calibrado.select("#calibracion figcaption").text()).isEqualTo("Brier 0,09 con 1 resuelta. Sobre la diagonal estarías bien calibrado.");
        assertThat(calibrado.select("#calibracion circle.punto")).hasSize(1);
        assertThat(calibrado.select("#resueltas-titulo").text()).isEqualTo("Resueltas (1)");
        assertThat(diario.inicio().select(".revisiones-diario li")).isEmpty();

        // R05: el resultado queda fijo; un segundo intento responde 409 y no cambia nada.
        ClienteApp.Respuesta otraVez = diario.revisar(UUID.fromString(prediccion), false);
        assertThat(otraVez.estado()).isEqualTo(409);
        assertThat(Jsoup.parse(otraVez.cuerpo()).select("#error-diario").text()).contains("Esta predicción ya está resuelta: no se puede modificar.");
        assertThat(diario.tablero().select("#calibracion figcaption").text()).startsWith("Brier 0,09");

        // El registro de la decisión se pinta al día, con la revisión en su línea de tiempo.
        Document ejecucionVista = Jsoup.parse(diario.cliente().get("/ejecuciones/" + ejecucion).cuerpo());
        assertThat(ejecucionVista.select("[data-patron=V11] .estado-resultado .chip").text()).isEqualTo("resuelta: se cumplió");

        // Todo quedó en el expediente de la decisión.
        Document expediente = Jsoup.parse(diario.cliente().get("/expedientes/" + decision).cuerpo());
        assertThat(expediente.select("h1").text()).isEqualTo("Decisión: Abrir la segunda sucursal");
        assertThat(expediente.getElementById("linea-" + ejecucion)).isNotNull();

        // RF-12: el respaldo lleva la predicción resuelta; importarlo de nuevo no duplica nada.
        Taller respaldo = Taller.de(diario.cliente());
        String archivo = respaldo.exportarMisDatos();
        assertThat(archivo).contains("\"version\" : 5").contains(prediccion).contains("\"resultado\" : \"acierto\"");
        ClienteApp.Respuesta importada = respaldo.importarMisDatos(archivo);
        assertThat(importada.estado()).isEqualTo(200);
        assertThat(importada.cuerpo()).contains("0 ejecuciones nuevas");
        assertThat(diario.tablero().select("#resueltas-titulo").text()).isEqualTo("Resueltas (1)");

        // RF-03: otra persona no abre la decisión ni revisa la predicción, ni importa el respaldo ajeno.
        String secretaria = Instalacion.personaNueva(admin, "secretaria de la junta", "2468");
        Diario otra = Diario.de(Instalacion.entraComo(secretaria, "2468"));
        ClienteApp.Respuesta ajena = Taller.de(otra.cliente()).importarMisDatos(archivo);
        assertThat(ajena.estado()).isEqualTo(422);
        assertThat(ajena.cuerpo()).contains("ya son de otra persona");
        assertThat(otra.cliente().get("/diario/decisiones/" + decision).estado()).isEqualTo(404);
        assertThat(otra.cliente().get("/ejecuciones/" + ejecucion).estado()).isEqualTo(404);
        assertThat(otra.revisar(UUID.fromString(prediccion), true).estado()).isEqualTo(404);
        assertThat(otra.tablero().select(".lista-decisiones li")).isEmpty();
    }

    /** RNF-06: identificadores únicos, hx-* del refresco de pasos, ARIA, etiquetas y nada de style ni fill. */
    @Test
    void las_pantallas_del_diario_cumplen_la_accesibilidad() {
        Diario.adelantarElReloj(admin, 0);
        String vecino = Instalacion.personaNueva(admin, "vecino de la cuadra", "9517");
        Diario diario = Diario.de(Instalacion.entraComo(vecino, "9517"));
        UUID decision = diario.nuevaDecision("Poner luces en la cuadra");

        for (Document d : java.util.List.of(diario.tablero(), diario.paso(decision, 0, "T40"), diario.paso(decision, 2, "T29"))) {
            assertThat(d.select("[style], [fill], [stroke], script:not([src])")).as("sin style, fill ni scripts en línea").isEmpty();
            java.util.List<String> ids = d.select("[id]").eachAttr("id");
            assertThat(ids).as("identificadores únicos").doesNotHaveDuplicates();
            assertThat(d.select(".chip")).allSatisfy(c -> assertThat(c.text()).as("todo estado lleva texto").isNotBlank());
            assertThat(d.select("input[type=text], input[type=number], input[type=date], textarea, select")).allSatisfy(campo ->
                    assertThat(d.select("label[for=" + campo.id() + "]")).as("etiqueta de " + campo.id()).isNotEmpty());
        }
        Document tablero = diario.tablero();
        assertThat(tablero.select("#revisiones").attr("aria-labelledby")).isEqualTo("revisiones-titulo");
        Document asistente = diario.paso(decision, 0, "T40");
        org.jsoup.nodes.Element pasos = asistente.getElementById("pasos-decision");
        assertThat(pasos.attr("hx-get")).isEqualTo("/diario/decisiones/" + decision + "/pasos?paso=0&tecnica=T40");
        assertThat(pasos.attr("hx-trigger")).isEqualTo("ejecucion-guardada from:body");
        assertThat(pasos.select("li[aria-current=step]").text()).startsWith("0 · Problema");
        assertThat(pasos.select(".tecnicas-paso a[aria-current=page]").text()).isEqualTo("T40 · Definición del problema");
        assertThat(asistente.select("#form-T40 input[name=_expediente]").val()).isEqualTo(decision.toString());
        assertThat(asistente.select("#form-T40").attr("hx-post")).isEqualTo("/tecnicas/T40/evaluar");
        Document bloqueado = diario.paso(decision, 2, "T29");
        assertThat(bloqueado.select("[role=alert]").text()).contains("Primero define el problema");
        assertThat(bloqueado.select("#form-T29")).as("sin el paso 0, el formulario no se ofrece").isEmpty();
        ClienteApp.Respuesta fragmento = diario.cliente().getHtmx("/diario/decisiones/" + decision + "/pasos?paso=0&tecnica=T40");
        assertThat(fragmento.estado()).isEqualTo(200);
        assertThat(Jsoup.parseBodyFragment(fragmento.cuerpo()).select("nav#pasos-decision")).hasSize(1);
    }

    @Test
    void una_fecha_de_revision_demasiado_cercana_no_deja_guardar_la_decision() {
        Diario.adelantarElReloj(admin, 0);
        String presidenta = Instalacion.personaNueva(admin, "presidenta de la junta", "8642");
        Diario diario = Diario.de(Instalacion.entraComo(presidenta, "8642"));
        UUID decision = diario.nuevaDecision("Llevar las cámaras a la asamblea");
        Document paso0 = diario.paso(decision, 0, "T40");
        diario.guardar(diario.formulario(paso0, "T40"), Map.of("reformulaciones[0].texto", "¿Cómo reducimos los robos sin vigilar a los vecinos?",
                "reformulaciones[0].elegida", "true", "reformulaciones[1].texto", "¿Cómo se siente segura la cuadra?"));
        diario.guardar(diario.formulario(diario.paso(decision, 0, "T41"), "T41"), Map.of("certezas[0].texto", "Hubo 12 robos este año.",
                "supuestos[0].texto", "Las cámaras disuaden."));
        diario.guardar(diario.formulario(diario.paso(decision, 4, "T16"), "T16"), Map.of("alternativas", "Luces o ronda.", "cifras", "Registro de la junta.",
                "contraria", "La cuadra sin cámaras."));

        ClienteApp.Respuesta r = diario.guardarSinPoder(diario.formulario(diario.paso(decision, 4, "T32"), "T32"), Map.of(
                "decision", "Llevar la propuesta de cámaras a la asamblea.", "contexto", "12 robos este año.", "alternativas", "Luces · ronda.",
                "prediccion", "La asamblea aprueba las cámaras.", "confianza", "95", "cambiarOpinion", "Que la cuadra sin cámaras vote en contra.",
                "fechaRevision", LocalDate.now(BOGOTA).plusDays(3).toString()));

        assertThat(r.estado()).isEqualTo(422);
        assertThat(r.cuerpo()).contains("Guardado bloqueado: la fecha de revisión tiene que ser al menos 7 días después de hoy");
        assertThat(diario.tablero().select("#abiertas-titulo").text()).isEqualTo("Decisiones abiertas (0)");
    }
}
