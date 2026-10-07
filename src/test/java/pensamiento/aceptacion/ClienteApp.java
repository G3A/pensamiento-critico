package pensamiento.aceptacion;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import pensamiento.testutil.Entorno;

/**
 * Driver de protocolo HTTP de la aceptación (capa 3 de Farley): una sesión de navegador por instancia,
 * con cookies, token CSRF y sin seguir redirecciones (así se afirma sobre ellas).
 */
public final class ClienteApp {

    public record Respuesta(int estado, String cuerpo, Map<String, java.util.List<String>> cabeceras) {
        public Optional<String> cabecera(String nombre) {
            return cabeceras.entrySet().stream()
                    .filter(e -> e.getKey() != null && e.getKey().equalsIgnoreCase(nombre))
                    .flatMap(e -> e.getValue().stream()).findFirst();
        }
    }

    private static final Pattern META_CSRF = Pattern.compile("<meta name=\"_csrf\" content=\"([^\"]*)\"");

    private final HttpClient http;
    private final String base;
    private String csrf = "";

    public ClienteApp() {
        this.base = Entorno.urlApp();
        CookieManager cookies = new CookieManager();
        cookies.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        this.http = HttpClient.newBuilder()
                .cookieHandler(cookies)
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public Respuesta get(String ruta) {
        return enviar(HttpRequest.newBuilder(URI.create(base + ruta)).GET());
    }

    public Respuesta getHtmx(String ruta) {
        return enviar(HttpRequest.newBuilder(URI.create(base + ruta)).header("HX-Request", "true").GET());
    }

    public Respuesta postFormulario(String ruta, Map<String, String> campos) {
        return postFormulario(ruta, campos, false);
    }

    public Respuesta postFormulario(String ruta, Map<String, String> campos, boolean htmx) {
        String cuerpo = campos.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        HttpRequest.Builder peticion = HttpRequest.newBuilder(URI.create(base + ruta))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("X-CSRF-TOKEN", csrf)
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo));
        if (htmx) {
            peticion.header("HX-Request", "true");
        }
        return enviar(peticion);
    }

    /** Visita la pantalla de bloqueo y toma el token CSRF de la sesión anónima. */
    public String tomarCsrf() {
        Respuesta bloqueo = get("/bloqueo");
        Matcher m = META_CSRF.matcher(bloqueo.cuerpo());
        if (!m.find()) {
            throw new IllegalStateException("La pantalla de bloqueo no trae el token CSRF (estado " + bloqueo.estado() + ")");
        }
        csrf = m.group(1);
        return csrf;
    }

    /** Entra como una persona; devuelve la respuesta cruda para afirmar sobre la redirección. */
    public Respuesta entrar(String nombre, String pin) {
        tomarCsrf();
        Respuesta r = postFormulario("/sesion", Map.of("nombre", nombre, "pin", pin, "volver", "/"));
        // Tras autenticarse el token cambia: se vuelve a tomar de una página con sesión.
        Respuesta inicio = get("/");
        Matcher m = META_CSRF.matcher(inicio.cuerpo());
        if (m.find()) {
            csrf = m.group(1);
        }
        return r;
    }

    public Respuesta bloquear() {
        return postFormulario("/salir", Map.of());
    }

    public String csrf() {
        return csrf;
    }

    private Respuesta enviar(HttpRequest.Builder peticion) {
        try {
            HttpResponse<String> r = http.send(peticion.timeout(Duration.ofSeconds(30)).build(), HttpResponse.BodyHandlers.ofString());
            return new Respuesta(r.statusCode(), r.body(), r.headers().map());
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo hablar con la aplicación en " + base, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
