package pensamiento.aceptacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * Hito 10, entregable 6, por HTTP: el estudiante entra y su inicio es "Reto de hoy", con un menú de cuatro entradas (Reto de
 * hoy, Dojo, Mis trabajos, Salir) también en la barra inferior; sin tema asignado lo invita al Dojo; con el tema que el
 * docente asignó, el reto sale de ese tema, se responde y "Siguiente reto" vuelve a "Reto de hoy". Una fecha pasada no se
 * acepta.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AulaRetoIT {

    private final String sufijo = UUID.randomUUID().toString().substring(0, 6);
    private ClienteApp profe;
    private ClienteApp estudiante;
    private UUID grupo;

    @BeforeAll
    void preparar() {
        ClienteApp admin = Instalacion.administrador();
        profe = Instalacion.docenteNuevo(admin, "Profe Reto " + sufijo);
        grupo = Aula.crearGrupo(admin, "9.º Reto " + sufijo, "Profe Reto " + sufijo);
        String nombre = "Estudiante Reto " + sufijo;
        String codigo = AulaAltaEnLoteIT.codigos(profe.postFormulario("/grupos/" + grupo + "/lote", Map.of("texto", nombre)).cuerpo()).get(0);
        assertThat(Instalacion.canjear(Instalacion.RUTA, codigo, Instalacion.contrasenaDe("estudiante")).estado()).isEqualTo(302);
        estudiante = Instalacion.entraComo(nombre, "estudiante");
    }

    @Test
    @Order(1)
    void el_estudiante_empieza_en_reto_de_hoy_con_un_menu_de_cuatro_entradas() {
        ClienteApp.Respuesta inicio = estudiante.get("/");
        assertThat(inicio.estado()).isEqualTo(302);
        assertThat(inicio.cabecera("Location").orElse("")).endsWith("/reto");

        Document reto = Jsoup.parse(estudiante.get("/reto").cuerpo());
        assertThat(reto.select("#menu li").eachText()).containsExactly("Reto de hoy", "Dojo", "Mis trabajos", "Salir");
        assertThat(reto.select(".barra-inferior a, .barra-inferior button").eachText()).containsExactly("Reto de hoy", "Dojo", "Mis trabajos", "Salir");
        assertThat(reto.select("#menu a[href=/catalogo], #menu a[href=/grupos]")).isEmpty();
        assertThat(reto.text()).contains("Tu profe todavía no eligió un tema");
    }

    @Test
    @Order(2)
    void una_fecha_pasada_no_se_acepta() {
        ClienteApp.Respuesta r = profe.postFormulario("/grupos/" + grupo + "/tema", Map.of("tema", "falacias", "hasta", "2000-01-01"));
        assertThat(r.estado()).isEqualTo(422);
        assertThat(r.cuerpo()).contains("La fecha ya pasó");
    }

    @Test
    @Order(3)
    void con_el_tema_asignado_el_reto_sale_de_ese_tema_y_se_responde() {
        assertThat(profe.postFormulario("/grupos/" + grupo + "/tema", Map.of("tema", "sesgos", "hasta", "")).estado()).isEqualTo(302);
        assertThat(profe.get("/grupos/" + grupo).cuerpo()).contains("T14 · Sesgos cognitivos");

        Document reto = Jsoup.parse(estudiante.get("/reto").cuerpo());
        assertThat(reto.select("h1").text()).isEqualTo("Reto de hoy");
        assertThat(reto.text()).contains("9.º Reto " + sufijo, "T14 · Sesgos cognitivos");
        Element formulario = reto.selectFirst("form[action^=/dojo/retos/]");
        assertThat(formulario).as("hay un reto para responder").isNotNull();
        assertThat(formulario.select("input[name=grupo]").val()).isEqualTo("9.º Reto " + sufijo);
        String opcion = formulario.select("input[name=respuesta]").first().val();

        ClienteApp.Respuesta respuesta = estudiante.postFormulario(formulario.attr("action"), Map.of("respuesta", opcion,
                "_clave", formulario.select("input[name=_clave]").val(), "tema", formulario.select("input[name=tema]").val(),
                "nivel", formulario.select("input[name=nivel]").val(), "grupo", formulario.select("input[name=grupo]").val()));

        assertThat(respuesta.estado()).isEqualTo(200);
        assertThat(Jsoup.parse(respuesta.cuerpo()).select("a[data-accion=siguiente]").attr("href")).isEqualTo("/reto");
    }

    @Test
    @Order(4)
    void el_panel_cuenta_el_reto_respondido_con_ambos_rotulos_y_solo_lo_ve_su_docente() {
        ClienteApp.Respuesta panel = profe.get("/grupos/" + grupo + "/panel");

        assertThat(panel.estado()).isEqualTo(200);
        assertThat(panel.cuerpo()).contains("Aciertos por concepto · T14 · Sesgos cognitivos", "Retos de crear · práctica, no nota",
                " de 1 (", "retos respondidos hoy, por 1 estudiante");
        assertThat(estudiante.get("/grupos/" + grupo + "/panel").estado()).isEqualTo(403);
    }

    @Test
    @Order(4)
    void mis_trabajos_muestra_solo_lo_suyo_y_el_enlace_al_progreso() {
        ClienteApp.Respuesta r = estudiante.get("/mis-trabajos");
        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo()).contains("Mis trabajos", "/dojo/progreso");
        assertThat(estudiante.get("/dojo/progreso").estado()).isEqualTo(200);
    }

    @Test
    @Order(5)
    void sin_tema_vuelve_a_invitar_al_dojo() {
        profe.postFormulario("/grupos/" + grupo + "/tema", Map.of("tema", "", "hasta", ""));
        assertThat(estudiante.get("/reto").cuerpo()).contains("Tu profe todavía no eligió un tema");
        assertThat(List.of(estudiante.get("/dojo").estado())).containsExactly(200);
    }
}
