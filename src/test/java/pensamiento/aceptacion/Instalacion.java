package pensamiento.aceptacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import pensamiento.testutil.Entorno;

/** DSL de la aceptación (capa 2 de Farley): verbos del dominio sobre el driver HTTP. */
public final class Instalacion {

    private Instalacion() {
    }

    /** El administrador entra con el PIN del secreto. */
    public static ClienteApp administrador() {
        ClienteApp admin = new ClienteApp();
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
        ClienteApp cliente = new ClienteApp();
        ClienteApp.Respuesta r = cliente.entrar(nombre, pin);
        assertThat(r.estado()).as(nombre + " entra").isEqualTo(302);
        assertThat(r.cabecera("Location").orElse("")).as("vuelve al inicio").endsWith("/");
        return cliente;
    }

    /** Crea un expediente y devuelve su identificador. */
    public static UUID creaExpediente(ClienteApp persona, String nombre) {
        ClienteApp.Respuesta r = persona.postFormulario("/expedientes", Map.of("nombre", nombre));
        assertThat(r.estado()).as("crear expediente").isEqualTo(201);
        String location = r.cabecera("Location").orElseThrow();
        return UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
    }
}
