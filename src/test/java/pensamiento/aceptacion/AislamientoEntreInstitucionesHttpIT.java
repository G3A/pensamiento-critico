package pensamiento.aceptacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Hito 8, entregable 6 (condición de salida), por HTTP: dos instituciones conviven y cada una entra por su ruta. Una sesión
 * abierta en /<a> es rechazada al pedir /<b> o al intentar entrar en b, y el expediente de una persona de A da 404 a una
 * persona de B. El mismo nombre existe en las dos instituciones sin chocar.
 */
class AislamientoEntreInstitucionesHttpIT {

    private final String rutaA = "aisla-a-" + UUID.randomUUID().toString().substring(0, 8);
    private final String rutaB = "aisla-b-" + UUID.randomUUID().toString().substring(0, 8);

    @Test
    void cada_institucion_entra_por_su_ruta_y_ninguna_ve_lo_de_la_otra() {
        Instalacion.sembrarInstitucion(rutaA, "Colegio A");
        Instalacion.sembrarInstitucion(rutaB, "Colegio B");
        String nombre = "rectora " + UUID.randomUUID().toString().substring(0, 8);
        Instalacion.personaLlamada(Instalacion.administradorDe(rutaA), nombre, "1357");
        Instalacion.personaLlamada(Instalacion.administradorDe(rutaB), nombre, "2468");

        ClienteApp deA = Instalacion.entraComo(rutaA, nombre, "1357");
        ClienteApp deB = Instalacion.entraComo(rutaB, nombre, "2468");
        UUID expedienteDeA = Instalacion.creaExpediente(deA, "Plan secreto de A");

        assertThat(deA.get("/expedientes/" + expedienteDeA).estado()).as("A ve su expediente").isEqualTo(200);
        ClienteApp.Respuesta desdeB = deB.get("/expedientes/" + expedienteDeA);
        assertThat(desdeB.estado()).as("el expediente de A desde B").isEqualTo(404);
        assertThat(desdeB.cuerpo()).doesNotContain("Plan secreto de A");
        assertThat(deB.get("/expedientes").cuerpo()).doesNotContain("Plan secreto de A");

        ClienteApp.Respuesta otraRuta = deA.get("/" + rutaB);
        assertThat(otraRuta.estado()).as("la sesión de A pide la entrada de B").isEqualTo(403);
        assertThat(otraRuta.cuerpo()).isEmpty();
        ClienteApp.Respuesta entrarEnB = deA.postFormulario("/sesion", Map.of("nombre", nombre, "contrasena", Instalacion.contrasenaDe("2468"), "institucion", rutaB, "volver", "/"));
        assertThat(entrarEnB.estado()).as("la sesión de A intenta entrar en B").isEqualTo(403);
        assertThat(deA.get("/expedientes/" + expedienteDeA).estado()).as("A sigue en A").isEqualTo(200);

        assertThat(deA.get("/" + rutaA).cabecera("Location").orElse("")).as("su propia ruta lo lleva al inicio").endsWith("/");
    }

    @Test
    void el_pin_de_una_institucion_no_abre_la_cuenta_del_mismo_nombre_en_otra() {
        Instalacion.sembrarInstitucion(rutaA, "Colegio A");
        Instalacion.sembrarInstitucion(rutaB, "Colegio B");
        String nombre = "vecina " + UUID.randomUUID().toString().substring(0, 8);
        Instalacion.personaLlamada(Instalacion.administradorDe(rutaA), nombre, "1357");
        Instalacion.personaLlamada(Instalacion.administradorDe(rutaB), nombre, "2468");

        ClienteApp intruso = new ClienteApp(rutaB);
        ClienteApp.Respuesta r = intruso.entrar(nombre, Instalacion.contrasenaDe("1357"));
        assertThat(r.estado()).isEqualTo(401);
        assertThat(intruso.get("/").cuerpo()).doesNotContain("¿Qué quieres hacer hoy?");
    }

    @Test
    void una_institucion_suspendida_responde_404_en_su_entrada() {
        UUID id = Instalacion.sembrarInstitucion(rutaA, "Colegio A");
        new pensamiento.testutil.BaseDatosDePrueba().jdbcAdmin().sql("UPDATE institucion SET estado = 'suspendida' WHERE id = :i")
                .param("i", id).update();
        ClienteApp.Respuesta r = new ClienteApp(rutaA).get("/" + rutaA);
        assertThat(r.estado()).isEqualTo(404);
        assertThat(r.cuerpo()).isEmpty();
    }
}
