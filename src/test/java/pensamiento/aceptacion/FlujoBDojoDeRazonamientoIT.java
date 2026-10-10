package pensamiento.aceptacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import pensamiento.catalogo.MapeadorJson;
import pensamiento.expediente.PaqueteDatos;

/**
 * Aceptación por HTTP del flujo B, Dojo de razonamiento (definición de hecho del hito 7): una semana simulada con el reloj de
 * la app adelantado, con el ejemplo de punta a punta de docs/dojo.md (tres retos por día, dos aciertos para dominar un nivel,
 * tema falacias). Cada repaso sale el día que toca, el nivel sube por reglas, Inicio avisa los retos del día, el respaldo
 * versión 6 lleva los intentos y otra persona no ve nada de lo de la primera (RF-03).
 */
class FlujoBDojoDeRazonamientoIT {

    private static final String FALACIAS = "falacias";

    private final ClienteApp admin = Instalacion.administrador();

    @AfterEach
    void devolverElRelojALaHoraReal() {
        Diario.adelantarElReloj(admin, 0);
    }

    @Test
    void una_semana_de_dojo_con_el_reloj_adelantado_programa_cada_repaso_por_reglas() {
        Diario.adelantarElReloj(admin, 0);
        String duena = Instalacion.personaNueva(admin, "dueña de la panadería", "1357");
        ClienteApp persona = Instalacion.entraComo(duena, "1357");
        Dojo dojo = Dojo.de(persona);
        dojo.configurar("T49", List.of(Map.entry("cfg.retosPorDia", "3"), Map.entry("cfg.facilidadInicial", "2.5")));
        dojo.configurar("T48", List.of(Map.entry("cfg.niveles", "identificar"), Map.entry("cfg.niveles", "analizar"), Map.entry("cfg.niveles", "evaluar"),
                Map.entry("cfg.niveles", "crear"), Map.entry("cfg.avanceAutomatico", "true"), Map.entry("cfg.aciertosParaDominar", "2")));

        // Día 1: tres conceptos nuevos en identificar.
        Document primero = dojo.pantalla(FALACIAS);
        assertThat(primero.select(".estado-dojo").text()).contains("racha 0", "Hoy: 0 de 3 retos");
        assertThat(primero.select("#reto [data-reto] .chip").text()).isEqualTo("identificar");
        assertThat(dojo.responder(FALACIAS, "generalizacion-i1", true).select(".repaso-reto").text()).isEqualTo("Siguiente repaso: mañana.");
        dojo.responder(FALACIAS, "ad_hominem-i1", false);
        Document dominaIdentificar = dojo.responder(FALACIAS, "falso_dilema-i1", true);
        assertThat(dominaIdentificar.select("[role=status]").text()).contains("Dominaste «identificar»: se abre «analizar».");
        Document terminado = dojo.pantalla(FALACIAS);
        assertThat(terminado.select(".mensaje-dojo").text()).isEqualTo("Por hoy terminaste: hiciste 3 retos.");
        assertThat(terminado.select(".estado-dojo").text()).contains("racha 1");

        // Día 2: tocan los tres, en el orden del banco, ya en analizar.
        Diario.adelantarElReloj(admin, 1);
        assertThat(dojo.responder(FALACIAS, "generalizacion-a1", true).select(".repaso-reto").text()).isEqualTo("Siguiente repaso: en 6 días.");
        assertThat(dojo.responder(FALACIAS, "ad_hominem-a1", true).select("[role=status]").text())
                .contains("Dominaste «analizar»: se abre «evaluar».");
        dojo.responder(FALACIAS, "falso_dilema-e1", false);

        // Día 3: evaluar y el primer reto de crear, calificado por la rúbrica.
        Diario.adelantarElReloj(admin, 2);
        dojo.responder(FALACIAS, "ad_hominem-e1", true);
        assertThat(dojo.responder(FALACIAS, "falso_dilema-e1", true).select("[role=status]").text())
                .contains("Dominaste «evaluar»: se abre «crear».");
        Document rubrica = dojo.responder(FALACIAS, "pendiente_resbaladiza-c1", true);
        assertThat(rubrica.select("ul.chequeos-rubrica li")).isNotEmpty().allSatisfy(li -> assertThat(li.text()).startsWith("cumple"));

        // Día 4: todos los niveles dominados y un error en crear con el texto original.
        Diario.adelantarElReloj(admin, 3);
        assertThat(dojo.responder(FALACIAS, "falso_dilema-c1", true).select("[role=status]").text()).contains("Dominaste los 4 niveles activos.");
        dojo.responder(FALACIAS, "pendiente_resbaladiza-c1", true);
        Document error = dojo.responder(FALACIAS, "autoridad-c1", false);
        assertThat(error.select("ul.chequeos-rubrica li").eachText()).anyMatch(t -> t.startsWith("no cumple"));
        assertThat(error.text()).contains("racha 4");

        // Día 6 (el 5 no entró): Inicio avisa, la racha se cortó y el repaso atrasado sale primero.
        Diario.adelantarElReloj(admin, 5);
        Document inicio = Jsoup.parse(persona.get("/").cuerpo());
        assertThat(inicio.select(".repasos-dojo li").text()).contains("B · 3 retos del Dojo para hoy", "racha 0");
        Document lunes = dojo.pantalla(FALACIAS);
        assertThat(Dojo.reto(lunes)).isEqualTo("autoridad-c1");
        assertThat(lunes.select("#reto [data-reto]").text()).contains("repaso que toca hoy");
        Document progreso = dojo.progreso();
        assertThat(progreso.select("[data-patron=V13c] ol.calendario li .repasos-dia").eachText())
                .containsExactly("1 repaso", "0 repasos", "1 repaso", "1 repaso", "2 repasos", "0 repasos", "0 repasos");
        assertThat(progreso.select("[data-patron=V13b]")).hasSize(4);

        // RF-12: el respaldo versión 6 lleva los doce intentos y se vuelve a importar sin duplicar.
        String archivo = Taller.de(persona).exportarMisDatos();
        PaqueteDatos paquete = MapeadorJson.mapper().readValue(archivo, PaqueteDatos.class);
        assertThat(paquete.version()).isEqualTo(6);
        assertThat(paquete.intentosDojo()).hasSize(12);
        assertThat(paquete.competencias()).singleElement().satisfies(c -> {
            assertThat(c.tecnica()).isEqualTo("T13");
            assertThat(c.nivel()).isEqualTo("crear");
            assertThat(c.aciertos()).isEqualTo(9);
        });
        assertThat(Taller.de(persona).importarMisDatos(archivo).estado()).isEqualTo(200);
        assertThat(MapeadorJson.mapper().readValue(Taller.de(persona).exportarMisDatos(), PaqueteDatos.class).intentosDojo()).hasSize(12);

        // RF-03: otra persona empieza de cero y no puede importar los intentos de la primera.
        String secretaria = Instalacion.personaNueva(admin, "secretaria de la junta", "2468");
        ClienteApp otra = Instalacion.entraComo(secretaria, "2468");
        Document suya = Dojo.de(otra).pantalla(FALACIAS);
        assertThat(suya.select(".estado-dojo").text()).contains("racha 0", "Hoy: 0 de 10 retos");
        assertThat(Dojo.reto(suya)).isEqualTo("generalizacion-i1");
        assertThat(Taller.de(otra).importarMisDatos(archivo).estado()).isNotEqualTo(200);
    }

    @Test
    void responder_dos_veces_el_mismo_formulario_guarda_un_solo_intento() {
        String vecino = Instalacion.personaNueva(admin, "vecino de la cuadra 3", "8642");
        ClienteApp persona = Instalacion.entraComo(vecino, "8642");
        Dojo dojo = Dojo.de(persona);
        String clave = UUID.randomUUID().toString();

        assertThat(dojo.responderCon("generalizacion-i1", "a", clave).estado()).isEqualTo(200);
        assertThat(dojo.responderCon("generalizacion-i1", "a", clave).estado()).isEqualTo(200);
        assertThat(dojo.responderCon("generalizacion-i1", "", UUID.randomUUID().toString()).estado()).isEqualTo(422);

        assertThat(MapeadorJson.mapper().readValue(Taller.de(persona).exportarMisDatos(), PaqueteDatos.class).intentosDojo()).hasSize(1);
        assertThat(dojo.pantalla("").select(".estado-dojo").text()).contains("Hoy: 1 de 10 retos");
    }
}
