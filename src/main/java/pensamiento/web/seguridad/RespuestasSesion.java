package pensamiento.web.seguridad;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

import gg.jte.TemplateEngine;
import gg.jte.TemplateOutput;
import gg.jte.output.StringOutput;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Component;

/**
 * Cómo responde la sesión según quién pregunta. Para una petición htmx (sesión expirada en medio de un
 * formulario) devuelve el fragmento de bloqueo "¿Quién eres?" apuntado a #bloqueo: la página y el
 * borrador quedan intactos y, al entrar de nuevo, el usuario sigue donde estaba (RF-02).
 * Para una navegación normal, redirige a la entrada de la institución (/<ruta>) con la URL a la que volver.
 * La ruta sale de la cookie PENSAMIENTO_INSTITUCION, que la entrada deja al visitarla y que sobrevive a la sesión: es solo
 * una pista para volver a la misma pantalla de entrada, la institución siempre la resuelve el servidor (hito 8).
 */
@Component
public class RespuestasSesion {

    public static final String EVENTO_CSRF = "csrf-renovado";
    public static final String COOKIE_INSTITUCION = "PENSAMIENTO_INSTITUCION";

    private final TemplateEngine plantillas;

    public RespuestasSesion(TemplateEngine plantillas) {
        this.plantillas = plantillas;
    }

    public static boolean esHtmx(HttpServletRequest request) {
        return "true".equals(request.getHeader("HX-Request"));
    }

    public AuthenticationEntryPoint alNoEstarAutenticado() {
        return (request, response, excepcion) -> {
            if (esHtmx(request)) {
                fragmentoDeBloqueo(request, response, HttpServletResponse.SC_UNAUTHORIZED, null);
            } else {
                String volver = request.getRequestURI() + (request.getQueryString() == null ? "" : "?" + request.getQueryString());
                Optional<String> ruta = rutaRecordada(request);
                response.sendRedirect(ruta.isEmpty() ? "/" : "/" + ruta.get() + "?volver=" + URLEncoder.encode(volver, StandardCharsets.UTF_8));
            }
        };
    }

    public AuthenticationSuccessHandler alEntrar() {
        return (request, response, autenticacion) -> {
            if (esHtmx(request)) {
                // Vacía el overlay #bloqueo y entrega el token CSRF de la sesión nueva.
                response.setStatus(HttpServletResponse.SC_OK);
                response.setHeader("HX-Retarget", "#bloqueo");
                response.setHeader("HX-Reswap", "innerHTML");
                response.setHeader("HX-Trigger", disparadorCsrf(request));
                response.setContentType("text/html;charset=UTF-8");
                response.getWriter().write("");
            } else {
                response.sendRedirect(destinoSeguro(request.getParameter("volver")));
            }
        };
    }

    public AuthenticationFailureHandler alFallar() {
        return (request, response, excepcion) -> {
            if (esHtmx(request)) {
                fragmentoDeBloqueo(request, response, HttpServletResponse.SC_UNAUTHORIZED, "Nombre o PIN incorrectos");
            } else {
                response.sendRedirect(RutasReservadas.deInstitucion(request.getParameter(ProveedorEntrada.CAMPO_INSTITUCION))
                        .map(r -> "/" + r + "?error").orElse("/"));
            }
        };
    }

    private void fragmentoDeBloqueo(HttpServletRequest request, HttpServletResponse response, int estado, String error) throws IOException {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        String valorToken = token == null ? "" : token.getToken();
        TemplateOutput salida = new StringOutput();
        plantillas.render("fragmentos/bloqueo.jte", new ModeloBloqueo(valorToken, error, true,
                RutasReservadas.deInstitucion(request.getParameter(ProveedorEntrada.CAMPO_INSTITUCION)).or(() -> rutaRecordada(request)).orElse("")), salida);
        response.setStatus(estado);
        response.setHeader("HX-Retarget", "#bloqueo");
        response.setHeader("HX-Reswap", "innerHTML");
        response.setHeader("HX-Trigger", "{\"" + EVENTO_CSRF + "\": {\"token\": \"" + valorToken + "\"}}");
        response.setContentType("text/html;charset=UTF-8");
        response.getWriter().write(salida.toString());
    }

    private static String disparadorCsrf(HttpServletRequest request) {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        String valor = token == null ? "" : token.getToken();
        return "{\"" + EVENTO_CSRF + "\": {\"token\": \"" + valor + "\"}, \"sesion-renovada\": true}";
    }

    /** Solo rutas relativas al propio sitio: una letra o dígito tras la barra y sin esquema, barra doble ni caracteres de control. */
    public static String destinoSeguro(String volver) {
        if (volver == null || !DESTINO_LOCAL.matcher(volver).matches()) {
            return "/";
        }
        return volver;
    }

    private static final java.util.regex.Pattern DESTINO_LOCAL = java.util.regex.Pattern.compile("^/(?!/)[A-Za-z0-9/_.~%?=&-]*$");

    /** Al salir, vuelve a la entrada de la institución de quien salió. */
    public LogoutSuccessHandler alSalir() {
        return (request, response, autenticacion) -> response.sendRedirect(
                autenticacion != null && autenticacion.getPrincipal() instanceof UsuarioSesion u ? "/" + u.ruta() : "/");
    }

    /** La ruta de la última entrada visitada en este navegador, si todavía es una ruta de institución válida. */
    public static Optional<String> rutaRecordada(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies).filter(c -> COOKIE_INSTITUCION.equals(c.getName())).findFirst()
                .flatMap(c -> RutasReservadas.deInstitucion(c.getValue()));
    }

    /**
     * Recuerda la ruta de entrada en este navegador por 180 días (sin datos personales: solo la ruta). Secure cuando la
     * petición llega por HTTPS; en http://localhost, mientras no haya certificado, no.
     */
    public static void recordarRuta(HttpServletRequest request, HttpServletResponse response, String ruta) {
        Cookie cookie = new Cookie(COOKIE_INSTITUCION, ruta);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setSecure(request.isSecure());
        cookie.setMaxAge(180 * 24 * 3600);
        cookie.setAttribute("SameSite", "Strict");
        response.addCookie(cookie);
    }

    /** Modelo del fragmento de bloqueo: token CSRF, error opcional, si se muestra como overlay y la ruta de la institución. */
    public record ModeloBloqueo(String csrf, String error, boolean overlay, String ruta) {
    }
}
