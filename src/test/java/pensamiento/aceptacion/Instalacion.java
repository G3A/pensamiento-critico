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
     * Siembra (una vez por corrida) la institución de pruebas con su cuenta "administrador" y la contraseña generada en el volumen
     * secretos-prueba. Directo en db-prueba como administrador de la base: las instituciones nuevas no reciben cuenta sola.
     */
    public static synchronized void sembrar() {
        if (sembrada) {
            return;
        }
        sembrarInstitucion(RUTA, "Institución de pruebas");
        sembrada = true;
    }

    /** Siembra (o reactiva) una institución con su cuenta "administrador" y la contraseña generada; devuelve su id. */
    public static UUID sembrarInstitucion(String ruta, String nombre) {
        BaseDatosDePrueba bd = new BaseDatosDePrueba();
        UUID institucion = bd.jdbcAdmin().sql("""
                INSERT INTO institucion (nombre, ruta) VALUES (:nombre, :ruta)
                ON CONFLICT (ruta) DO UPDATE SET estado = 'activa' RETURNING id""").param("nombre", nombre).param("ruta", ruta)
                .query(UUID.class).single();
        String hash = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8().encode(Entorno.contrasenaAdministrador());
        bd.jdbcAdmin().sql("""
                INSERT INTO usuario (institucion_id, nombre, clave_hash, tipo_clave, rol_global)
                VALUES (:i, 'administrador', :h, 'contrasena', 'administrador')
                ON CONFLICT (institucion_id, nombre_clave) DO UPDATE SET clave_hash = EXCLUDED.clave_hash, tipo_clave = 'contrasena', activo = true""")
                .param("i", institucion).param("h", hash).update();
        // Su llave de acceso de una corrida anterior ya no tiene clave privada: se borra para registrar otra.
        bd.jdbcAdmin().sql("DELETE FROM llave_acceso WHERE usuario_id IN (SELECT id FROM usuario WHERE institucion_id = :i AND nombre = 'administrador')")
                .param("i", institucion).update();
        LLAVES.remove(ruta + "/administrador");
        return institucion;
    }

    /** El administrador de la institución de pruebas entra con la contraseña generada. */
    public static ClienteApp administrador() {
        sembrar();
        return administradorDe(RUTA);
    }

    /** El administrador de una institución sembrada con sembrarInstitucion entra por su ruta y confirma con su llave. */
    public static ClienteApp administradorDe(String ruta) {
        ClienteApp admin = new ClienteApp(ruta);
        ClienteApp.Respuesta r = admin.entrar("administrador", Entorno.contrasenaAdministrador());
        assertThat(r.estado()).as("el administrador entra").isEqualTo(302);
        assertThat(r.cabecera("Location").orElse("")).as("y le pide su llave de acceso").endsWith("/llave");
        completarLlave(admin, "/llave", ruta + "/administrador");
        return admin;
    }

    /** Las llaves de acceso de prueba de esta corrida, por titular ("ruta/nombre" u "operador"). */
    private static final java.util.Map<String, pensamiento.testutil.AutenticadorDePrueba> LLAVES = new java.util.concurrent.ConcurrentHashMap<>();
    private static boolean operadorSinLlaves;

    /**
     * Completa la etapa de la llave de acceso de quien acaba de dar su contraseña: la registra si todavía no tiene (y se
     * guarda el autenticador de prueba de este titular) o firma con el que ya registró en esta corrida.
     */
    public static void completarLlave(ClienteApp cliente, String base, String titular) {
        ClienteApp.Respuesta pagina = cliente.get(base);
        cliente.csrfDe(pagina);
        String etapa = org.jsoup.Jsoup.parse(pagina.cuerpo()).select("[data-llave]").attr("data-etapa");
        pensamiento.testutil.AutenticadorDePrueba llave;
        ClienteApp.Respuesta resultado;
        if ("registrar".equals(etapa)) {
            llave = new pensamiento.testutil.AutenticadorDePrueba();
            ClienteApp.Respuesta opciones = cliente.postJson(base + "/registro/opciones", "");
            assertThat(opciones.estado()).as("opciones de registro de " + titular).isEqualTo(200);
            resultado = cliente.postJson(base + "/registro", llave.registrar(opciones.cuerpo()));
            LLAVES.put(titular, llave);
        } else {
            assertThat(etapa).as("etapa de la llave de " + titular).isEqualTo("confirmar");
            llave = LLAVES.get(titular);
            assertThat(llave).as("la llave de " + titular + " se registró en esta corrida").isNotNull();
            ClienteApp.Respuesta opciones = cliente.postJson(base + "/confirmacion/opciones", "");
            resultado = cliente.postJson(base + "/confirmacion", llave.firmar(opciones.cuerpo()));
        }
        assertThat(resultado.estado()).as("la llave de " + titular + ": " + resultado.cuerpo()).isEqualTo(200);
        String ir = resultado.cuerpo().replaceAll(".*\"ir\"\s*:\s*\"([^\"]+)\".*", "$1");
        cliente.csrfDe(cliente.get(ir));
    }

    /**
     * Antes de la primera entrada del operador en cada corrida se borran sus llaves: la clave privada de una corrida anterior
     * no sobrevive, así que registra una nueva.
     */
    static synchronized void prepararOperador() {
        if (!operadorSinLlaves) {
            BaseDatosDePrueba bd = new BaseDatosDePrueba();
            bd.jdbcAdmin().sql("DELETE FROM llave_acceso WHERE operador_id IS NOT NULL").update();
            bd.jdbcAdmin().sql("DELETE FROM codigo_recuperacion WHERE operador_id IS NOT NULL").update();
            LLAVES.remove("operador");
            operadorSinLlaves = true;
        }
    }

    /**
     * La contraseña de prueba que corresponde a una "seña" corta (las pruebas anteriores al hito 9 daban un PIN de 4 dígitos):
     * una frase de 10 caracteres o más que no está en la lista de contraseñas comunes.
     */
    public static String contrasenaDe(String sena) {
        return Entorno.contrasenaAdministrador().equals(sena) ? sena : "frase de prueba " + sena;
    }

    /** El administrador crea una persona con nombre único; la persona canjea su código y elige su contraseña. Devuelve el nombre. */
    public static String personaNueva(ClienteApp admin, String rol, String sena) {
        String nombre = rol + " " + UUID.randomUUID().toString().substring(0, 8);
        personaLlamada(admin, nombre, sena);
        return nombre;
    }

    /** Una persona entra con su contraseña y queda con sesión abierta. */
    public static ClienteApp entraComo(String nombre, String sena) {
        sembrar();
        return entraComo(RUTA, nombre, sena);
    }

    /** Una persona entra por la ruta de su institución. */
    public static ClienteApp entraComo(String ruta, String nombre, String sena) {
        ClienteApp cliente = new ClienteApp(ruta);
        ClienteApp.Respuesta r = cliente.entrar(nombre, contrasenaDe(sena));
        assertThat(r.estado()).as(nombre + " entra").isEqualTo(302);
        assertThat(r.cabecera("Location").orElse("")).as("vuelve al inicio").endsWith("/");
        return cliente;
    }

    /** El administrador crea una persona con exactamente ese nombre y ella canjea su código con la contraseña de esa seña. */
    public static void personaLlamada(ClienteApp admin, String nombre, String sena) {
        String codigo = codigoDeAlta(admin, nombre);
        ClienteApp.Respuesta canje = canjear(admin.ruta(), codigo, contrasenaDe(sena));
        assertThat(canje.estado()).as("canjear el código de " + nombre).isEqualTo(302);
    }

    private static final java.util.regex.Pattern CODIGO = java.util.regex.Pattern.compile("class=\"codigo-grande\"[^>]*>([0-9A-Z]{4}-[0-9A-Z]{4})<");

    /** El administrador crea la cuenta (201) y lee el código de alta de la tarjeta, que se muestra una sola vez. */
    public static String codigoDeAlta(ClienteApp admin, String nombre) {
        return codigoDeAlta(admin, nombre, "persona");
    }

    /** Lo mismo con un rol: persona, docente, estudiante o administrador. */
    public static String codigoDeAlta(ClienteApp admin, String nombre, String rol) {
        ClienteApp.Respuesta r = admin.postFormulario("/usuarios", Map.of("nombre", nombre, "rol", rol));
        assertThat(r.estado()).as("crear la cuenta de " + nombre).isEqualTo(201);
        return codigoDeLaTarjeta(r);
    }

    public static String codigoDeLaTarjeta(ClienteApp.Respuesta tarjeta) {
        java.util.regex.Matcher m = CODIGO.matcher(tarjeta.cuerpo());
        assertThat(m.find()).as("la tarjeta trae el código").isTrue();
        return m.group(1);
    }

    /**
     * Hito 10: el administrador crea un docente con ese nombre; el docente canjea su código, entra y registra su llave de
     * acceso. Devuelve su cliente con la sesión lista.
     */
    public static ClienteApp docenteNuevo(ClienteApp admin, String nombre) {
        String codigo = codigoDeAlta(admin, nombre, "docente");
        assertThat(canjear(admin.ruta(), codigo, contrasenaDe("docente")).estado()).as("el docente canjea su código").isEqualTo(302);
        ClienteApp docente = new ClienteApp(admin.ruta());
        ClienteApp.Respuesta r = docente.entrar(nombre, contrasenaDe("docente"));
        assertThat(r.cabecera("Location").orElse("")).as(nombre + " confirma con su llave").endsWith("/llave");
        completarLlave(docente, "/llave", admin.ruta() + "/" + nombre);
        return docente;
    }

    /** Hito 10: el administrador crea un estudiante suelto (sin grupo) que canjea su código; devuelve su cliente ya dentro. */
    public static ClienteApp estudianteNuevo(ClienteApp admin, String nombre) {
        String codigo = codigoDeAlta(admin, nombre, "estudiante");
        assertThat(canjear(admin.ruta(), codigo, contrasenaDe("estudiante")).estado()).isEqualTo(302);
        return entraComo(admin.ruta(), nombre, "estudiante");
    }

    /** Alguien sin sesión canjea un código en la entrada de esa institución y elige su contraseña. */
    public static ClienteApp.Respuesta canjear(String ruta, String codigo, String contrasena) {
        ClienteApp anonimo = new ClienteApp(ruta);
        anonimo.tomarCsrf();
        return anonimo.postFormulario("/" + ruta + "/codigo", Map.of("codigo", codigo, "contrasena", contrasena, "repetida", contrasena));
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
