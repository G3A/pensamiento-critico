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

import pensamiento.testutil.Entorno;

/**
 * RNF-09 y RF-08: la única prueba de humo en navegador real. Una persona nueva entra, pulsa "Empieza con un
 * ejemplo", evalúa y ve la matriz de T28 · Análisis de hipótesis en competencia (ACH). Corre en Chromium sin
 * pantalla dentro del perfil test; cualquier error de consola (por ejemplo, una violación de la CSP) la hace fallar.
 */
class RNF09PrimerUsoEnNavegadorIT {

    @Test
    void una_persona_nueva_llega_a_la_matriz_desde_empieza_con_un_ejemplo() {
        ClienteApp admin = Instalacion.administrador();
        String persona = Instalacion.personaNueva(admin, "vecina", "2580");
        String base = urlConIp(Entorno.urlApp());

        try (Playwright playwright = Playwright.create();
             Browser navegador = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true))) {
            Page pagina = navegador.newContext(new Browser.NewContextOptions().setViewportSize(360, 780)).newPage();
            List<String> erroresDeConsola = new ArrayList<>();
            pagina.onConsoleMessage(m -> {
                if ("error".equals(m.type())) {
                    erroresDeConsola.add(m.text());
                }
            });

            pagina.navigate(base + "/bloqueo");
            pagina.getByLabel("Tu PIN").fill("2580");
            pagina.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(persona).setExact(true)).click();
            pagina.waitForURL(base + "/");

            assertThat(pagina.locator(".intenciones a").allTextContents()).hasSize(6);
            pagina.locator("#empieza-con-un-ejemplo").click();
            pagina.locator("#form-T28").waitFor();
            assertThat(pagina.locator("#T28-pregunta").inputValue()).contains("ventas de los sábados");

            pagina.locator("button[data-accion=evaluar]").click();
            pagina.locator("#resultado-T28 [data-patron=V03a]").waitFor();

            assertThat(pagina.locator("#resultado-T28 [data-patron=V03a] .titular").textContent()).contains("Menos refutada: H1");
            assertThat(pagina.locator("#resultado-T28 [data-patron=V03a] tfoot td").allTextContents()).containsExactly("0", "5", "4");
            assertThat(pagina.evaluate("document.documentElement.scrollWidth")).as("sin desplazamiento horizontal a 360 px").isEqualTo(360);
            assertThat(erroresDeConsola).as("errores de consola, incluidas violaciones de la CSP").isEmpty();
        }
    }

    /**
     * En la red del compose la app es http://app:8080, y "app" es un dominio de nivel superior en la lista de precarga
     * HSTS de Chrome: el navegador exigiría HTTPS. Con la IP del servicio no hay subida a HTTPS.
     */
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
