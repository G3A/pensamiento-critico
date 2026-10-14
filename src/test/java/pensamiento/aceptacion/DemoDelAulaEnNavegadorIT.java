package pensamiento.aceptacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.Cookie;
import org.junit.jupiter.api.Test;

import pensamiento.testutil.BaseDatosDePrueba;
import pensamiento.testutil.ReenvioLocal;

/**
 * Hito 10 · Definición de hecho: la demo de 10 minutos del comercial, automatizada. El docente abre la clase y proyecta el QR
 * (1366×768); tres celulares (360, 390 y 412 px) entran por la dirección del QR con su nombre y su contraseña; el docente
 * asigna un reto del Dojo y lo responden en vivo; a un celular se le corta la red 30 segundos en medio del reto (Playwright
 * setOffline): ve el aviso, su respuesta sigue ahí y, al volver la red, se envía una sola vez; el panel muestra solo agregados;
 * "Terminar clase" cierra las sesiones del grupo. Entregables 4, 6, 7 y 8.
 */
class DemoDelAulaEnNavegadorIT {

    private static final Pattern CODIGO_CLASE = Pattern.compile("class=\"codigo-grande codigo-clase\"[^>]*>([0-9A-Z]{6})<");
    private static final int[][] CELULARES = {{360, 780}, {390, 844}, {412, 915}};

    private final BaseDatosDePrueba bd = new BaseDatosDePrueba();

    @Test
    void la_demo_de_10_minutos_corre_de_punta_a_punta() {
        Instant inicio = Instant.now();
        String sufijo = UUID.randomUUID().toString().substring(0, 6);
        String base = ReenvioLocal.arrancar();
        ClienteApp admin = Instalacion.administrador();
        ClienteApp profe = Instalacion.docenteNuevo(admin, "Profe Demo " + sufijo);
        UUID grupo = Aula.crearGrupo(admin, "10.º Demo " + sufijo, "Profe Demo " + sufijo);
        List<String> nombres = List.of("Ana Demo " + sufijo, "Beto Demo " + sufijo, "Carla Demo " + sufijo);
        List<String> codigos = AulaAltaEnLoteIT.codigos(profe.postFormulario("/grupos/" + grupo + "/lote", Map.of("texto", String.join("\n", nombres))).cuerpo());
        for (String c : codigos) {
            assertThat(Instalacion.canjear(Instalacion.RUTA, c, Instalacion.contrasenaDe("estudiante")).estado()).isEqualTo(302);
        }

        try (Playwright playwright = Playwright.create();
             Browser chromium = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true))) {
            // 1. El docente abre la clase y proyecta el QR.
            profe.postFormulario("/grupos/" + grupo + "/clase", Map.of("minutos", "50"));
            BrowserContext proyector = chromium.newContext(new Browser.NewContextOptions().setViewportSize(1366, 768));
            proyector.addCookies(List.of(new Cookie("PENSAMIENTO_SESION", profe.cookie("PENSAMIENTO_SESION")).setDomain("localhost").setPath("/")));
            Page tablero = proyector.newPage();
            tablero.navigate(base + "/grupos/" + grupo + "/clase");
            assertThat(tablero.locator("svg.qr").isVisible()).as("el QR se ve en el tablero").isTrue();
            Matcher m = CODIGO_CLASE.matcher(tablero.content());
            assertThat(m.find()).isTrue();
            String direccion = base + "/" + Instalacion.RUTA + "?clase=" + m.group(1);
            assertThat(tablero.content()).contains("/" + Instalacion.RUTA + "?clase=" + m.group(1));

            // 2. Tres celulares entran por la dirección del QR.
            List<BrowserContext> contextos = new ArrayList<>();
            List<Page> celulares = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                BrowserContext c = chromium.newContext(new Browser.NewContextOptions().setViewportSize(CELULARES[i][0], CELULARES[i][1]).setHasTouch(true));
                Page celular = c.newPage();
                celular.navigate(direccion);
                assertThat(celular.content()).contains("Clase de <strong>10.º Demo " + sufijo);
                celular.fill("#nombre", nombres.get(i));
                celular.fill("#contrasena", Instalacion.contrasenaDe("estudiante"));
                celular.click("button:has-text('Entrar')");
                celular.waitForURL("**/reto");
                contextos.add(c);
                celulares.add(celular);
            }

            // 3. El docente asigna el reto y lo responden en vivo.
            assertThat(profe.postFormulario("/grupos/" + grupo + "/tema", Map.of("tema", "falacias", "hasta", "")).estado()).isEqualTo(302);
            for (Page celular : celulares) {
                celular.navigate(base + "/reto");
                celular.waitForFunction("() => window.htmx !== undefined");
                celular.locator("form[data-borrador] input[name=respuesta]").first().check();
            }
            for (Page celular : celulares.subList(0, 2)) {
                celular.click("button[data-accion=responder]");
                celular.waitForSelector("a[data-accion=siguiente]");
            }

            // 4. A Carla se le corta la red 30 segundos en medio del reto: su respuesta sigue ahí y se envía una sola vez.
            Page carla = celulares.get(2);
            String elegida = carla.locator("form[data-borrador] input[name=respuesta]:checked").getAttribute("value");
            contextos.get(2).setOffline(true);
            carla.click("button[data-accion=responder]");
            carla.waitForSelector("[data-aviso-borrador]:has-text('Sin conexión')");
            carla.waitForTimeout(30_000);
            assertThat(carla.locator("form[data-borrador] input[name=respuesta]:checked").getAttribute("value")).isEqualTo(elegida);
            contextos.get(2).setOffline(false);
            carla.waitForSelector("a[data-accion=siguiente]");
            Long intentos = bd.jdbcAdmin().sql("SELECT count(*) FROM intento_dojo i JOIN usuario u ON u.id = i.usuario_id WHERE u.nombre = :n")
                    .param("n", nombres.get(2)).query(Long.class).single();
            assertThat(intentos).as("la respuesta de Carla se guardó una sola vez").isEqualTo(1);

            // 5. El panel muestra solo agregados.
            String panel = profe.get("/grupos/" + grupo + "/panel").cuerpo();
            assertThat(panel).contains("3 de 3", "entraron hoy", "retos respondidos hoy, por 3 estudiantes", "Aciertos por concepto");
            assertThat(panel).as("ni un nombre: los tres practicaron hoy").doesNotContain(nombres.get(0), nombres.get(1), nombres.get(2));

            // 6. "Terminar clase" cierra las sesiones del grupo.
            profe.postFormulario("/grupos/" + grupo + "/clase/terminar", Map.of());
            for (Page celular : celulares) {
                celular.navigate(base + "/reto");
                assertThat(celular.content()).as("la sesión se cerró: vuelve a la entrada").contains("Tu contraseña");
            }
            contextos.forEach(BrowserContext::close);
            proyector.close();
        }
        Duration tardo = Duration.between(inicio, Instant.now());
        System.out.println("[medición hito 10] demo del aula automatizada en " + tardo.toSeconds() + " s");
        assertThat(tardo).isLessThan(Duration.ofMinutes(10));
    }
}
