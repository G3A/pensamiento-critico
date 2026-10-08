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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import pensamiento.testutil.Entorno;

/**
 * RNF-09 y RNF-06 del hito 4: el flujo D en Chromium a 360 px. La dueña abre una decisión desde el Diario, define el
 * problema (T40 con una fila agregada, T41), dibuja las causas con Ishikawa y las ve como árbol MECE (Graphviz en los dos),
 * pasa la lista de T16 y registra la decisión con T32. Con el reloj adelantado, la revisión aparece al entrar al Diario;
 * la marca como cumplida y ve su Brier. Sin desplazamiento horizontal y sin errores de consola (CSP incluida).
 */
class RNF09DiarioEnNavegadorIT {

    private final ClienteApp admin = Instalacion.administrador();

    @AfterEach
    void devolverElRelojALaHoraReal() {
        Diario.adelantarElReloj(admin, 0);
    }

    @Test
    void el_flujo_d_de_punta_a_punta_en_un_celular() {
        Diario.adelantarElReloj(admin, 0);
        String persona = Instalacion.personaNueva(admin, "dueña de la panadería", "2604");
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
            pagina.getByLabel("Tu PIN").fill("2604");
            pagina.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(persona).setExact(true)).click();
            pagina.waitForURL(base + "/");

            // P17: nueva decisión desde el Diario.
            pagina.navigate(base + "/diario");
            pagina.locator("#titulo-decision").fill("Abrir la segunda sucursal");
            pagina.locator("button[data-accion=nueva-decision]").click();
            pagina.waitForURL(u -> u.contains("/diario/decisiones/"));
            assertThat(pagina.locator("h1").textContent()).isEqualTo("Abrir la segunda sucursal");
            sinDesbordar(pagina);

            // P18 · T40: se agrega una reformulación, se elige la primera y se guarda en la decisión.
            pagina.locator("#form-T40 .anadir-fila").click();
            pagina.locator("#T40-reformulaciones-1-texto").waitFor();
            pagina.locator("#T40-reformulaciones-0-texto").fill("¿Dónde abrimos sin descuidar la original?");
            pagina.locator("#T40-reformulaciones-0-elegida").check();
            pagina.locator("#T40-reformulaciones-1-texto").fill("¿Cómo vendemos más pan con lo que ya tenemos?");
            guardar(pagina, "T40", "V13a");

            // T41: certezas y supuestos; el problema ya viene escrito desde T40.
            pagina.locator(".tecnicas-paso a[data-tecnica=T41]").click();
            pagina.locator("#form-T41").waitFor();
            assertThat(pagina.locator("#T41-problema").inputValue()).isEqualTo("¿Dónde abrimos sin descuidar la original?");
            pagina.locator("#T41-certezas-0-texto").fill("La sucursal original vende 260 panes por día.");
            pagina.locator("#T41-supuestos-0-texto").fill("La terminal tiene más gente que el centro.");
            pagina.locator("#T41-supuestos-0-como").fill("Contar peatones una mañana.");
            guardar(pagina, "T41", "V04");

            // T43 · Diagrama de Ishikawa con Graphviz, y la misma lista de causas vista como árbol MECE.
            pagina.locator(".tecnicas-paso a[data-tecnica=T43]").click();
            pagina.locator("#form-T43").waitFor();
            pagina.locator("#T43-causas-0-texto").fill("El horno nuevo llega tarde");
            pagina.locator("#T43-causas-0-categoria").selectOption("Máquina");
            guardar(pagina, "T43", "V07");
            assertThat(pagina.locator("#resultado-T43 [data-patron=V07] svg g.node.efecto").count()).isEqualTo(1);
            pagina.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Ver como árbol MECE")).click();
            pagina.locator("#form-T42").waitFor();
            pagina.locator("#form-T42 button[data-accion=evaluar]").click();
            pagina.locator("#resultado-T42 [data-patron=V06] svg g.node.raiz").waitFor();
            assertThat(pagina.locator("#resultado-T42 [data-patron=V06] .tarjeta-resultado .titular").textContent()).contains("6 ramas, 1 hoja");
            sinDesbordar(pagina);

            // Paso 4: la lista de T16 y el registro de T32 con su predicción.
            pagina.navigate(pagina.url().replaceAll("\\?.*$", "") + "?paso=4&tecnica=T16");
            pagina.locator("#T16-alternativas").fill("Centro, terminal o esperar un año.");
            pagina.locator("#T16-cifras").fill("Conteo propio de peatones.");
            pagina.locator("#T16-contraria").fill("El encargado prefería esperar.");
            guardar(pagina, "T16", "V02");
            pagina.locator(".tecnicas-paso a[data-tecnica=T32]").click();
            pagina.locator("#form-T32").waitFor();
            pagina.locator("#T32-alternativas").fill("Centro · esperar un año.");
            pagina.locator("#T32-prediccion").fill("La sucursal de la terminal cubre sus costos en 6 meses.");
            pagina.locator("#T32-confianza").fill("70");
            pagina.locator("#T32-cambiarOpinion").fill("Tres meses seguidos por debajo de 200 panes por día.");
            guardar(pagina, "T32", "V11");
            pagina.locator(".pasos-decision .chip-ok:has-text('registrada')").waitFor();
            sinDesbordar(pagina);

            // Con el reloj adelantado hasta la revisión, aparece al entrar al Diario; se marca como cumplida.
            Diario.adelantarElReloj(admin, 91);
            pagina.navigate(base + "/diario");
            assertThat(pagina.locator("#revisiones-titulo").textContent()).isEqualTo("Revisiones pendientes (1)");
            sinDesbordar(pagina);
            pagina.locator("#revisiones button[data-accion=se-cumplio]").click();
            pagina.locator("#calibracion figcaption").waitFor();
            assertThat(pagina.locator("#calibracion figcaption").textContent()).isEqualTo("Brier 0,09 con 1 resuelta. Sobre la diagonal estarías bien calibrado.");
            assertThat(pagina.locator("#revisiones-titulo").textContent()).isEqualTo("Revisiones pendientes (0)");
            sinDesbordar(pagina);
            assertThat(erroresDeConsola).as("errores de consola, incluidas violaciones de la CSP").isEmpty();
        }
    }

    /** "Guardar en historial" en el asistente: queda guardada en la decisión y se ve el patrón de su resultado. */
    private static void guardar(Page pagina, String tecnica, String patron) {
        pagina.locator("#form-" + tecnica + " button[data-accion=guardar]").click();
        pagina.locator("#resultado-" + tecnica + " .guardado").waitFor();
        assertThat(pagina.locator("#resultado-" + tecnica + " [data-patron=" + patron + "]").count()).isEqualTo(1);
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
