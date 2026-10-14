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
    private final CookieManager cookies;
    private final String base;
    private final String ruta;
    private String csrf = "";

    /** Un navegador que entra por la institución de pruebas (/prueba). */
    public ClienteApp() {
        this(Instalacion.RUTA);
    }

    /** Un navegador que entra por la ruta de la institución dada. */
    public ClienteApp(String ruta) {
        this.base = Entorno.urlApp();
        this.ruta = ruta;
        this.cookies = new CookieManager();
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

    /**
     * Una conexión SSE como la abre htmx: lee el flujo entero hasta que el servidor lo cierra tras el evento "fin" y lo
     * devuelve como texto ("event:token", "data:…"). El modelo en CPU puede tardar minutos: espera hasta el tiempo dado.
     */
    public Respuesta flujoSse(String ruta, Duration tiempoMaximo) {
        try {
            HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(base + ruta)).header("Accept", "text/event-stream")
                    .timeout(tiempoMaximo).GET().build(), HttpResponse.BodyHandlers.ofString());
            return new Respuesta(r.statusCode(), r.body(), r.headers().map());
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer el flujo SSE de " + ruta, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
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

    /** El valor de una cookie de esta sesión (para pasarle la sesión a un navegador de Playwright). */
    public String cookie(String nombre) {
        return cookies.getCookieStore().getCookies().stream().filter(c -> c.getName().equals(nombre)).map(java.net.HttpCookie::getValue)
                .findFirst().orElseThrow(() -> new IllegalStateException("Sin cookie " + nombre));
    }

    /** Un POST con cuerpo JSON y el token CSRF en la cabecera, como los que manda /llave.js. */
    public Respuesta postJson(String ruta, String json) {
        return enviar(HttpRequest.newBuilder(URI.create(base + ruta))
                .header("Content-Type", "application/json")
                .header("X-CSRF-TOKEN", csrf)
                .POST(HttpRequest.BodyPublishers.ofString(json)));
    }

    /** Una petición htmx desde la página de una persona: lleva su id en X-Persona, como la manda layout.jte (RF-02b). */
    public Respuesta postHtmxDesdeLaPaginaDe(String persona, String ruta, Map<String, String> campos) {
        String cuerpo = campos.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        return enviar(HttpRequest.newBuilder(URI.create(base + ruta))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("X-CSRF-TOKEN", csrf)
                .header("HX-Request", "true")
                .header("X-Persona", persona)
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo)));
    }

    /** Sube un archivo como multipart/form-data, con el token CSRF en la cabecera (formulario de importar). */
    public Respuesta postArchivo(String ruta, String campo, String nombreArchivo, byte[] contenido) {
        String limite = "----limite" + java.util.UUID.randomUUID();
        byte[] inicio = ("--" + limite + "\r\nContent-Disposition: form-data; name=\"" + campo + "\"; filename=\"" + nombreArchivo
                + "\"\r\nContent-Type: application/json\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] fin = ("\r\n--" + limite + "--\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] cuerpo = new byte[inicio.length + contenido.length + fin.length];
        System.arraycopy(inicio, 0, cuerpo, 0, inicio.length);
        System.arraycopy(contenido, 0, cuerpo, inicio.length, contenido.length);
        System.arraycopy(fin, 0, cuerpo, inicio.length + contenido.length, fin.length);
        return enviar(HttpRequest.newBuilder(URI.create(base + ruta))
                .header("Content-Type", "multipart/form-data; boundary=" + limite)
                .header("X-CSRF-TOKEN", csrf)
                .POST(HttpRequest.BodyPublishers.ofByteArray(cuerpo)));
    }

    /** Envía pares nombre-valor que pueden repetirse (como un formulario real), opcionalmente como petición htmx. */
    public Respuesta postPares(String ruta, java.util.List<Map.Entry<String, String>> pares, boolean htmx) {
        String cuerpo = pares.stream()
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

    /** Visita la entrada de su institución (/<ruta>) y toma el token CSRF de la sesión anónima. */
    public String tomarCsrf() {
        Respuesta bloqueo = get("/" + ruta);
        Matcher m = META_CSRF.matcher(bloqueo.cuerpo());
        if (!m.find()) {
            throw new IllegalStateException("La pantalla de bloqueo no trae el token CSRF (estado " + bloqueo.estado() + ")");
        }
        csrf = m.group(1);
        return csrf;
    }

    /** Entra como una persona con su contraseña; devuelve la respuesta cruda para afirmar sobre la redirección. */
    public Respuesta entrar(String nombre, String contrasena) {
        tomarCsrf();
        Respuesta r = postFormulario("/sesion", Map.of("nombre", nombre, "contrasena", contrasena, "volver", "/", "institucion", ruta));
        // Tras autenticarse el token cambia: se vuelve a tomar de una página con sesión.
        Respuesta inicio = get("/");
        Matcher m = META_CSRF.matcher(inicio.cuerpo());
        if (m.find()) {
            csrf = m.group(1);
        }
        return r;
    }

    /**
     * Hito 10: entra desde la entrada de una clase abierta (/<ruta>?clase=CODIGO), con su contraseña; {@code conLista} si tocó
     * su nombre en la lista del grupo.
     */
    public Respuesta entrarEnClase(String nombre, String contrasena, String codigo, boolean conLista) {
        csrfDe(get("/" + ruta + "?clase=" + codigo));
        java.util.Map<String, String> campos = new java.util.HashMap<>(Map.of("nombre", nombre, "contrasena", contrasena, "volver", "/",
                "institucion", ruta, "clase", codigo));
        if (conLista) {
            campos.put("lista", "si");
        }
        Respuesta r = postFormulario("/sesion", campos);
        csrfDe(get("/"));
        return r;
    }

    /** Entra como operador del servicio por /operador/entrar y confirma con su llave; devuelve la respuesta cruda del envío. */
    public Respuesta entrarComoOperador(String clave) {
        Instalacion.prepararOperador();
        csrfDe(get("/operador/entrar"));
        Respuesta r = postFormulario("/operador/sesion", Map.of("nombre", "operador", "clave", clave));
        if (r.estado() == 302 && r.cabecera("Location").orElse("").endsWith("/operador/llave")) {
            Instalacion.completarLlave(this, "/operador/llave", "operador");
        }
        csrfDe(get("/operador"));
        return r;
    }

    /** Toma el token CSRF de una página ya pedida (si lo trae). */
    public void csrfDe(Respuesta pagina) {
        Matcher m = META_CSRF.matcher(pagina.cuerpo());
        if (m.find()) {
            csrf = m.group(1);
        }
    }

    public Respuesta bloquear() {
        return postFormulario("/salir", Map.of());
    }

    public String ruta() {
        return ruta;
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
