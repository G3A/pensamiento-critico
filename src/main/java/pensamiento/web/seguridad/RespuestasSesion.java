package pensamiento.web.seguridad;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import gg.jte.TemplateEngine;
import gg.jte.TemplateOutput;
import gg.jte.output.StringOutput;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Component;

/**
 * Cómo responde la sesión según quién pregunta. Para una petición htmx (sesión expirada en medio de un
 * formulario) devuelve el fragmento de bloqueo "¿Quién eres?" apuntado a #bloqueo: la página y el
 * borrador quedan intactos y, al entrar de nuevo, el usuario sigue donde estaba (RF-02).
 * Para una navegación normal, redirige a /bloqueo con la URL a la que volver.
 */
@Component
public class RespuestasSesion {

    public static final String EVENTO_CSRF = "csrf-renovado";

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
                response.sendRedirect("/bloqueo?volver=" + URLEncoder.encode(volver, StandardCharsets.UTF_8));
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
                String volver = request.getParameter("volver");
                response.sendRedirect(volver != null && volver.startsWith("/") && !volver.startsWith("//") ? volver : "/");
            }
        };
    }

    public AuthenticationFailureHandler alFallar() {
        return (request, response, excepcion) -> {
            if (esHtmx(request)) {
                fragmentoDeBloqueo(request, response, HttpServletResponse.SC_UNAUTHORIZED, "Nombre o PIN incorrectos");
            } else {
                response.sendRedirect("/bloqueo?error");
            }
        };
    }

    private void fragmentoDeBloqueo(HttpServletRequest request, HttpServletResponse response, int estado, String error) throws IOException {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        String valorToken = token == null ? "" : token.getToken();
        TemplateOutput salida = new StringOutput();
        plantillas.render("fragmentos/bloqueo.jte", new ModeloBloqueo(valorToken, error, true), salida);
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

    /** Modelo del fragmento de bloqueo: token CSRF, error opcional y si se muestra como overlay. */
    public record ModeloBloqueo(String csrf, String error, boolean overlay) {
    }
}
