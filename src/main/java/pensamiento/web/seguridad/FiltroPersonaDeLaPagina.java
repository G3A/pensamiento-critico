package pensamiento.web.seguridad;

import java.io.IOException;
import java.util.Optional;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * RF-02b (hito 9): cada página manda en la cabecera X-Persona el id de quien la abrió. Si llega una petición desde la página
 * de A con la sesión de B (B entró en otra pestaña del mismo navegador), no se procesa: recibe 401 con el overlay de A, y
 * lo que A escribió no se guarda en la cuenta de B. Una petición sin la cabecera (navegación normal) sigue igual.
 */
public class FiltroPersonaDeLaPagina extends OncePerRequestFilter {

    private final RespuestasSesion respuestas;

    public FiltroPersonaDeLaPagina(RespuestasSesion respuestas) {
        this.respuestas = respuestas;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain cadena)
            throws ServletException, IOException {
        Optional<String> dePagina = Optional.ofNullable(request.getHeader(RespuestasSesion.CABECERA_PERSONA)).filter(p -> !p.isBlank());
        Optional<UsuarioSesion> sesion = ContextoRls.usuarioDeSesion();
        boolean entrando = "/sesion".equals(request.getRequestURI());   // el overlay de A puede volver a entrar como A
        if (!entrando && dePagina.isPresent() && sesion.isPresent() && !dePagina.get().equals(sesion.get().id().toString())) {
            respuestas.rechazarOtraPersona(request, response);
            return;
        }
        cadena.doFilter(request, response);
    }
}
