package pensamiento.aceptacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.google.gson.JsonObject;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.CDPSession;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.Test;

import pensamiento.testutil.Entorno;
import pensamiento.testutil.ReenvioLocal;

/**
 * Hito 9, entregable 7: el administrador confirma con su llave de acceso en un navegador real, con el autenticador virtual
 * de Chromium (CDP WebAuthn.addVirtualAuthenticator). Sin llave solo puede registrarla; al registrarla ve 10 códigos de
 * recuperación una sola vez; después entra con contraseña y llave; un código de recuperación sirve una sola vez. Sin
 * errores de consola (CSP: el JavaScript de la llave es /llave.js).
 */
class LlaveDeAccesoEnNavegadorIT {

    @Test
    void el_administrador_registra_su_llave_y_despues_entra_con_ella_o_con_un_codigo_de_recuperacion_una_sola_vez() {
        String ruta = "llave-" + UUID.randomUUID().toString().substring(0, 8);
        Instalacion.sembrarInstitucion(ruta, "Colegio de la llave");
        String base = ReenvioLocal.arrancar();

        try (Playwright playwright = Playwright.create();
             Browser navegador = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true))) {
            BrowserContext contexto = navegador.newContext(new Browser.NewContextOptions().setViewportSize(1366, 768));
            Page pagina = contexto.newPage();
            List<String> errores = new ArrayList<>();
            pagina.onConsoleMessage(m -> {
                // El 422 del código de recuperación repetido es esperado: Chromium lo anota como recurso fallido.
                if ("error".equals(m.type()) && !m.text().contains("status of 422")) {
                    errores.add(m.text());
                }
            });
            CDPSession cdp = contexto.newCDPSession(pagina);
            cdp.send("WebAuthn.enable");
            JsonObject opciones = new JsonObject();
            opciones.addProperty("protocol", "ctap2");
            opciones.addProperty("transport", "internal");
            opciones.addProperty("hasResidentKey", true);
            opciones.addProperty("hasUserVerification", true);
            opciones.addProperty("isUserVerified", true);
            opciones.addProperty("automaticPresenceSimulation", true);
            JsonObject parametros = new JsonObject();
            parametros.add("options", opciones);
            cdp.send("WebAuthn.addVirtualAuthenticator", parametros);

            entrar(pagina, base, ruta);
            pagina.waitForURL(base + "/llave");
            pagina.navigate(base + "/catalogo");
            assertThat(pagina.url()).as("sin llave solo puede registrarla").isEqualTo(base + "/llave");

            pagina.getByText("Registrar mi llave").click();
            pagina.waitForURL(base + "/llave/codigos");
            List<String> codigos = pagina.locator(".codigos-recuperacion li").allTextContents();
            assertThat(codigos).hasSize(10).allMatch(c -> c.strip().matches("[0-9A-Z]{5}-[0-9A-Z]{5}"));
            pagina.getByText("Ya los guardé: seguir").click();
            pagina.waitForURL(base + "/");
            pagina.navigate(base + "/llave/codigos");
            assertThat(pagina.content()).as("los códigos se muestran una sola vez").doesNotContain(codigos.getFirst().strip());

            salir(pagina);
            entrar(pagina, base, ruta);
            pagina.waitForURL(base + "/llave");
            pagina.getByText("Confirmar con mi llave").click();
            pagina.waitForURL(base + "/");
            assertThat(pagina.locator("h1").first().textContent()).contains("¿Qué quieres hacer hoy?");

            salir(pagina);
            entrar(pagina, base, ruta);
            pagina.waitForURL(base + "/llave");
            usarCodigo(pagina, codigos.get(3).strip());
            pagina.waitForURL(base + "/llave?aviso=recuperacion");
            salir(pagina);
            entrar(pagina, base, ruta);
            pagina.waitForURL(base + "/llave");
            usarCodigo(pagina, codigos.get(3).strip());
            assertThat(pagina.locator("[role=alert]").first().textContent()).contains("no sirve");
            assertThat(pagina.url()).startsWith(base + "/llave");

            assertThat(errores).as("errores de consola, incluidas violaciones de la CSP").isEmpty();
        }
    }

    private static void entrar(Page pagina, String base, String ruta) {
        pagina.navigate(base + "/" + ruta);
        pagina.getByLabel("Tu nombre").fill("administrador");
        pagina.getByLabel("Tu contraseña").fill(Entorno.contrasenaAdministrador());
        pagina.getByLabel("Tu contraseña").press("Enter");
    }

    private static void usarCodigo(Page pagina, String codigo) {
        pagina.getByText("No tengo mi llave: usar un código de recuperación").click();
        pagina.getByLabel("Código de recuperación").fill(codigo);
        pagina.getByText("Entrar con el código").click();
    }

    private static void salir(Page pagina) {
        pagina.locator("form.salir button").first().click();
        pagina.waitForLoadState();
    }
}
