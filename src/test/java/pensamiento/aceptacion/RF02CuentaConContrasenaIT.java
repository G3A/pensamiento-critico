package pensamiento.aceptacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

/**
 * RF-02 y RF-02a (hito 9): cuenta por persona con contraseña y sesión en servidor; una sola forma de entrar, sin lista de
 * nombres; error genérico con eco y el nombre conservado; overlay de sesión expirada con el mismo formulario.
 */
class RF02CuentaConContrasenaIT {

    @Test
    void el_administrador_crea_una_cuenta_la_persona_entra_sale_y_vuelve_a_entrar() {
        ClienteApp admin = Instalacion.administrador();
        String nombre = Instalacion.personaNueva(admin, "dueña de la panadería", "1357");

        ClienteApp persona = Instalacion.entraComo(nombre, "1357");
        ClienteApp.Respuesta inicio = persona.get("/");
        assertThat(inicio.estado()).isEqualTo(200);
        assertThat(inicio.cuerpo()).contains("¿Qué quieres hacer hoy?").contains(nombre).contains(">Salir<").doesNotContain("Cambiar de persona");

        ClienteApp.Respuesta salir = persona.bloquear();
        assertThat(salir.estado()).isEqualTo(302);
        assertThat(salir.cabecera("Location").orElse("")).as("al salir vuelve a la entrada de su institución").endsWith("/" + Instalacion.RUTA);
        assertThat(persona.get("/").cuerpo()).as("tras salir, el inicio ya no se ve").doesNotContain("¿Qué quieres hacer hoy?").contains("dirección de tu colegio");

        ClienteApp.Respuesta deVuelta = persona.entrar(nombre.toUpperCase(), Instalacion.contrasenaDe("1357"));
        assertThat(deVuelta.estado()).as("el nombre no distingue mayúsculas").isEqualTo(302);
        assertThat(persona.get("/").estado()).isEqualTo(200);
    }

    @Test
    void la_entrada_es_un_solo_formulario_sin_nombres_con_enlaces_a_codigo_y_sin_cuenta() {
        Instalacion.sembrar();
        Document entrada = Jsoup.parse(new ClienteApp().get("/" + Instalacion.RUTA).cuerpo());
        assertThat(entrada.select("form[action=/sesion]")).hasSize(1);
        assertThat(entrada.select("form[action=/sesion] button")).as("un solo botón: Entrar").singleElement()
                .satisfies(b -> assertThat(b.text()).isEqualTo("Entrar"));
        assertThat(entrada.select("input[name=nombre]")).hasSize(1);
        assertThat(entrada.select("input[name=contrasena][type=password]")).hasSize(1);
        assertThat(entrada.text()).doesNotContain("administrador");
        assertThat(entrada.select("a[href=/" + Instalacion.RUTA + "/codigo]").text()).isEqualTo("Tengo un código");
        assertThat(entrada.select("a[href=/" + Instalacion.RUTA + "/sin-cuenta]").text()).isEqualTo("¿No tienes cuenta?");
    }

    @Test
    void una_contrasena_equivocada_responde_401_con_eco_y_el_nombre_conservado() {
        ClienteApp admin = Instalacion.administrador();
        String nombre = Instalacion.personaNueva(admin, "vecino", "2468");
        ClienteApp intruso = new ClienteApp();
        ClienteApp.Respuesta r = intruso.entrar(nombre, "no es la contraseña");
        assertThat(r.estado()).isEqualTo(401);
        Document pagina = Jsoup.parse(r.cuerpo());
        assertThat(pagina.select("[role=alert]").text()).isEqualTo("Probaste como «" + nombre + "». El nombre o la contraseña no coinciden.");
        assertThat(pagina.select("input[name=nombre]").attr("value")).isEqualTo(nombre);
        assertThat(pagina.select("input[name=contrasena]").attr("value")).isEmpty();
        assertThat(intruso.get("/").cuerpo()).doesNotContain("¿Qué quieres hacer hoy?");

        ClienteApp.Respuesta nadie = new ClienteApp().entrar("alguien que no existe", "no es la contraseña");
        assertThat(nadie.estado()).as("una cuenta que no existe responde igual").isEqualTo(401);
        assertThat(Jsoup.parse(nadie.cuerpo()).select("[role=alert]").text())
                .isEqualTo("Probaste como «alguien que no existe». El nombre o la contraseña no coinciden.");
    }

    @Test
    void un_nombre_repetido_responde_422_con_la_pagina_y_el_mensaje() {
        ClienteApp admin = Instalacion.administrador();
        String nombre = Instalacion.personaNueva(admin, "repetida", "1357");
        ClienteApp.Respuesta repetido = admin.postFormulario("/usuarios", Map.of("nombre", nombre.toUpperCase(), "rol", "persona"));
        assertThat(repetido.estado()).isEqualTo(422);
        assertThat(Jsoup.parse(repetido.cuerpo()).select("[role=alert]").text()).contains("Ya existe una persona llamada");
        assertThat(repetido.cuerpo()).doesNotContain("Whitelabel", "Exception");
        ClienteApp.Respuesta vacio = admin.postFormulario("/usuarios", Map.of("nombre", "  ", "rol", "persona"));
        assertThat(vacio.estado()).isEqualTo(422);
        assertThat(admin.postFormulario("/usuarios", Map.of("nombre", nombre + " bis", "rol", "persona")).estado())
                .as("después de un 422 se sigue creando").isEqualTo(201);
    }

    @Test
    void cuando_la_sesion_expira_en_medio_de_un_formulario_htmx_recibe_el_mismo_formulario_como_overlay() {
        ClienteApp anonimo = new ClienteApp();
        anonimo.tomarCsrf();
        ClienteApp.Respuesta r = anonimo.getHtmx("/catalogo?familia=F5");
        assertThat(r.estado()).isEqualTo(401);
        assertThat(r.cabecera("HX-Retarget").orElse("")).isEqualTo("#bloqueo");
        assertThat(r.cabecera("HX-Trigger").orElse("")).contains("csrf-renovado");
        Document overlay = Jsoup.parse(r.cuerpo());
        assertThat(overlay.text()).contains("Entra de nuevo para seguir").contains("sigue en la página");
        assertThat(overlay.select("input[name=contrasena]")).hasSize(1);
    }

    @Test
    void una_persona_sin_rol_administrador_no_puede_crear_cuentas() {
        ClienteApp admin = Instalacion.administrador();
        String nombre = Instalacion.personaNueva(admin, "estudiante", "1111");
        ClienteApp persona = Instalacion.entraComo(nombre, "1111");
        ClienteApp.Respuesta r = persona.postFormulario("/usuarios", Map.of("nombre", "colado", "rol", "persona"));
        assertThat(r.estado()).isEqualTo(403);
    }
}
