package pensamiento.web.seguridad;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

import gg.jte.TemplateEngine;
import gg.jte.TemplateOutput;
import gg.jte.output.StringOutput;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.session.SessionInformationExpiredStrategy;
import org.springframework.stereotype.Component;

/**
 * Cómo responde la sesión según quién pregunta. Para una petición htmx (sesión expirada en medio de un formulario) devuelve
 * el formulario de entrada como overlay apuntado a #bloqueo: la página y el borrador quedan intactos y solo la misma
 * persona puede seguir (RF-02b). Para una navegación normal, redirige a la entrada de la institución (/<ruta>) con la URL
 * a la que volver. La ruta sale de la cookie PENSAMIENTO_INSTITUCION, que la entrada deja al visitarla: es solo una pista
 * para volver a la misma pantalla, la institución siempre la resuelve el servidor (hito 8).
 *
 * <p>Hito 9: un intento fallido responde 401 con la página renderizada, el nombre conservado y el eco «Probaste como …»;
 * con espera, 429 con cuánto falta. Una sesión cerrada desde afuera (restablecer, desactivar) recibe 401.
 */
@Component
public class RespuestasSesion {

    public static final String EVENTO_CSRF = "csrf-renovado";
    public static final String COOKIE_INSTITUCION = "PENSAMIENTO_INSTITUCION";
    /** La página de cada persona manda su id en esta cabecera (layout.jte); el overlay solo acepta a esa persona. */
    public static final String CABECERA_PERSONA = "X-Persona";
    /** Atributos con que el rechazo de la entrada se reenvía a /sesion/rechazo para pintarse con su estado. */
    public static final String ATRIBUTO_ERROR = "entrada.error";
    public static final String ATRIBUTO_ESTADO = "entrada.estado";
    public static final String RUTA_RECHAZO = "/sesion/rechazo";

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
                overlay(request, response, HttpServletResponse.SC_UNAUTHORIZED, Optional.empty(), "");
            } else {
                String volver = request.getRequestURI() + (request.getQueryString() == null ? "" : "?" + request.getQueryString());
                Optional<String> ruta = rutaRecordada(request);
                response.sendRedirect(ruta.isEmpty() ? "/" : "/" + ruta.get() + "?volver=" + URLEncoder.encode(volver, StandardCharsets.UTF_8));
            }
        };
    }

    public AuthenticationSuccessHandler alEntrar() {
        return (request, response, autenticacion) -> {
            Optional<UsuarioSesion> yo = autenticacion.getPrincipal() instanceof UsuarioSesion u ? Optional.of(u) : Optional.empty();
            if (esHtmx(request) && yo.map(UsuarioSesion::lista).orElse(false)) {
                // Vacía el overlay #bloqueo y entrega el token CSRF de la sesión nueva.
                response.setStatus(HttpServletResponse.SC_OK);
                response.setHeader("HX-Retarget", "#bloqueo");
                response.setHeader("HX-Reswap", "innerHTML");
                response.setHeader("HX-Trigger", disparadorCsrf(request));
                response.setContentType("text/html;charset=UTF-8");
                response.getWriter().write("");
            } else if (yo.isPresent() && !yo.get().lista()) {
                redirigir(request, response, yo.get().etapa().pantalla());
            } else {
                redirigir(request, response, destinoSeguro(request.getParameter("volver")));
            }
        };
    }

    public AuthenticationFailureHandler alFallar() {
        return (request, response, excepcion) -> {
            int estado = excepcion instanceof ProveedorEntrada.DemasiadosIntentos ? 429 : HttpServletResponse.SC_UNAUTHORIZED;
            String mensaje = mensaje(excepcion, request.getParameter("nombre"));
            if (esHtmx(request)) {
                overlay(request, response, estado, Optional.of(mensaje), valor(request.getParameter("nombre")));
            } else {
                request.setAttribute(ATRIBUTO_ERROR, mensaje);
                request.setAttribute(ATRIBUTO_ESTADO, estado);
                try {
                    request.getRequestDispatcher(RUTA_RECHAZO).forward(request, response);
                } catch (ServletException e) {
                    throw new IOException(e);
                }
            }
        };
    }

    /** El texto del rechazo: genérico con eco de lo tecleado, sin decir si la cuenta existe. */
    public static String mensaje(AuthenticationException excepcion, String nombre) {
        if (excepcion instanceof ProveedorEntrada.DemasiadosIntentos espera) {
            return "Hubo muchos intentos con ese nombre. Espera " + duracion(espera.espera()) + " y vuelve a probar. Si olvidaste tu "
                    + "contraseña, pide un código para restablecerla a quien te dio la cuenta.";
        }
        if (excepcion instanceof ProveedorEntrada.OtraPersona) {
            return "Esta página es de otra persona: solo ella puede seguir aquí. Para entrar con tu cuenta, abre la entrada en otra pestaña.";
        }
        String escrito = valor(nombre).strip();
        return escrito.isEmpty() ? "Escribe tu nombre y tu contraseña."
                : "Probaste como «" + (escrito.length() > 60 ? escrito.substring(0, 60) + "…" : escrito) + "». El nombre o la contraseña no coinciden.";
    }

    public static String duracion(Duration espera) {
        long segundos = Math.max(1, espera.toSeconds() + (espera.toNanosPart() > 0 ? 1 : 0));
        if (segundos < 60) {
            return segundos + (segundos == 1 ? " segundo" : " segundos");
        }
        long minutos = (segundos + 59) / 60;
        return minutos + (minutos == 1 ? " minuto" : " minutos");
    }

    /**
     * Una sesión cerrada desde afuera (restablecer la contraseña, desactivar la cuenta) recibe 401: el overlay en htmx; si
     * no, la entrada de su institución con el aviso.
     */
    public SessionInformationExpiredStrategy alCerrarseDesdeAfuera() {
        return evento -> {
            HttpServletRequest request = evento.getRequest();
            HttpServletResponse response = evento.getResponse();
            SecurityContextHolder.clearContext();
            String aviso = "Tu sesión se cerró: cambiaron tu acceso. Entra de nuevo.";
            if (esHtmx(request)) {
                overlay(request, response, HttpServletResponse.SC_UNAUTHORIZED, Optional.of(aviso), "");
                return;
            }
            request.setAttribute(ATRIBUTO_ERROR, aviso);
            request.setAttribute(ATRIBUTO_ESTADO, HttpServletResponse.SC_UNAUTHORIZED);
            request.getRequestDispatcher(RUTA_RECHAZO).forward(request, response);
        };
    }

    /** Quien todavía no termina de entrar (elegir contraseña, llave de acceso) va a la pantalla de su etapa; los demás, 403. */
    public AccessDeniedHandler alNoTenerPermiso() {
        return (request, response, excepcion) -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof UsuarioSesion u && !u.lista()) {
                redirigir(request, response, u.etapa().pantalla());
                return;
            }
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
        };
    }

    /**
     * Una petición htmx desde la página de otra persona (su cabecera X-Persona no es quien tiene la sesión ahora, por
     * ejemplo porque otra persona entró en otra pestaña): no se procesa; recibe 401 con el overlay de esa página.
     */
    public void rechazarOtraPersona(HttpServletRequest request, HttpServletResponse response) throws IOException {
        overlay(request, response, HttpServletResponse.SC_UNAUTHORIZED, Optional.of(mensaje(new ProveedorEntrada.OtraPersona(), "")), "");
    }

    /** Con htmx una redirección 302 se seguiría sola dentro del fragmento: se responde 200 con HX-Redirect. */
    public static void redirigir(HttpServletRequest request, HttpServletResponse response, String destino) throws IOException {
        if (esHtmx(request)) {
            response.setStatus(HttpServletResponse.SC_OK);
            response.setHeader("HX-Redirect", destino);
        } else {
            response.sendRedirect(destino);
        }
    }

    private void overlay(HttpServletRequest request, HttpServletResponse response, int estado, Optional<String> error, String nombre)
            throws IOException {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        String valorToken = token == null ? "" : token.getToken();
        String ruta = RutasReservadas.deInstitucion(request.getParameter(ProveedorEntrada.CAMPO_INSTITUCION)).or(() -> rutaRecordada(request)).orElse("");
        Optional<String> persona = Optional.ofNullable(request.getParameter(ProveedorEntrada.CAMPO_PERSONA))
                .or(() -> Optional.ofNullable(request.getHeader(CABECERA_PERSONA)))
                .flatMap(ConfiguracionSeguridad::uuid).map(Object::toString);
        TemplateOutput salida = new StringOutput();
        plantillas.render("fragmentos/bloqueo.jte", new ModeloEntrada(valorToken, ruta, "", nombre, error, Optional.empty(), true, persona), salida);
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

    private static String valor(String texto) {
        return texto == null ? "" : texto;
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

    /**
     * Modelo del formulario único de entrada (tag/entrar.jte), en la página y en el overlay: token CSRF, ruta de la
     * institución, adónde volver, el nombre escrito (se conserva tras un error), el error con eco, un aviso, si es overlay y
     * la persona dueña de la página (solo en el overlay).
     */
    public record ModeloEntrada(String csrf, String ruta, String volver, String nombre, Optional<String> error, Optional<String> aviso,
                                boolean overlay, Optional<String> persona, Optional<ClaseEnEntrada> clase) {

        public ModeloEntrada(String csrf, String ruta, String volver, String nombre, Optional<String> error, Optional<String> aviso,
                             boolean overlay, Optional<String> persona) {
            this(csrf, ruta, volver, nombre, error, aviso, overlay, persona, Optional.empty());
        }
    }

    /**
     * Hito 10: la entrada con el código de una clase abierta (QR o escrito). Dice de qué grupo es y, solo si su docente la
     * activó, trae la lista de nombres de ese grupo para tocar el propio. {@code conLista}: el nombre vino de la lista.
     */
    public record ClaseEnEntrada(String codigo, String grupo, java.util.List<String> lista, boolean conLista) {

        /** El enlace de un nombre de la lista: vuelve a la entrada con ese nombre escrito. */
        public String enlace(String ruta, String nombre) {
            return "/" + ruta + "?clase=" + java.net.URLEncoder.encode(codigo, java.nio.charset.StandardCharsets.UTF_8) + "&lista=si&nombre="
                    + java.net.URLEncoder.encode(nombre, java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}
