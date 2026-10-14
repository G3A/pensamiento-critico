package pensamiento.aceptacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

import pensamiento.testutil.BaseDatosDePrueba;
import pensamiento.web.usuarios.CodigoDeUnUso;

/**
 * Hito 9 · Puerta de entrada, por HTTP contra app-prueba: la entrada no lista nombres ni con 1000 cuentas; alta con código
 * de un uso (410 al segundo canje y al vencido, solo el hash en la base); restablecer cierra la sesión vieja (401); cambiar
 * exige la actual; el sexto intento recibe 429 aunque la contraseña sea correcta; todo queda en la auditoría; el overlay de A
 * rechaza a B; quien tenía PIN elige su contraseña antes de seguir; "¿No tienes cuenta?" con el texto de la institución.
 */
class PuertaDeEntradaIT {

    private final BaseDatosDePrueba bd = new BaseDatosDePrueba();

    private UUID institucionDePrueba() {
        Instalacion.sembrar();
        return bd.jdbcAdmin().sql("SELECT id FROM institucion WHERE ruta = :r").param("r", Instalacion.RUTA).query(UUID.class).single();
    }

    private UUID idDe(String nombre) {
        return bd.jdbcAdmin().sql("SELECT id FROM usuario WHERE institucion_id = :i AND nombre = :n")
                .param("i", institucionDePrueba()).param("n", nombre).query(UUID.class).single();
    }

    @Test
    void la_entrada_anonima_de_una_institucion_con_1000_cuentas_no_muestra_ningun_nombre() {
        String ruta = "mil-cuentas-" + UUID.randomUUID().toString().substring(0, 8);
        UUID institucion = bd.crearInstitucion("Colegio de mil", ruta);
        try {
            bd.jdbcAdmin().sql("""
                    INSERT INTO usuario (institucion_id, nombre, clave_hash, tipo_clave, rol_global)
                    SELECT :i, 'sembrada ' || lpad(n::text, 4, '0'), 'x', 'contrasena', 'persona' FROM generate_series(1, 1000) n""")
                    .param("i", institucion).update();
            ClienteApp.Respuesta entrada = new ClienteApp(ruta).get("/" + ruta);
            assertThat(entrada.estado()).isEqualTo(200);
            assertThat(entrada.cuerpo()).doesNotContain("sembrada");
            assertThat(new ClienteApp(ruta).get("/" + ruta + "/sin-cuenta").cuerpo()).doesNotContain("sembrada");
            assertThat(new ClienteApp(ruta).get("/" + ruta + "/codigo").cuerpo()).doesNotContain("sembrada");
        } finally {
            bd.borrarInstitucion(institucion);
        }
    }

    @Test
    void el_codigo_de_alta_sirve_una_vez_y_en_la_base_solo_esta_su_hash() {
        ClienteApp admin = Instalacion.administrador();
        String nombre = "alta " + UUID.randomUUID().toString().substring(0, 8);
        String codigo = Instalacion.codigoDeAlta(admin, nombre);

        List<String> guardados = bd.jdbcAdmin().sql("SELECT codigo_hash FROM codigo_cuenta WHERE usuario_id = :u").param("u", idDe(nombre))
                .query(String.class).list();
        assertThat(guardados).containsExactly(CodigoDeUnUso.hash(codigo.replace("-", "")));
        assertThat(bd.jdbcAdmin().sql("SELECT count(*) FROM codigo_cuenta WHERE codigo_hash LIKE :c").param("c", "%" + codigo.replace("-", "") + "%")
                .query(Long.class).single()).isZero();

        assertThat(Instalacion.canjear(Instalacion.RUTA, codigo, "frase de prueba 2468").estado()).isEqualTo(302);
        ClienteApp.Respuesta otraVez = Instalacion.canjear(Instalacion.RUTA, codigo, "otra frase de prueba");
        assertThat(otraVez.estado()).isEqualTo(410);
        assertThat(otraVez.cuerpo()).contains("Ese código no sirve");
        assertThat(Instalacion.entraComo(nombre, "2468").get("/").estado()).isEqualTo(200);
        String hash = bd.jdbcAdmin().sql("SELECT clave_hash FROM usuario WHERE id = :u").param("u", idDe(nombre)).query(String.class).single();
        assertThat(hash).startsWith("$argon2").doesNotContain("frase de prueba");
    }

    @Test
    void un_codigo_vencido_no_sirve_y_una_contrasena_corta_no_lo_gasta() {
        ClienteApp admin = Instalacion.administrador();
        String nombre = "vence " + UUID.randomUUID().toString().substring(0, 8);
        String codigo = Instalacion.codigoDeAlta(admin, nombre);
        ClienteApp.Respuesta corta = Instalacion.canjear(Instalacion.RUTA, codigo, "corta");
        assertThat(corta.estado()).isEqualTo(422);
        assertThat(Jsoup.parse(corta.cuerpo()).select("input[name=codigo]").attr("value")).as("el código se conserva").isEqualTo(codigo);
        bd.jdbcAdmin().sql("UPDATE codigo_cuenta SET caduca_en = now() - interval '1 minute' WHERE usuario_id = :u").param("u", idDe(nombre)).update();
        assertThat(Instalacion.canjear(Instalacion.RUTA, codigo, "frase de prueba 1357").estado()).isEqualTo(410);
    }

    @Test
    void restablecer_cierra_la_sesion_vieja_con_401_y_el_codigo_nuevo_fija_otra_contrasena() {
        ClienteApp admin = Instalacion.administrador();
        String nombre = Instalacion.personaNueva(admin, "olvidadiza", "1357");
        ClienteApp sesionVieja = Instalacion.entraComo(nombre, "1357");
        assertThat(sesionVieja.get("/").estado()).isEqualTo(200);

        ClienteApp.Respuesta tarjeta = admin.postFormulario("/usuarios/" + idDe(nombre) + "/restablecer", Map.of());
        assertThat(tarjeta.estado()).isEqualTo(200);
        String codigo = Instalacion.codigoDeLaTarjeta(tarjeta);

        assertThat(sesionVieja.get("/").estado()).as("la sesión vieja recibe 401").isEqualTo(401);
        assertThat(sesionVieja.get("/").cuerpo()).as("y ya no tiene sesión").doesNotContain("¿Qué quieres hacer hoy?");
        assertThat(Instalacion.canjear(Instalacion.RUTA, codigo, "frase de prueba 9999").estado()).isEqualTo(302);
        assertThat(new ClienteApp().entrar(nombre, Instalacion.contrasenaDe("1357")).estado()).as("la contraseña vieja ya no sirve").isEqualTo(401);
        assertThat(Instalacion.entraComo(nombre, "9999").get("/").estado()).isEqualTo(200);
        assertThat(acciones(idDe(nombre))).contains("codigo_restablecer", "canjear_codigo");
    }

    @Test
    void cambiar_la_contrasena_exige_la_actual_y_queda_en_la_auditoria() {
        ClienteApp admin = Instalacion.administrador();
        String nombre = Instalacion.personaNueva(admin, "cambiante", "1357");
        ClienteApp persona = Instalacion.entraComo(nombre, "1357");
        ClienteApp.Respuesta sinActual = persona.postFormulario("/cuenta/contrasena",
                Map.of("actual", "no es esta", "nueva", "frase de prueba 7531", "repetida", "frase de prueba 7531"));
        assertThat(sinActual.estado()).isEqualTo(422);
        assertThat(sinActual.cuerpo()).contains("La contraseña actual no coincide");
        ClienteApp.Respuesta bien = persona.postFormulario("/cuenta/contrasena",
                Map.of("actual", Instalacion.contrasenaDe("1357"), "nueva", "frase de prueba 7531", "repetida", "frase de prueba 7531"));
        assertThat(bien.estado()).isEqualTo(302);
        assertThat(Instalacion.entraComo(nombre, "7531").get("/").estado()).isEqualTo(200);
        assertThat(acciones(idDe(nombre))).contains("cambiar_clave");
    }

    @Test
    void desactivar_cierra_la_sesion_y_reactivar_deja_entrar_de_nuevo() {
        ClienteApp admin = Instalacion.administrador();
        String nombre = Instalacion.personaNueva(admin, "pausada", "1357");
        ClienteApp persona = Instalacion.entraComo(nombre, "1357");
        assertThat(admin.postFormulario("/usuarios/" + idDe(nombre) + "/desactivar", Map.of()).estado()).isEqualTo(302);
        assertThat(persona.get("/").estado()).isEqualTo(401);
        assertThat(new ClienteApp().entrar(nombre, Instalacion.contrasenaDe("1357")).estado()).isEqualTo(401);
        assertThat(admin.postFormulario("/usuarios/" + idDe(nombre) + "/reactivar", Map.of()).estado()).isEqualTo(302);
        assertThat(Instalacion.entraComo(nombre, "1357").get("/").estado()).isEqualTo(200);
    }

    @Test
    void el_sexto_intento_recibe_429_aunque_la_contrasena_sea_correcta_y_el_administrador_levanta_la_espera() {
        ClienteApp admin = Instalacion.administrador();
        String nombre = Instalacion.personaNueva(admin, "insistente", "1357");
        for (int i = 0; i < 5; i++) {
            assertThat(new ClienteApp().entrar(nombre, "no es la contraseña " + i).estado()).isEqualTo(401);
        }
        ClienteApp.Respuesta sexto = new ClienteApp().entrar(nombre, Instalacion.contrasenaDe("1357"));
        assertThat(sexto.estado()).isEqualTo(429);
        assertThat(Jsoup.parse(sexto.cuerpo()).select("[role=alert]").text()).contains("Espera").containsPattern("\\d+ segundos");

        Document usuarios = Jsoup.parse(admin.get("/usuarios").cuerpo());
        String clave = nombre.toLowerCase();
        assertThat(usuarios.select(".esperas li").text()).contains(clave);
        assertThat(admin.postFormulario("/usuarios/espera", Map.of("nombre", clave)).estado()).isEqualTo(302);
        assertThat(Instalacion.entraComo(nombre, "1357").get("/").estado()).isEqualTo(200);
        assertThat(acciones(idDe(nombre))).contains("fallo_entrar", "entrar");
        assertThat(bd.jdbcAdmin().sql("SELECT count(*) FROM auditoria WHERE accion = 'espera_entrar' AND detalle = :d").param("d", clave)
                .query(Long.class).single()).isPositive();
        assertThat(bd.jdbcAdmin().sql("SELECT count(*) FROM auditoria WHERE detalle LIKE '%no es la contraseña%' OR detalle LIKE '%frase de prueba%'")
                .query(Long.class).single()).as("la auditoría nunca guarda una contraseña").isZero();
    }

    @Test
    void entrar_tarda_lo_mismo_exista_o_no_la_cuenta() {
        ClienteApp admin = Instalacion.administrador();
        String nombre = Instalacion.personaNueva(admin, "cronometrada", "1357");
        List<Long> existe = new ArrayList<>();
        List<Long> noExiste = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            existe.add(medir(() -> new ClienteApp().entrar(nombre, "no es la contraseña")));
            noExiste.add(medir(() -> new ClienteApp().entrar("nadie " + UUID.randomUUID(), "no es la contraseña")));
        }
        Collections.sort(existe);
        Collections.sort(noExiste);
        double razon = (double) existe.get(2) / noExiste.get(2);
        assertThat(razon).as("mediana con cuenta / mediana sin cuenta (%s / %s ms)", existe, noExiste).isBetween(0.5, 2.0);
    }

    @Test
    void en_el_overlay_de_a_la_persona_b_es_rechazada_y_la_pagina_de_a_no_se_procesa_con_la_sesion_de_b() {
        ClienteApp admin = Instalacion.administrador();
        String a = Instalacion.personaNueva(admin, "persona A", "1357");
        String b = Instalacion.personaNueva(admin, "persona B", "2468");
        String idA = idDe(a).toString();

        ClienteApp navegador = new ClienteApp();
        navegador.tomarCsrf();
        ClienteApp.Respuesta bSobreA = navegador.postHtmxDesdeLaPaginaDe(idA, "/sesion", Map.of("nombre", b,
                "contrasena", Instalacion.contrasenaDe("2468"), "institucion", Instalacion.RUTA, "persona", idA));
        assertThat(bSobreA.estado()).isEqualTo(401);
        assertThat(bSobreA.cuerpo()).contains("Esta página es de otra persona");
        assertThat(bSobreA.cuerpo()).contains("name=\"persona\" value=\"" + idA + "\"");

        navegador.csrfDe(navegador.get("/" + Instalacion.RUTA));
        ClienteApp.Respuesta aVuelve = navegador.postHtmxDesdeLaPaginaDe(idA, "/sesion", Map.of("nombre", a,
                "contrasena", Instalacion.contrasenaDe("1357"), "institucion", Instalacion.RUTA, "persona", idA));
        assertThat(aVuelve.estado()).isEqualTo(200);
        assertThat(aVuelve.cabecera("HX-Trigger").orElse("")).contains("csrf-renovado");

        ClienteApp deB = Instalacion.entraComo(b, "2468");
        ClienteApp.Respuesta desdeLaPaginaDeA = deB.postHtmxDesdeLaPaginaDe(idA, "/expedientes", Map.of("nombre", "Borrador de A"));
        assertThat(desdeLaPaginaDeA.estado()).as("la página de A con la sesión de B").isEqualTo(401);
        assertThat(deB.get("/expedientes").cuerpo()).doesNotContain("Borrador de A");
    }

    @Test
    void quien_tenia_pin_entra_una_ultima_vez_y_elige_su_contrasena_antes_de_seguir() {
        UUID institucion = institucionDePrueba();
        String nombre = "con pin " + UUID.randomUUID().toString().substring(0, 8);
        bd.jdbcAdmin().sql("INSERT INTO usuario (institucion_id, nombre, clave_hash, tipo_clave, rol_global) VALUES (:i, :n, :h, 'pin', 'persona')")
                .param("i", institucion).param("n", nombre).param("h", Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8().encode("2468")).update();
        ClienteApp persona = new ClienteApp();
        ClienteApp.Respuesta entra = persona.entrar(nombre, "2468");
        assertThat(entra.estado()).isEqualTo(302);
        assertThat(entra.cabecera("Location").orElse("")).endsWith("/cuenta/contrasena-nueva");
        ClienteApp.Respuesta catalogo = persona.get("/catalogo");
        assertThat(catalogo.cabecera("Location").orElse("")).as("solo puede elegir su contraseña").endsWith("/cuenta/contrasena-nueva");
        persona.csrfDe(persona.get("/cuenta/contrasena-nueva"));
        ClienteApp.Respuesta elegida = persona.postFormulario("/cuenta/contrasena-nueva", Map.of("nueva", "frase de prueba 2468", "repetida", "frase de prueba 2468"));
        assertThat(elegida.estado()).isEqualTo(302);
        assertThat(persona.get("/catalogo").estado()).isEqualTo(200);
        assertThat(new ClienteApp().entrar(nombre, "2468").estado()).as("el PIN ya no sirve").isEqualTo(401);
        assertThat(Instalacion.entraComo(nombre, "2468").get("/").estado()).isEqualTo(200);
    }

    @Test
    void no_tienes_cuenta_explica_tres_pasos_con_el_texto_de_la_institucion_y_muestra_el_catalogo() {
        ClienteApp admin = Instalacion.administrador();
        assertThat(admin.postFormulario("/usuarios/quien-da-cuentas", Map.of("texto", "tu profe de filosofía")).estado()).isEqualTo(302);
        Document pagina = Jsoup.parse(new ClienteApp().get("/" + Instalacion.RUTA + "/sin-cuenta").cuerpo());
        assertThat(pagina.select("ol.pasos li")).hasSize(3);
        assertThat(pagina.select("ol.pasos li").first().text()).contains("tu profe de filosofía");
        assertThat(pagina.text()).contains("T28 · Análisis de hipótesis en competencia (ACH)");
        assertThat(pagina.select("a[href=/" + Instalacion.RUTA + "/codigo]")).isNotEmpty();
        admin.postFormulario("/usuarios/quien-da-cuentas", Map.of("texto", "quien administra la app en tu institución"));
    }

    private List<String> acciones(UUID usuario) {
        return bd.jdbcAdmin().sql("SELECT accion FROM auditoria WHERE objeto_id = :u OR usuario_id = :u").param("u", usuario).query(String.class).list();
    }

    private static long medir(Runnable accion) {
        long inicio = System.nanoTime();
        accion.run();
        return (System.nanoTime() - inicio) / 1_000_000;
    }
}
