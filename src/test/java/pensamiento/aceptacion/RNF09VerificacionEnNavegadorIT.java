package pensamiento.aceptacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.FilePayload;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import pensamiento.catalogo.MapeadorJson;
import pensamiento.expediente.PaqueteDatos;
import pensamiento.testutil.Entorno;

/**
 * RNF-09 y RNF-06 del hito 6: en Chromium a 360 px, importar un PDF a la biblioteca con la barra de progreso de la subida y
 * esperar a que la lista lo muestre indexado (se refresca sola); después, desde la premisa del tráfico, abrir la ficha de
 * verificación, elegir el tipo, usar el pasaje del panel de biblioteca como evidencia en la ficha de fuente y guardar el
 * veredicto. Sin desplazamiento horizontal y sin errores de consola (CSP incluida).
 */
class RNF09VerificacionEnNavegadorIT {

    private static final String MAPA = """
            [Sucursal]: Conviene abrir la segunda sucursal en el centro.
              + <Más ventas>: Con más gente pasando, se vende más. {peso: 3}
                + [Tráfico]: El centro tiene más tráfico peatonal que el barrio.
                + [Conversión]: Más tráfico da más ventas. #asumible""";

    private final ClienteApp admin = Instalacion.administrador();

    @Test
    void importar_y_verificar_la_premisa_desde_un_celular() {
        String persona = Instalacion.personaNueva(admin, "dueña de la panadería", "5173");
        ClienteApp http = Instalacion.entraComo(persona, "5173");
        Taller taller = Taller.de(http);
        List<UUID> guardadas = taller.guardarEnElTaller(taller.evaluarEnElTaller(taller.tallerDeArgumentos().escribirArgumento(MAPA)
                .elegirEstandar("preponderancia")));
        UUID argumento = MapeadorJson.mapper().readValue(taller.exportarMisDatos(), PaqueteDatos.class).ejecuciones().stream()
                .filter(e -> e.id().equals(guardadas.getFirst())).findFirst().orElseThrow().argumentos().getFirst().id();
        UUID trafico = UUID.fromString(Jsoup.parse(http.get("/argumentos/" + argumento).cuerpo()).select("li[data-premisa]").stream()
                .filter(li -> li.text().contains("El centro tiene más tráfico")).findFirst().orElseThrow().attr("data-premisa"));

        try (Playwright playwright = Playwright.create();
             Browser navegador = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true))) {
            Page pagina = navegador.newContext(new Browser.NewContextOptions().setViewportSize(360, 780)).newPage();
            List<String> errores = errores(pagina);
            String base = entrar(pagina, persona, "5173");

            // P13: subir el PDF con su barra de progreso; la lista lo muestra indexado sin recargar.
            pagina.navigate(base + "/biblioteca");
            sinDesbordar(pagina);
            // Nombre único: los documentos que otras pruebas compartieron en la misma institución también están en la lista.
            String nombre = "conteo-peatonal-" + UUID.randomUUID().toString().substring(0, 8) + ".pdf";
            pagina.locator("#archivo").setInputFiles(new FilePayload(nombre, "application/pdf",
                    FlujoAMasVerificacionIT.conteoDelMunicipio(UUID.randomUUID().toString())));
            pagina.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Importar")).click();
            pagina.locator("#mensaje-subida").waitFor();
            assertThat(pagina.locator("#mensaje-subida").textContent()).contains("Importado: " + nombre);
            assertThat(pagina.locator("#progreso-subida").evaluate("p => p.value > 0 && p.value === p.max")).as("la barra llegó al final").isEqualTo(true);
            pagina.locator("li[data-estado=indexado] .nombre-documento:has-text('" + nombre + "')")
                    .waitFor(new com.microsoft.playwright.Locator.WaitForOptions().setTimeout(90_000));
            sinDesbordar(pagina);

            // P10: desde la premisa del argumento, la ficha; el tipo se guarda y los chequeos se actualizan.
            pagina.navigate(base + "/argumentos/" + argumento);
            pagina.locator("li[data-premisa='" + trafico + "'] a:has-text('Verificar')").click();
            pagina.waitForURL(base + "/verificar/" + trafico);
            sinDesbordar(pagina);
            pagina.locator("input[name=tipo][value=hecho]").check();
            pagina.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Guardar el tipo")).click();
            pagina.locator(".mensaje-ficha:has-text('Tipo guardado: hecho.')").waitFor();

            // P11: el panel de biblioteca busca solo; el pasaje se usa como evidencia en la ficha de fuente (P12).
            pagina.locator("#consulta-ficha").fill("centro barrio personas");
            pagina.locator("label.casilla:has-text('Solo por palabras') input").check();
            pagina.locator("#form-busqueda-ficha button").click();
            pagina.locator("#resultados-busqueda li[data-fragmento]:has-text('" + nombre + "'):has-text('1.200 personas por hora') a:has-text('Usar como evidencia')").click();
            pagina.waitForURL(u -> u.contains("/fuentes/nueva"));
            sinDesbordar(pagina);
            assertThat(pagina.locator("#f-pasaje").inputValue()).contains("1.200 personas por hora");
            pagina.locator("#f-titulo").fill("Conteo peatonal del municipio");
            pagina.locator("#f-fecha").fill("2025-03-15");
            pagina.locator("#f-grupo").fill("municipio");
            pagina.locator("input[name=independiente]").check();
            pagina.locator("input[name=original]").check();
            pagina.locator("input[name=postura][value=apoya]").check();
            pagina.locator("#previa .texto-previa:has-text('Aporta fuerza')").waitFor();
            pagina.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Guardar fuente")).click();
            pagina.waitForURL(base + "/verificar/" + trafico);
            assertThat(pagina.locator("li[data-evidencia]").count()).isEqualTo(1);

            // Paso 4: el veredicto con la confianza; la ficha lo dice sin recargar.
            pagina.locator("input[name=confianza]").fill("60");
            pagina.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Guardar veredicto")).click();
            pagina.locator(".mensaje-ficha:has-text('Veredicto guardado: en verificación.')").waitFor();
            sinDesbordar(pagina);
            assertThat(errores).as("errores de consola, incluidas violaciones de la CSP").isEmpty();
        }
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
