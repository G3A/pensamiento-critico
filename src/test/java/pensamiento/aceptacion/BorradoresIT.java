package pensamiento.aceptacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import pensamiento.testutil.Entorno;

/**
 * Hito 10, entregable 8, por HTTP: la copia del borrador en el servidor es solo de quien la escribe. Se guarda, se lee igual y
 * se borra; su docente no la ve; una clave inválida responde 422; y la página de otra persona (cabecera X-Persona de otra
 * cuenta) no puede escribir en su cuenta.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BorradoresIT {

    private final String sufijo = UUID.randomUUID().toString().substring(0, 6);
    private ClienteApp profe;
    private ClienteApp estudiante;

    @BeforeAll
    void preparar() {
        ClienteApp admin = Instalacion.administrador();
        profe = Instalacion.docenteNuevo(admin, "Profe Borrador " + sufijo);
        UUID grupo = Aula.crearGrupo(admin, "8.º Borrador " + sufijo, "Profe Borrador " + sufijo);
        String nombre = "Estudiante Borrador " + sufijo;
        String codigo = AulaAltaEnLoteIT.codigos(profe.postFormulario("/grupos/" + grupo + "/lote", Map.of("texto", nombre)).cuerpo()).get(0);
        Instalacion.canjear(Instalacion.RUTA, codigo, Instalacion.contrasenaDe("estudiante"));
        estudiante = Instalacion.entraComo(nombre, "estudiante");
    }

    @Test
    void se_guarda_se_lee_igual_y_se_borra_y_su_docente_no_la_ve() {
        String campos = "{\"respuesta\":\"Es un solo caso, señora:\\nantes de decidir contaría.\"}";

        assertThat(estudiante.postFormulario("/borradores", Map.of("clave", "dojo:generalizacion-c1", "campos", campos)).estado()).isEqualTo(204);

        ClienteApp.Respuesta leido = estudiante.get("/borradores?clave=dojo:generalizacion-c1");
        assertThat(leido.estado()).isEqualTo(200);
        assertThat(leido.cuerpo()).contains("Es un solo caso, señora:\\nantes de decidir contaría.");
        assertThat(profe.get("/borradores?clave=dojo:generalizacion-c1").estado()).as("su docente no la ve").isEqualTo(204);
        assertThat(estudiante.postFormulario("/borradores/borrar", Map.of("clave", "dojo:generalizacion-c1")).estado()).isEqualTo(204);
        assertThat(estudiante.get("/borradores?clave=dojo:generalizacion-c1").estado()).isEqualTo(204);
    }

    @Test
    void una_clave_invalida_o_campos_que_no_son_json_responden_422() {
        assertThat(estudiante.postFormulario("/borradores", Map.of("clave", "Con Mayúsculas", "campos", "{}")).estado()).isEqualTo(422);
        assertThat(estudiante.postFormulario("/borradores", Map.of("clave", "dojo:x", "campos", "no es json")).estado()).isEqualTo(422);
        assertThat(estudiante.postFormulario("/borradores", Map.of("clave", "dojo:x", "campos", "{\"r\":\"" + "x".repeat(16_001) + "\"}")).estado())
                .isEqualTo(422);
    }

    @Test
    void la_pagina_de_otra_persona_no_escribe_en_su_cuenta() throws Exception {
        HttpRequest pedido = HttpRequest.newBuilder(URI.create(Entorno.urlApp() + "/borradores"))
                .header("Content-Type", "application/x-www-form-urlencoded").header("X-CSRF-TOKEN", estudiante.csrf())
                .header("X-Persona", UUID.randomUUID().toString()).header("Cookie", "PENSAMIENTO_SESION=" + estudiante.cookie("PENSAMIENTO_SESION"))
                .POST(HttpRequest.BodyPublishers.ofString("clave=dojo%3Aajeno&campos=%7B%7D")).build();

        int estado = HttpClient.newHttpClient().send(pedido, HttpResponse.BodyHandlers.discarding()).statusCode();

        assertThat(estado).isEqualTo(401);
        assertThat(estudiante.get("/borradores?clave=dojo:ajeno").estado()).isEqualTo(204);
    }
}
