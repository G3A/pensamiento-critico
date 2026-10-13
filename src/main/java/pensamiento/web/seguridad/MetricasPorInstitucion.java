package pensamiento.web.seguridad;

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.server.observation.DefaultServerRequestObservationConvention;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;

import pensamiento.web.operador.OperadorSesion;

/**
 * Las métricas http.server.requests (latencia y errores) llevan la institución como etiqueta (hito 8): la ruta de quien
 * tiene sesión, "operador" para el operador del servicio y "ninguna" antes de entrar. Nunca el nombre ni el texto de una
 * persona. La sesión se lee del HttpSession porque al cerrar la observación Spring Security ya limpió su contexto.
 */
@Component
public class MetricasPorInstitucion extends DefaultServerRequestObservationConvention {

    public static final String ETIQUETA = "institucion";

    @Override
    public KeyValues getLowCardinalityKeyValues(ServerRequestObservationContext contexto) {
        HttpServletRequest request = contexto.getCarrier();
        return super.getLowCardinalityKeyValues(contexto).and(KeyValue.of(ETIQUETA, request == null ? "ninguna" : institucion(request)));
    }

    static String institucion(HttpServletRequest request) {
        Object guardado;
        try {
            HttpSession sesion = request.getSession(false);
            guardado = sesion == null ? null : sesion.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        } catch (IllegalStateException sesionInvalidada) {
            return "ninguna";     // salió en esta misma petición
        }
        if (guardado instanceof SecurityContext contexto) {
            org.springframework.security.core.Authentication autenticacion = contexto.getAuthentication();
            Object principal = autenticacion == null ? null : autenticacion.getPrincipal();
            if (principal instanceof UsuarioSesion usuario) {
                return usuario.ruta();
            }
            if (principal instanceof OperadorSesion) {
                return "operador";
            }
        }
        return "ninguna";
    }
}
