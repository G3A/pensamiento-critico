package pensamiento.aceptacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
import org.junit.jupiter.api.Test;

import pensamiento.catalogo.CatalogoJson;
import pensamiento.nucleo.BancoDojo;
import pensamiento.testutil.Entorno;

/**
 * RNF-09 y RNF-06 del hito 7: el Dojo (P19) y el registro de cambios de opinión (P20) en Chromium a 360 px, solo con el
 * teclado. La persona elige la opción con el espacio, responde con Enter, el foco queda en "Siguiente reto" y Enter abre el
 * siguiente; en P20 registra un cambio a mano con Tab y Enter. Sin desplazamiento horizontal y sin errores de consola (CSP
 * incluida); también el progreso y un capítulo del manual.
 */
class RNF09DojoYCambiosEnNavegadorIT {

    private static final BancoDojo BANCO = new CatalogoJson().bancoDojo();

    @Test
    void el_dojo_y_el_registro_de_cambios_en_un_celular_con_el_teclado() {
        ClienteApp admin = Instalacion.administrador();
        String persona = Instalacion.personaNueva(admin, "hijo mayor", "8080");
        String base = urlConIp(Entorno.urlApp());

        try (Playwright playwright = Playwright.create();
             Browser navegador = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true))) {
            Page pagina = navegador.newContext(new Browser.NewContextOptions().setViewportSize(360, 780)).newPage();
            List<String> errores = new ArrayList<>();
            pagina.onConsoleMessage(m -> {
                if ("error".equals(m.type())) {
                    errores.add(m.text());
                }
            });
            pagina.navigate(base + "/bloqueo");
            pagina.getByLabel("Tu PIN").fill("8080");
            pagina.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(persona).setExact(true)).click();
            pagina.waitForURL(base + "/");

            // P19: el primer reto de falacias, respondido con el teclado.
            pagina.navigate(base + "/dojo?tema=falacias");
            String id = pagina.locator("#reto [data-reto]").getAttribute("data-reto");
            assertThat(id).isEqualTo("generalizacion-i1");
            sinDesbordar(pagina);
            BancoDojo.Reto reto = BANCO.reto(id).orElseThrow();
            pagina.locator("#reto input[type=radio][value=" + reto.correcta() + "]").focus();
            pagina.keyboard().press("Space");
            pagina.keyboard().press("Tab");
            assertThat(pagina.evaluate("document.activeElement.dataset.accion")).as("después de las opciones, Responder").isEqualTo("responder");
            pagina.keyboard().press("Enter");
            pagina.locator("#reto[data-resultado=acierto]").waitFor();
            assertThat(pagina.locator("#respuesta-titulo").textContent()).contains("Acierto.");
            assertThat(pagina.evaluate("document.activeElement.dataset.accion")).as("el foco queda en Siguiente reto").isEqualTo("siguiente");
            sinDesbordar(pagina);
            pagina.keyboard().press("Enter");
            pagina.waitForURL(u -> u.contains("/dojo"));
            pagina.locator("#reto [data-reto=ad_hominem-i1]").waitFor();
            sinDesbordar(pagina);

            // Responder sin elegir dice qué falta, junto a las opciones.
            pagina.locator("button[data-accion=responder]").focus();
            pagina.keyboard().press("Enter");
            pagina.locator("#error-reto").waitFor();
            assertThat(pagina.locator("#error-reto").textContent()).contains("Elige una opción.");

            // El progreso: calendario y niveles, sin desbordar.
            pagina.navigate(base + "/dojo/progreso");
            pagina.locator("[data-patron=V13c]").waitFor();
            assertThat(pagina.locator("[data-patron=V13b]").count()).isEqualTo(4);
            sinDesbordar(pagina);

            // P20: registrar un cambio a mano con el teclado.
            pagina.navigate(base + "/cambios-de-opinion");
            pagina.locator("#nueva-postura").focus();
            pagina.keyboard().type("La consola no me quita tiempo de estudio");
            pagina.keyboard().press("Tab");
            pagina.keyboard().type("70");
            pagina.keyboard().press("Tab");
            pagina.keyboard().type("40");
            pagina.keyboard().press("Tab");
            pagina.locator("#nueva-causa").selectOption("evidencia");
            pagina.locator("button[data-accion=registrar-cambio]").focus();
            pagina.keyboard().press("Enter");
            pagina.locator("[data-patron=V11] ol.linea-tiempo li").first().waitFor();
            assertThat(pagina.locator("[data-patron=V11] ol.linea-tiempo li").first().textContent())
                    .contains("«La consola no me quita tiempo de estudio» · 70% → 40% · evidencia nueva");
            sinDesbordar(pagina);

            // El diario de razonamiento y un capítulo del manual.
            pagina.navigate(base + "/cambios-de-opinion/diario");
            pagina.locator("[data-patron=V11]").waitFor();
            sinDesbordar(pagina);
            pagina.navigate(base + "/manual/F8");
            pagina.locator("#tecnica-T49").waitFor();
            sinDesbordar(pagina);
            // El único aviso esperado es el 422 de responder sin elegir, que Chromium anota como recurso fallido.
            assertThat(errores).as("errores de consola, incluidas violaciones de la CSP")
                    .allMatch(e -> e.contains("status of 422"), "solo el 422 esperado").hasSizeLessThanOrEqualTo(1);
        }
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
