package pensamiento.aceptacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Aceptación por HTTP del módulo T, registro de cambios de opinión (P20, definición de hecho del hito 7): un cambio de opinión
 * de cada flujo (T08 desde su ficha, el cierre del Consejero y el veredicto de la ficha de verificación) y uno registrado a mano
 * aparecen en una sola línea de tiempo; un año después, sus posturas salen sin revisar con el acceso al modo debate; con el
 * registro automático apagado, T08 ya no anota; el diario de razonamiento reúne las ejecuciones, con la reflexión del cierre.
 */
class FlujoTCambiosDeOpinionIT {

    private static final String CONSEJERO = "Conviene cerrar la sucursal del barrio los domingos.";
    private static final String CONDICION = "Pasan al menos 1.000 personas por la esquina cada mañana.";

    private final ClienteApp admin = Instalacion.administrador();

    @AfterEach
    void devolverElRelojALaHoraReal() {
        Diario.adelantarElReloj(admin, 0);
    }

    @Test
    void los_cambios_de_t08_del_consejero_y_de_la_ficha_aparecen_en_una_sola_linea_de_tiempo() {
        Diario.adelantarElReloj(admin, 0);
        String duena = Instalacion.personaNueva(admin, "dueña de la panadería", "1357");
        ClienteApp persona = Instalacion.entraComo(duena, "1357");
        Taller taller = Taller.de(persona);

        // T08 · Preguntas socráticas desde su ficha: el ejemplo de la segunda sucursal baja de 80 a 65.
        taller.guardar(taller.cargarEjemplo("T08", "La segunda sucursal"));

        // El Consejero: una sesión de decisión que cierra con la confianza de 80 a 50 por evidencia y una reflexión de T47.
        DialogoConsejero consejero = DialogoConsejero.de(persona);
        UUID sesion = consejero.nuevaSesion("decision", CONSEJERO, List.of(), "80", false);
        consejero.irAlCierre(sesion);
        consejero.responder(sesion, "Que los domingos se venda más de la mitad del pan de la semana.");
        consejero.cerrar(sesion, "Necesito ver las ventas de tres domingos.", "50", "evidencia", List.of());

        // La ficha de verificación: la condición de T11 con una fuente; el segundo veredicto baja la confianza de 70 a 40.
        taller.guardar(taller.cargarEjemplo("T11", "La sucursal que se paga sola"));
        Element pendiente = Jsoup.parse(persona.get("/").cuerpo()).select(".pendientes a").stream()
                .filter(a -> a.text().equals("Verificar la condición: " + CONDICION)).findFirst().orElseThrow();
        UUID condicion = UUID.fromString(pendiente.attr("href").substring("/verificar/".length()));
        Verificacion verificacion = Verificacion.de(persona);
        verificacion.elegirTipo(condicion, "dato_estadistico");
        assertThat(verificacion.registrar(condicion, new Verificacion.Fuente("Conteo propio de la esquina", "2026-09-20", "primaria", "observacional",
                "panadería", false, true, List.of(4, 4, 2, 3, 3), "contradice", "Contamos 640 personas en promedio entre las 7 y las 10.", null))
                .estado()).isEqualTo(200);
        assertThat(verificacion.veredicto(condicion, 70).estado()).isEqualTo(200);
        assertThat(verificacion.veredicto(condicion, 40).estado()).isEqualTo(200);

        // Registrar a mano, en P20, un cambio por presión social.
        Document antes = Jsoup.parse(persona.get("/cambios-de-opinion").cuerpo());
        String clave = antes.select("form.registro-cambio input[name=_clave]").val();
        ClienteApp.Respuesta registro = persona.postFormulario("/cambios-de-opinion", Map.of("_clave", clave, "postura",
                "Los clientes nuevos prefieren pan blanco", "antes", "75", "despues", "55", "causa", "presion"), true);
        assertThat(registro.estado()).isEqualTo(200);
        assertThat(registro.cabecera("HX-Redirect")).contains("/cambios-de-opinion");
        assertThat(persona.postFormulario("/cambios-de-opinion", Map.of("_clave", UUID.randomUUID().toString(), "postura", "Algo", "antes", "50",
                "despues", "50", "causa", "manual"), true).estado()).as("sin cambio de confianza no se registra").isEqualTo(422);

        // P20: una sola línea de tiempo con los cuatro, cada uno con la técnica que lo registró, y el año en barras.
        Document p20 = Jsoup.parse(persona.get("/cambios-de-opinion").cuerpo());
        List<String> linea = p20.select("[data-patron=V11] ol.linea-tiempo li").eachText();
        assertThat(linea).hasSize(4);
        assertThat(linea).anyMatch(t -> t.contains("«Conviene abrir la segunda sucursal en el centro este año.» · 80% → 65%")
                && t.contains("T08 · Preguntas socráticas"));
        assertThat(linea).anyMatch(t -> t.contains("«" + CONSEJERO + "» · 80% → 50% · evidencia nueva · T08 · Preguntas socráticas"));
        assertThat(linea).anyMatch(t -> t.contains("«" + CONDICION + "» · 70% → 40% · evidencia nueva · T22 · Triangulación"));
        assertThat(linea).anyMatch(t -> t.contains("«Los clientes nuevos prefieren pan blanco» · 75% → 55% · presión social · T46"));
        assertThat(p20.select("[data-barra=presion] label").text()).isEqualTo("Presión social: 1");
        assertThat(p20.select("#sin-revisar-titulo + p").text()).startsWith("Ninguna");

        // Un año y un mes después, las posturas salen sin revisar, cada una con su acceso al modo debate del Consejero.
        Diario.adelantarElReloj(admin, 400);
        Document unAnoDespues = Jsoup.parse(persona.get("/cambios-de-opinion").cuerpo());
        Element debate = unAnoDespues.select("ul.lista-aparte li").stream().filter(li -> li.text().contains(CONSEJERO)).findFirst().orElseThrow()
                .selectFirst("a");
        assertThat(debate.text()).isEqualTo("Ponerla a prueba en el modo debate");
        Document consejeroDebate = Jsoup.parse(persona.get(debate.attr("href")).cuerpo());
        assertThat(consejeroDebate.select("[name=postura]").val().isEmpty() ? consejeroDebate.select("[name=postura]").text()
                : consejeroDebate.select("[name=postura]").val()).isEqualTo(CONSEJERO);
        Diario.adelantarElReloj(admin, 0);

        // El diario de razonamiento reúne las ejecuciones de la semana, con la reflexión del cierre (T47).
        Document diario = Jsoup.parse(persona.get("/cambios-de-opinion/diario").cuerpo());
        assertThat(diario.select("[data-patron=V11] ol.linea-tiempo li").eachText())
                .anyMatch(t -> t.contains("T47 · Reflexión estructurada"))
                .anyMatch(t -> t.contains("T46 · Registro de cambios de opinión"));

        // Con el registro automático apagado en T46, guardar T08 otra vez no anota otro cambio, y P20 lo avisa.
        Dojo.de(persona).configurar("T46", List.of(Map.entry("cfg.causas", "manual"), Map.entry("cfg.causas", "presion"),
                Map.entry("cfg.mesesSinRevisar", "12")));
        taller.guardar(taller.cargarEjemplo("T08", "La segunda sucursal"));
        Document apagado = Jsoup.parse(persona.get("/cambios-de-opinion").cuerpo());
        assertThat(apagado.select("[data-patron=V11] ol.linea-tiempo li")).hasSize(4);
        assertThat(apagado.text()).contains("El registro automático está apagado");
    }
}
