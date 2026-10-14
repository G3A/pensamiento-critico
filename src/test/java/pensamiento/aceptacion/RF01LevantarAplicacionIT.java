package pensamiento.aceptacion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** RF-01: docker compose up --build deja app, db y ollama sanos. Se afirma por HTTP contra /actuator/health. */
class RF01LevantarAplicacionIT {

    @Test
    void la_aplicacion_responde_up_y_sin_sesion_no_muestra_nada_interno() {
        ClienteApp.Respuesta salud = new ClienteApp().get("/actuator/health");
        assertThat(salud.estado()).isEqualTo(200);
        assertThat(salud.cuerpo()).as("solo el estado (hito 8)").contains("\"status\":\"UP\"")
                .doesNotContain("components", "details", "qwen3", "PostgreSQL");
        assertThat(new ClienteApp().get("/actuator/metrics").estado()).as("las métricas no son públicas").isNotEqualTo(200);
    }

    @Test
    void el_operador_ve_la_base_de_datos_y_la_ia_sanas_y_las_metricas_por_institucion() {
        ClienteApp operador = new ClienteApp();
        operador.entrarComoOperador(pensamiento.testutil.Entorno.claveOperador());
        ClienteApp.Respuesta salud = operador.get("/actuator/health");
        assertThat(salud.cuerpo()).contains("\"status\":\"UP\"");
        assertThat(salud.cuerpo()).as("PostgreSQL responde").contains("\"db\":{\"details\"").contains("\"database\":\"PostgreSQL\"");
        assertThat(salud.cuerpo()).as("Ollama lista los dos modelos").contains("\"ia\":{\"details\"").contains("qwen3").contains("bge-m3");

        Instalacion.administrador().get("/catalogo");   // con su llave de acceso (hito 9)
        ClienteApp.Respuesta peticiones = operador.get("/actuator/metrics/http.server.requests");
        assertThat(peticiones.estado()).isEqualTo(200);
        assertThat(peticiones.cuerpo()).as("la institución es una etiqueta").contains("\"tag\":\"institucion\"").contains("\"" + Instalacion.RUTA + "\"");
        assertThat(operador.get("/actuator/metrics/hikaricp.connections.active").estado()).as("conexiones").isEqualTo(200);
        assertThat(operador.get("/actuator/metrics/ia.cola").estado()).as("cola de Ollama").isEqualTo(200);
    }

    @Test
    void sin_sesion_la_entrada_de_la_institucion_pide_nombre_y_contrasena_y_el_resto_redirige_a_ella() {
        Instalacion.sembrar();
        ClienteApp cliente = new ClienteApp();
        ClienteApp.Respuesta entrada = cliente.get("/" + Instalacion.RUTA);
        assertThat(entrada.estado()).isEqualTo(200);
        assertThat(entrada.cuerpo()).contains("Tu nombre").contains("Tu contraseña").doesNotContain("administrador");
        // Hito 9, innegociable 10: nada interno antes de entrar, ni el modelo ni un botón de menú sin menú.
        assertThat(entrada.cuerpo().toLowerCase()).doesNotContain("qwen", "bge-m3", "ollama", ">menú<", "id=\"menu\"");
        assertThat(cliente.get("/" + Instalacion.RUTA + "/sin-cuenta").cuerpo().toLowerCase()).doesNotContain("qwen", "bge-m3", "ollama");

        ClienteApp.Respuesta catalogo = cliente.get("/catalogo");
        assertThat(catalogo.estado()).isEqualTo(302);
        assertThat(catalogo.cabecera("Location").orElse("")).as("la cookie recuerda la institución").contains("/" + Instalacion.RUTA + "?volver=");
    }

    @Test
    void sin_sesion_ni_institucion_recordada_la_raiz_pide_la_direccion_del_colegio_sin_listar_instituciones() {
        Instalacion.sembrar();
        ClienteApp.Respuesta raiz = new ClienteApp().get("/");
        assertThat(raiz.estado()).isEqualTo(200);
        assertThat(raiz.cuerpo()).contains("dirección de tu colegio").doesNotContain(Instalacion.RUTA).doesNotContain("Institución de pruebas");
        ClienteApp.Respuesta catalogo = new ClienteApp().get("/catalogo");
        assertThat(catalogo.cabecera("Location").orElse("")).as("sin institución recordada, a la raíz").matches(".*//[^/]+/$|^/$");
    }

    @Test
    void una_ruta_de_institucion_que_no_existe_responde_404_sin_cuerpo() {
        ClienteApp.Respuesta r = new ClienteApp().get("/colegio-que-no-existe");
        assertThat(r.estado()).isEqualTo(404);
        assertThat(r.cuerpo()).isEmpty();
    }

    @Test
    void las_cabeceras_de_seguridad_llegan_al_navegador() {
        ClienteApp.Respuesta bloqueo = new ClienteApp().get("/");
        assertThat(bloqueo.cabecera("Content-Security-Policy").orElse("")).contains("script-src 'self'").doesNotContain("unsafe");
        assertThat(bloqueo.cabecera("X-Frame-Options").orElse("")).isEqualToIgnoringCase("DENY");
        assertThat(bloqueo.cabecera("X-Content-Type-Options").orElse("")).isEqualToIgnoringCase("nosniff");
    }
}
