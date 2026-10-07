package pensamiento.aceptacion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** RF-01: docker compose up --build deja app, db y ollama sanos. Se afirma por HTTP contra /actuator/health. */
class RF01LevantarAplicacionIT {

    @Test
    void la_aplicacion_responde_up_con_la_base_de_datos_y_la_ia_sanas() {
        ClienteApp cliente = new ClienteApp();
        ClienteApp.Respuesta salud = cliente.get("/actuator/health");
        assertThat(salud.estado()).isEqualTo(200);
        assertThat(salud.cuerpo()).contains("\"status\":\"UP\"");
        assertThat(salud.cuerpo()).as("PostgreSQL responde").contains("\"db\":{\"details\"").contains("\"database\":\"PostgreSQL\"");
        assertThat(salud.cuerpo()).as("Ollama lista los dos modelos").contains("\"ia\":{\"details\"").contains("qwen3").contains("bge-m3");
    }

    @Test
    void sin_sesion_la_pantalla_de_bloqueo_pregunta_quien_eres_y_el_resto_redirige_a_ella() {
        ClienteApp cliente = new ClienteApp();
        ClienteApp.Respuesta bloqueo = cliente.get("/bloqueo");
        assertThat(bloqueo.estado()).isEqualTo(200);
        assertThat(bloqueo.cuerpo()).contains("¿Quién eres?").contains("administrador");

        ClienteApp.Respuesta inicio = cliente.get("/");
        assertThat(inicio.estado()).isEqualTo(302);
        assertThat(inicio.cabecera("Location").orElse("")).contains("/bloqueo");
    }

    @Test
    void las_cabeceras_de_seguridad_llegan_al_navegador() {
        ClienteApp.Respuesta bloqueo = new ClienteApp().get("/bloqueo");
        assertThat(bloqueo.cabecera("Content-Security-Policy").orElse("")).contains("script-src 'self'").doesNotContain("unsafe");
        assertThat(bloqueo.cabecera("X-Frame-Options").orElse("")).isEqualToIgnoringCase("DENY");
        assertThat(bloqueo.cabecera("X-Content-Type-Options").orElse("")).isEqualToIgnoringCase("nosniff");
    }
}
