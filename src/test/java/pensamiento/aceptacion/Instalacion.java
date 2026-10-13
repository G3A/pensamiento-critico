package pensamiento.aceptacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

import pensamiento.testutil.BaseDatosDePrueba;
import pensamiento.testutil.Entorno;

/** DSL de la aceptación (capa 2 de Farley): verbos del dominio sobre el driver HTTP. */
public final class Instalacion {

    private Instalacion() {
    }

    /** La institución de la suite en db-prueba; su entrada es /prueba. */
    public static final String RUTA = "prueba";

    private static boolean sembrada;

    /**
     * Siembra (una vez por corrida) la institución de pruebas con su cuenta "administrador" y el PIN generado en el volumen
     * secretos-prueba. Directo en db-prueba como administrador de la base: las instituciones nuevas no reciben cuenta sola.
     */
    public static synchronized void sembrar() {
        if (sembrada) {
            return;
        }
        sembrarInstitucion(RUTA, "Institución de pruebas");
        sembrada = true;
    }

    /** Siembra (o reactiva) una institución con su cuenta "administrador" y el PIN generado; devuelve su id. */
    public static UUID sembrarInstitucion(String ruta, String nombre) {
        BaseDatosDePrueba bd = new BaseDatosDePrueba();
        UUID institucion = bd.jdbcAdmin().sql("""
                INSERT INTO institucion (nombre, ruta) VALUES (:nombre, :ruta)
                ON CONFLICT (ruta) DO UPDATE SET estado = 'activa' RETURNING id""").param("nombre", nombre).param("ruta", ruta)
                .query(UUID.class).single();
        String hash = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8().encode(Entorno.pinAdministrador());
        bd.jdbcAdmin().sql("""
                INSERT INTO usuario (institucion_id, nombre, pin_hash, rol_global) VALUES (:i, 'administrador', :h, 'administrador')
                ON CONFLICT (institucion_id, nombre_clave) DO UPDATE SET pin_hash = EXCLUDED.pin_hash, activo = true""")
                .param("i", institucion).param("h", hash).update();
        return institucion;
    }

    /** El administrador de la institución de pruebas entra con el PIN generado. */
    public static ClienteApp administrador() {
        sembrar();
        return administradorDe(RUTA);
    }

    /** El administrador de una institución sembrada con sembrarInstitucion entra por su ruta. */
    public static ClienteApp administradorDe(String ruta) {
        ClienteApp admin = new ClienteApp(ruta);
        ClienteApp.Respuesta r = admin.entrar("administrador", Entorno.pinAdministrador());
        assertThat(r.estado()).as("el administrador entra").isEqualTo(302);
        return admin;
    }

    /** El administrador crea una persona con nombre único y PIN; devuelve el nombre. */
    public static String personaNueva(ClienteApp admin, String rol, String pin) {
        String nombre = rol + " " + UUID.randomUUID().toString().substring(0, 8);
        ClienteApp.Respuesta r = admin.postFormulario("/usuarios", Map.of("nombre", nombre, "pin", pin));
        assertThat(r.estado()).as("crear persona " + nombre).isEqualTo(303);
        return nombre;
    }

    /** Una persona entra con su PIN y queda con sesión abierta. */
    public static ClienteApp entraComo(String nombre, String pin) {
        sembrar();
        return entraComo(RUTA, nombre, pin);
    }

    /** Una persona entra por la ruta de su institución. */
    public static ClienteApp entraComo(String ruta, String nombre, String pin) {
        ClienteApp cliente = new ClienteApp(ruta);
        ClienteApp.Respuesta r = cliente.entrar(nombre, pin);
        assertThat(r.estado()).as(nombre + " entra").isEqualTo(302);
        assertThat(r.cabecera("Location").orElse("")).as("vuelve al inicio").endsWith("/");
        return cliente;
    }

    /** El administrador crea una persona con exactamente ese nombre. */
    public static void personaLlamada(ClienteApp admin, String nombre, String pin) {
        ClienteApp.Respuesta r = admin.postFormulario("/usuarios", Map.of("nombre", nombre, "pin", pin));
        assertThat(r.estado()).as("crear persona " + nombre).isEqualTo(303);
    }

    /** El health con detalles: desde el hito 8 solo lo ve el operador del servicio. */
    public static String saludComoOperador() {
        ClienteApp operador = new ClienteApp();
        operador.entrarComoOperador(Entorno.claveOperador());
        return operador.get("/actuator/health").cuerpo();
    }

    /** Crea un expediente y devuelve su identificador. */
    public static UUID creaExpediente(ClienteApp persona, String nombre) {
        ClienteApp.Respuesta r = persona.postFormulario("/expedientes", Map.of("nombre", nombre));
        assertThat(r.estado()).as("crear expediente").isEqualTo(201);
        String location = r.cabecera("Location").orElseThrow();
        return UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
    }
}
