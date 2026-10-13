package pensamiento.web.seguridad;

import java.io.IOException;
import java.util.Optional;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * La sesión queda atada a la institución por la que se entró (hito 8): con una sesión abierta, una petición que trae la
 * ruta de otra institución (la entrada /<otra>, su /<otra>/invitacion o el campo "institucion" del formulario de entrada)
 * recibe 403 sin cuerpo. Para cambiar de institución hay que salir primero.
 */
public class FiltroInstitucionDeSesion extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain cadena)
            throws ServletException, IOException {
        Optional<UsuarioSesion> usuario = ContextoRls.usuarioDeSesion();
        if (usuario.isPresent() && traeOtraInstitucion(request, usuario.get().ruta())) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        cadena.doFilter(request, response);
    }

    public static boolean traeOtraInstitucion(HttpServletRequest request, String rutaDeSesion) {
        Optional<String> enLaDireccion = rutaDeEntrada(request.getRequestURI());
        Optional<String> enElFormulario = "/sesion".equals(request.getRequestURI())
                ? Optional.ofNullable(request.getParameter(ProveedorEntrada.CAMPO_INSTITUCION)) : Optional.empty();
        return enLaDireccion.filter(r -> !r.equals(rutaDeSesion)).isPresent()
                || enElFormulario.filter(r -> !r.equals(rutaDeSesion)).isPresent();
    }

    /** La ruta de institución de una dirección de entrada: /<ruta> o /<ruta>/invitacion; las demás rutas no traen institución. */
    private static Optional<String> rutaDeEntrada(String uri) {
        String sinBarra = uri.startsWith("/") ? uri.substring(1) : uri;
        String segmento = sinBarra.endsWith("/invitacion") ? sinBarra.substring(0, sinBarra.length() - "/invitacion".length()) : sinBarra;
        return segmento.contains("/") ? Optional.empty() : RutasReservadas.deInstitucion(segmento);
    }
}
