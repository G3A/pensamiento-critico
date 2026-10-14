package pensamiento.aceptacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

import pensamiento.testutil.BaseDatosDePrueba;
import pensamiento.testutil.Entorno;
import pensamiento.testutil.SemillaDeInstitucion;

/**
 * Hito 10, entregable 1, por HTTP: el docente de una estudiante, con su sesión abierta y su llave confirmada, pide por id cada
 * objeto de ella (expediente, ejecución, argumento, sesión del Consejero, documento, decisión, verificación) y recibe 404, y
 * ninguna respuesta trae el texto centinela que ella escribió.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RlsPorRolHttpIT {

    private final BaseDatosDePrueba bd = new BaseDatosDePrueba();
    private SemillaDeInstitucion estudiante;
    private ClienteApp docente;

    @BeforeAll
    void sembrar() {
        estudiante = new SemillaDeInstitucion(bd);
        String hash = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8().encode(Entorno.contrasenaAdministrador());
        UUID id = bd.jdbcAdmin().sql("""
                INSERT INTO usuario (institucion_id, nombre, clave_hash, tipo_clave, rol_global)
                VALUES (:i, 'docente', :h, 'contrasena', 'docente') RETURNING id""")
                .param("i", estudiante.institucion()).param("h", hash).query(UUID.class).single();
        bd.jdbcAdmin().sql("UPDATE grupo SET docente_id = :d WHERE id = :g").param("d", id).param("g", estudiante.id("grupo")).update();
        docente = new ClienteApp(estudiante.ruta());
        ClienteApp.Respuesta r = docente.entrar("docente", Entorno.contrasenaAdministrador());
        assertThat(r.cabecera("Location").orElse("")).as("el docente confirma con su llave").endsWith("/llave");
        Instalacion.completarLlave(docente, "/llave", estudiante.ruta() + "/docente");
    }

    @AfterAll
    void limpiar() {
        bd.jdbcAdmin().sql("DELETE FROM llave_acceso WHERE usuario_id IN (SELECT id FROM usuario WHERE institucion_id = :i)")
                .param("i", estudiante.institucion()).update();
        bd.jdbcAdmin().sql("DELETE FROM codigo_recuperacion WHERE usuario_id IN (SELECT id FROM usuario WHERE institucion_id = :i)")
                .param("i", estudiante.institucion()).update();
        estudiante.borrar();
    }

    @Test
    void el_docente_recibe_404_por_cada_objeto_de_su_estudiante_y_nunca_su_texto() {
        Map<String, String> rutas = Map.of(
                "expediente", "/expedientes/" + estudiante.id("expediente"),
                "ejecución", "/ejecuciones/" + estudiante.id("ejecucion"),
                "argumento", "/argumentos/" + estudiante.id("argumento"),
                "sesión del Consejero", "/consejero/sesiones/" + estudiante.id("sesion_consejero"),
                "documento", "/biblioteca/" + estudiante.id("documento") + "/original",
                "decisión", "/diario/decisiones/" + estudiante.id("ejecucion"),
                "verificación", "/verificar/" + estudiante.id("afirmacion"));

        rutas.forEach((que, ruta) -> {
            ClienteApp.Respuesta r = docente.get(ruta);
            assertThat(r.estado()).as("%s de la estudiante pedido por su docente (%s)", que, ruta).isEqualTo(404);
            assertThat(r.cuerpo()).as("%s: sin el texto de la estudiante", que).doesNotContain(estudiante.centinela());
        });
    }

    @Test
    void la_biblioteca_y_el_inicio_del_docente_no_muestran_el_documento_compartido_de_la_estudiante() {
        assertThat(docente.get("/biblioteca").cuerpo()).doesNotContain(estudiante.centinela());
        assertThat(docente.get("/").cuerpo()).doesNotContain(estudiante.centinela());
    }
}
