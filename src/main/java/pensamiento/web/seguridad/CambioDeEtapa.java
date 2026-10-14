package pensamiento.web.seguridad;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

/**
 * Pasa la sesión de una persona a otra etapa de la entrada (hito 9): después de elegir su contraseña o de confirmar con su
 * llave de acceso. Cambia el id de la sesión (como al entrar) y guarda el contexto nuevo en la sesión del servidor.
 */
public final class CambioDeEtapa {

    private static final SecurityContextRepository REPOSITORIO = new HttpSessionSecurityContextRepository();

    private CambioDeEtapa() {
    }

    public static void aplicar(HttpServletRequest request, HttpServletResponse response, org.springframework.security.core.userdetails.UserDetails nueva) {
        request.changeSessionId();
        SecurityContext contexto = SecurityContextHolder.createEmptyContext();
        UsernamePasswordAuthenticationToken autenticacion = UsernamePasswordAuthenticationToken.authenticated(nueva, null, nueva.getAuthorities());
        contexto.setAuthentication(autenticacion);
        SecurityContextHolder.setContext(contexto);
        REPOSITORIO.saveContext(contexto, request, response);
    }
}
