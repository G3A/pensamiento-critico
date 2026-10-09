package pensamiento.aceptacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
import org.junit.jupiter.api.Test;

import pensamiento.testutil.Entorno;

/**
 * RNF-09 y RNF-06 del hito 5: el Consejero socrático en Chromium a 360 px. Sin el modelo, una sesión de debate de punta a
 * punta: el equipo rojo ataca en el diálogo, se responde, el double crux se guarda desde el panel, se va al cierre, se
 * responde la falsación y se cierra. Con el modelo, el turno llega por SSE, el texto provisional se reemplaza con la
 * versión validada y la entrada se habilita. Sin desplazamiento horizontal y sin errores de consola (CSP incluida).
 */
class RNF09ConsejeroEnNavegadorIT {

    private final ClienteApp admin = Instalacion.administrador();

    private static boolean ollamaDisponible() {
        String salud = new ClienteApp().get("/actuator/health").cuerpo();
        return salud.contains("\"ia\":{") && !salud.contains("\"status\":\"PLANTILLAS\"");
    }

    @Test
    void el_debate_con_el_double_crux_de_punta_a_punta_en_un_celular() {
        String persona = Instalacion.personaNueva(admin, "vecino de la junta", "4826");
        try (Playwright playwright = Playwright.create();
             Browser navegador = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true))) {
            Page pagina = navegador.newContext(new Browser.NewContextOptions().setViewportSize(360, 780)).newPage();
            List<String> errores = errores(pagina);
            String base = entrar(pagina, persona, "4826");

            pagina.navigate(base + "/consejero");
            pagina.locator("input[name=modo][value=debate]").check();
            pagina.locator("#postura").fill("Hay que comprar las cámaras que ofrece el vendedor.");
            pagina.locator("#razon0").fill("El vendedor dice que bajan los robos un 70%.");
            pagina.locator("#razon1").fill("En el barrio vecino bajaron los robos después de ponerlas.");
            pagina.locator("#apoyo1").selectOption("causa");
            Locator usaModelo = pagina.locator("input[name=usaModelo]");
            if (usaModelo.isEnabled() && usaModelo.isChecked()) {
                usaModelo.uncheck();
            }
            pagina.locator("button[data-accion=empezar-sesion]").click();
            pagina.waitForURL(u -> u.contains("/consejero/sesiones/"));
            assertThat(pagina.locator("#dialogo").getAttribute("role")).isEqualTo("log");
            assertThat(pagina.locator("#dialogo li.burbuja-equipo-rojo .texto-burbuja").first().textContent())
                    .isEqualTo("Eso lo dice alguien que gana si le crees. ¿Tienes un solo dato que no venga de esa persona?");
            sinDesbordar(pagina);

            // El equipo rojo en el diálogo: se responde y llega el ataque siguiente sin recargar.
            responder(pagina, "El municipio reporta 12% de baja en el barrio vecino. Es menos, pero es independiente.");
            pagina.locator("#dialogo li.burbuja-equipo-rojo").nth(1).waitFor();
            assertThat(pagina.locator("#dialogo li.burbuja-equipo-rojo").nth(1).locator(".autor").textContent()).contains("equipo rojo · ataque 2");
            assertThat(pagina.locator("#panel-consejero").getAttribute("aria-label")).isEqualTo("Debilidades que identificó el código");

            // Double crux desde el panel del debate: se guarda en el expediente de la sesión.
            pagina.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("T38 · Double crux")).click();
            pagina.locator("#form-T38").waitFor();
            assertThat(pagina.locator("#T38-posturaA").inputValue()).isEqualTo("Hay que comprar las cámaras que ofrece el vendedor.");
            pagina.locator("#T38-posturaB").fill("Mejor arreglar los postes.");
            pagina.locator("#T38-dependeB-0-texto").fill("Las cámaras no bajan los robos, los mueven.");
            pagina.locator("#T38-cruxes .anadir-fila").click();
            pagina.locator("#T38-cruxes-0-hecho").waitFor();
            pagina.locator("#T38-cruxes-0-hecho").fill("En barrios parecidos, las cámaras bajaron los robos sin moverlos a otras cuadras.");
            pagina.locator("#T38-cruxes-0-cambiaA").check();
            pagina.locator("#T38-cruxes-0-cambiaB").check();
            pagina.locator("#T38-cruxes-0-verificable").check();
            pagina.locator("#form-T38 button[data-accion=guardar]").click();
            pagina.locator("#resultado-T38 .guardado").waitFor();
            assertThat(pagina.locator("#resultado-T38 [data-patron=V04] .tarjeta-resultado .titular").textContent()).isEqualTo("1 crux común · 1 a verificación.");
            sinDesbordar(pagina);

            // Ir al cierre, responder la falsación y cerrar.
            pagina.locator("button[data-accion=ir-al-cierre]").click();
            pagina.locator("#dialogo li.burbuja-consejero .autor:has-text('consejero · cierre')").waitFor();
            responder(pagina, "Que el municipio diga que los robos se movieron de cuadra.");
            pagina.locator("form.cerrar-sesion").waitFor();
            pagina.locator("#confianzaDespues").fill("70");
            pagina.locator("button[data-accion=cerrar-sesion]").click();
            pagina.locator(".resultado-sesion [data-patron=V09]").waitFor();
            assertThat(pagina.locator(".resultado-sesion .tarjeta-resultado .titular").textContent()).isEqualTo("3 ataques · 1 respondido · 2 sin responder.");
            assertThat(pagina.locator("#entrada-consejero").textContent()).contains("Esta sesión está cerrada");
            sinDesbordar(pagina);
            assertThat(errores).as("errores de consola, incluidas violaciones de la CSP").isEmpty();
        }
    }

    @Test
    void con_el_modelo_el_turno_llega_por_sse_y_la_entrada_se_habilita_al_terminar() {
        assumeTrue(ollamaDisponible(), "Ollama no responde: esta prueba necesita el modelo");
        String persona = Instalacion.personaNueva(admin, "dueña de la panadería", "6248");
        try (Playwright playwright = Playwright.create();
             Browser navegador = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true))) {
            Page pagina = navegador.newContext(new Browser.NewContextOptions().setViewportSize(360, 780)).newPage();
            List<String> errores = errores(pagina);
            String base = entrar(pagina, persona, "6248");

            pagina.navigate(base + "/consejero");
            pagina.locator("input[name=modo][value=decision]").check();
            pagina.locator("#postura").fill("Conviene abrir los domingos.");
            pagina.locator("input[name=usaModelo]").check();
            pagina.locator("button[data-accion=empezar-sesion]").click();
            pagina.waitForURL(u -> u.contains("/consejero/sesiones/"));
            esperarTurno(pagina);
            sinDesbordar(pagina);

            responder(pagina, "Quiero vender más los fines de semana sin cansar al equipo.");
            pagina.locator("#dialogo li.burbuja-consejero").nth(1).waitFor();
            esperarTurno(pagina);
            String pregunta = pagina.locator("#dialogo li.burbuja-consejero .texto-burbuja").nth(1).textContent().strip();
            assertThat(pregunta).endsWith("?");
            assertThat(pagina.locator("#dialogo li.burbuja-consejero").nth(1).locator(".autor").textContent())
                    .containsAnyOf("redactada por el modelo · validada", "del banco");
            sinDesbordar(pagina);
            assertThat(errores).as("errores de consola, incluidas violaciones de la CSP").isEmpty();
        }
    }

    /** Espera a que ningún turno esté redactando y la entrada quede habilitada (el evento final de SSE llegó). */
    private static void esperarTurno(Page pagina) {
        pagina.waitForCondition(() -> pagina.locator("#dialogo [sse-connect]").count() == 0
                && !pagina.locator("#texto-respuesta").isDisabled(), new Page.WaitForConditionOptions().setTimeout(300_000));
    }

    private static void responder(Page pagina, String texto) {
        pagina.locator("#texto-respuesta").fill(texto);
        int antes = pagina.locator("#dialogo li").count();
        pagina.locator("button[data-accion=enviar]").click();
        pagina.waitForCondition(() -> pagina.locator("#dialogo li").count() > antes);
    }

    private static List<String> errores(Page pagina) {
        List<String> errores = new ArrayList<>();
        pagina.onConsoleMessage(m -> {
            if ("error".equals(m.type())) {
                errores.add(m.text());
            }
        });
        return errores;
    }

    private static String entrar(Page pagina, String persona, String pin) {
        String base = urlConIp(Entorno.urlApp());
        pagina.navigate(base + "/bloqueo");
        pagina.getByLabel("Tu PIN").fill(pin);
        pagina.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(persona).setExact(true)).click();
        pagina.waitForURL(base + "/");
        return base;
    }

    private static void sinDesbordar(Page pagina) {
        assertThat(pagina.evaluate("document.documentElement.scrollWidth")).as("sin desplazamiento horizontal a 360 px; se salen: "
                + pagina.evaluate(SE_SALEN)).isEqualTo(360);
    }

    private static final String SE_SALEN = "Array.from(document.querySelectorAll(\"body *\")).filter(e => e.getBoundingClientRect().right > "
            + "window.innerWidth + 1 && !Array.from(e.children).some(h => h.getBoundingClientRect().right > window.innerWidth + 1))"
            + ".slice(0, 8).map(e => e.tagName + \"#\" + e.id + \".\" + e.className + \" \" + Math.round(e.getBoundingClientRect().right))";

    /** Con la IP del servicio: "app" está en la lista de precarga HSTS de Chrome (ver RNF09PrimerUsoEnNavegadorIT). */
    private static String urlConIp(String url) {
        URI uri = URI.create(url);
        try {
            String ip = InetAddress.getByName(uri.getHost()).getHostAddress();
            return uri.getScheme() + "://" + ip + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
        } catch (UnknownHostException e) {
            throw new IllegalStateException("No se resuelve " + uri.getHost(), e);
        }
    }
}
