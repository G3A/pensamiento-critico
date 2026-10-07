package pensamiento.aceptacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

/** RF-02: cuenta por persona con PIN obligatorio y sesión en servidor. Crear cuenta, bloquear, volver. */
class RF02CuentaConPinIT {

    @Test
    void el_administrador_crea_una_cuenta_la_persona_entra_bloquea_y_vuelve_a_entrar() {
        ClienteApp admin = Instalacion.administrador();
        String nombre = Instalacion.personaNueva(admin, "dueña de la panadería", "1357");

        ClienteApp persona = Instalacion.entraComo(nombre, "1357");
        ClienteApp.Respuesta inicio = persona.get("/");
        assertThat(inicio.estado()).isEqualTo(200);
        assertThat(inicio.cuerpo()).contains("¿Qué quieres hacer hoy?").contains(nombre);

        ClienteApp.Respuesta bloqueo = persona.bloquear();
        assertThat(bloqueo.estado()).isEqualTo(302);
        assertThat(persona.get("/").estado()).as("tras bloquear, el inicio ya no se ve").isEqualTo(302);

        ClienteApp.Respuesta deVuelta = persona.entrar(nombre, "1357");
        assertThat(deVuelta.estado()).isEqualTo(302);
        assertThat(persona.get("/").estado()).isEqualTo(200);
    }

    @Test
    void no_existe_cuenta_sin_pin() {
        ClienteApp admin = Instalacion.administrador();
        ClienteApp.Respuesta sinPin = admin.postFormulario("/usuarios", Map.of("nombre", "sin pin"));
        assertThat(sinPin.estado()).isEqualTo(422);
        ClienteApp.Respuesta pinCorto = admin.postFormulario("/usuarios", Map.of("nombre", "pin corto", "pin", "12"));
        assertThat(pinCorto.estado()).isEqualTo(422);
    }

    @Test
    void un_pin_equivocado_no_abre_sesion() {
        ClienteApp admin = Instalacion.administrador();
        String nombre = Instalacion.personaNueva(admin, "vecino", "2468");
        ClienteApp intruso = new ClienteApp();
        ClienteApp.Respuesta r = intruso.entrar(nombre, "0000");
        assertThat(r.estado()).isEqualTo(302);
        assertThat(r.cabecera("Location").orElse("")).contains("/bloqueo?error");
        assertThat(intruso.get("/").estado()).isEqualTo(302);
    }

    @Test
    void cuando_la_sesion_expira_en_medio_de_un_formulario_htmx_recibe_el_bloqueo_como_overlay_y_no_pierde_la_pagina() {
        // Una petición htmx sin sesión recibe 401 con el fragmento "¿Quién eres?" apuntado a #bloqueo:
        // la página (y el borrador) siguen en el navegador y al entrar se continúa donde se estaba.
        ClienteApp anonimo = new ClienteApp();
        ClienteApp.Respuesta r = anonimo.getHtmx("/catalogo?familia=F5");
        assertThat(r.estado()).isEqualTo(401);
        assertThat(r.cabecera("HX-Retarget").orElse("")).isEqualTo("#bloqueo");
        assertThat(r.cabecera("HX-Trigger").orElse("")).contains("csrf-renovado");
        assertThat(r.cuerpo()).contains("¿Quién eres?").contains("sigue en la página");
    }

    @Test
    void una_persona_sin_rol_administrador_no_puede_crear_cuentas() {
        ClienteApp admin = Instalacion.administrador();
        String nombre = Instalacion.personaNueva(admin, "estudiante", "1111");
        ClienteApp persona = Instalacion.entraComo(nombre, "1111");
        ClienteApp.Respuesta r = persona.postFormulario("/usuarios", Map.of("nombre", "colado", "pin", "2222"));
        assertThat(r.estado()).isEqualTo(403);
    }
}
