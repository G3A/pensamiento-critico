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
 * RNF-09 y RF-08: las pruebas de humo en navegador real. Una persona nueva entra, pulsa "Empieza con un
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
     * Hito 2 (RF-09, RF-10, RNF-06): en el Taller, a 360 px, el ejemplo de la panadería con una réplica que descalifica a
     * quien objeta se evalúa; aparecen el mapa dibujado, el panel Toulmin y una marca de falacia; elegir un nodo en la
     * lista lo resalta también en el SVG (componente Alpine registrado en app.js, bajo la CSP). Sin errores de consola.
     */
    @Test
    void en_el_taller_se_evalua_el_argumento_y_se_ve_el_mapa_y_una_marca_de_falacia() {
        ClienteApp admin = Instalacion.administrador();
        String persona = Instalacion.personaNueva(admin, "dueña", "1470");
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
            pagina.getByLabel("Tu PIN").fill("1470");
            pagina.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(persona).setExact(true)).click();
            pagina.waitForURL(base + "/");

            pagina.navigate(base + "/taller?ejemplo=sucursal");
            String texto = pagina.locator("#taller-argdown").inputValue();
            assertThat(texto).startsWith("[Sucursal]: Conviene abrir la segunda sucursal en el centro.");
            pagina.locator("#taller-argdown").fill(texto + "\n    - El empleado que lo dice es un perezoso, así que su objeción no sirve.");
            pagina.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Evaluar").setExact(true)).click();
            pagina.locator("#form-taller [data-patron=V01] svg g.node").first().waitFor();

            assertThat(pagina.locator("#form-taller [data-patron=V01] svg g.node").count()).isEqualTo(5);
            assertThat(pagina.locator("#form-taller [data-patron=V02] li[data-parte=respaldo] .chip").textContent()).isEqualTo("falta");
            assertThat(pagina.locator("#form-taller [data-patron=V05] mark.propuesta").textContent()).contains("es un perezoso");

            var primero = pagina.locator("#form-taller button.nodo-lista").first();
            String id = primero.getAttribute("data-nodo");
            primero.click();
            assertThat(primero.getAttribute("aria-pressed")).isEqualTo("true");
            assertThat(pagina.locator("[id=\"" + id + "\"]").getAttribute("class")).contains("seleccionado");

            assertThat(pagina.evaluate("document.documentElement.scrollWidth")).as("sin desplazamiento horizontal a 360 px; se salen: " + pagina.evaluate(SE_SALEN)).isEqualTo(360);
            assertThat(erroresDeConsola).as("errores de consola, incluidas violaciones de la CSP").isEmpty();
        }
    }

    /**
     * Hito 3 (RF-14, corrección 11): la prueba de humo de SSE. En T34 · Steelmanning, con el ejemplo de las cámaras,
     * pedir una propuesta muestra la espera (indicador, tiempo, Cancelar y el botón deshabilitado) con su conexión SSE;
     * el evento final trae la propuesta sin adoptar; adoptarla hace que cuente. A 360 px y sin errores de consola.
     * Necesita el modelo: sin Ollama, el botón está deshabilitado y la prueba se salta.
     */
    @Test
    void en_t34_se_pide_un_steelman_se_espera_por_sse_y_se_adopta() {
        ClienteApp admin = Instalacion.administrador();
        String persona = Instalacion.personaNueva(admin, "presidenta", "3690");
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
            pagina.getByLabel("Tu PIN").fill("3690");
            pagina.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(persona).setExact(true)).click();
            pagina.waitForURL(base + "/");

            pagina.navigate(base + "/tecnicas/T34?pestana=usar");
            pagina.locator("nav.barra-ejemplos a.ejemplo", new Page.LocatorOptions().setHasText("Los que no quieren cámaras")).click();
            pagina.locator("#form-T34 #pedir-T34").waitFor();
            org.junit.jupiter.api.Assumptions.assumeTrue(pagina.locator("#pedir-T34 button").isEnabled(), "Ollama no responde: el humo de SSE necesita el modelo");

            pagina.locator("#pedir-T34 button").click();
            pagina.locator("#espera-T34 .espera[sse-connect]").waitFor();
            assertThat(pagina.locator("#espera-T34 .estado-espera").getAttribute("role")).isEqualTo("status");
            assertThat(pagina.locator("#pedir-T34 button").isDisabled()).as("el botón queda deshabilitado mientras el modelo trabaja").isTrue();
            assertThat(pagina.locator("#espera-T34 button[data-accion=cancelar]").isVisible()).isTrue();

            pagina.locator("#form-T34 li.propuesta[data-propuesta=IA1]").waitFor(new com.microsoft.playwright.Locator.WaitForOptions().setTimeout(300_000));
            assertThat(pagina.locator("#espera-T34 .aviso-ia").textContent()).contains("propuesta del modelo");
            assertThat(pagina.locator("#form-T34 li.propuesta[data-propuesta=IA1] .chip").textContent())
                    .isEqualTo("propuesta del modelo · sin adoptar · no cuenta");

            pagina.locator("#form-T34 button.adoptar").click();
            pagina.locator("#resultado-T34 [data-patron=V04]").waitFor();
            assertThat(pagina.locator("#resultado-T34 .tarjeta-resultado .titular").textContent()).startsWith("Steelman de ");
            assertThat(pagina.locator("#form-T34 li.propuesta[data-propuesta=IA1] .chip").textContent()).isEqualTo("adoptada por ti · cuenta");

            assertThat(pagina.evaluate("document.documentElement.scrollWidth")).as("sin desplazamiento horizontal a 360 px; se salen: " + pagina.evaluate(SE_SALEN)).isEqualTo(360);
            assertThat(erroresDeConsola).as("errores de consola, incluidas violaciones de la CSP").isEmpty();
        }
    }

    /** Los elementos más profundos que pasan del borde derecho de la ventana, para saber qué arreglar. */
    private static final String SE_SALEN = "Array.from(document.querySelectorAll(\"body *\")).filter(e => e.getBoundingClientRect().right > "
            + "window.innerWidth + 1 && !Array.from(e.children).some(h => h.getBoundingClientRect().right > window.innerWidth + 1))"
            + ".slice(0, 8).map(e => e.tagName + \"#\" + e.id + \".\" + e.className + \" \" + Math.round(e.getBoundingClientRect().right))";

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
