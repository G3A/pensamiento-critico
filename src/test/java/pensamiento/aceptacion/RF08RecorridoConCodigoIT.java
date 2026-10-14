package pensamiento.aceptacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import pensamiento.testutil.ReenvioLocal;

/**
 * Hito 9, definición de hecho: un recorrido automatizado de RF-08 en el portátil y en el celular, desde "Tengo un código"
 * hasta un resultado útil de una técnica, en menos de cinco minutos. La persona recibe su tarjeta, canjea el código, elige
 * su contraseña, entra con Enter, toca "Empieza con un ejemplo" y evalúa T28 · Análisis de hipótesis en competencia (ACH).
 * No reemplaza las sesiones moderadas con personas reales: RF-08 sigue abierto hasta entonces.
 */
class RF08RecorridoConCodigoIT {

    @ParameterizedTest(name = "{0}×{1}")
    @CsvSource({"1366, 768", "360, 780"})
    void desde_tengo_un_codigo_hasta_un_resultado_de_t28_en_menos_de_cinco_minutos(int ancho, int alto) {
        ClienteApp admin = Instalacion.administrador();
        String nombre = "estudiante rf08 " + UUID.randomUUID().toString().substring(0, 6);
        String codigo = Instalacion.codigoDeAlta(admin, nombre);
        String contrasena = "mi perro come mango verde";
        String base = ReenvioLocal.arrancar();

        try (Playwright playwright = Playwright.create();
             Browser navegador = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true))) {
            Page pagina = navegador.newContext(new Browser.NewContextOptions().setViewportSize(ancho, alto)).newPage();
            List<String> errores = new ArrayList<>();
            pagina.onConsoleMessage(m -> {
                if ("error".equals(m.type())) {
                    errores.add(m.text());
                }
            });
            Instant inicio = Instant.now();

            pagina.navigate(base + "/" + Instalacion.RUTA);
            pagina.getByText("Tengo un código").click();
            pagina.getByLabel("Código").fill(codigo.toLowerCase());
            pagina.getByLabel("Tu contraseña nueva").fill(contrasena);
            pagina.getByLabel("Escríbela otra vez").fill(contrasena);
            pagina.getByText("Guardar mi contraseña").click();
            pagina.waitForURL(base + "/" + Instalacion.RUTA);
            assertThat(pagina.locator("[role=status]").first().textContent()).contains("tu contraseña quedó guardada");
            assertThat(pagina.getByLabel("Tu nombre").inputValue()).as("el nombre ya viene escrito").isEqualTo(nombre);

            pagina.getByLabel("Tu contraseña").fill(contrasena);
            pagina.getByLabel("Tu contraseña").press("Enter");
            pagina.waitForURL(base + "/");
            pagina.locator("#empieza-con-un-ejemplo").click();
            pagina.locator("#form-T28").waitFor();
            pagina.locator("#form-T28 button[data-accion=evaluar]").click();
            pagina.locator("#resultado-T28 [data-patron=V03a] .titular").waitFor();

            Duration tiempo = Duration.between(inicio, Instant.now());
            assertThat(pagina.locator("#resultado-T28 [data-patron=V03a] .titular").textContent()).contains("H1");
            assertThat(tiempo).as("del código al resultado").isLessThan(Duration.ofMinutes(5));
            assertThat(pagina.evaluate("document.documentElement.scrollWidth")).isEqualTo(ancho);
            assertThat(errores).as("errores de consola").isEmpty();
        }
    }
}
