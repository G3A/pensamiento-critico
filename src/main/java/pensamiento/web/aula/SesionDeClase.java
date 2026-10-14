package pensamiento.web.aula;

import java.time.Duration;
import java.time.Instant;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.servlet.HandlerInterceptor;

import pensamiento.nucleo.puertos.Reloj;
import pensamiento.web.seguridad.ContextoRls;
import pensamiento.web.seguridad.UsuarioSesion;

/**
 * Hito 10, entregable 4: mientras la clase de su grupo está abierta, la sesión de un estudiante dura lo que le queda a la
 * clase (hasta 60 minutos) en vez de 10 minutos sin actividad. Se revisa a lo sumo una vez por minuto por sesión, para no
 * consultar la base en cada petición; al terminar la clase, "Terminar clase" cierra esas sesiones.
 */
public class SesionDeClase implements HandlerInterceptor {

    static final String REVISADA_EN = "aula.sesion-revisada-en";
    static final Duration CADA = Duration.ofMinutes(1);

    private final ServicioAula servicio;
    private final Reloj reloj;

    public SesionDeClase(ServicioAula servicio, Reloj reloj) {
        this.servicio = servicio;
        this.reloj = reloj;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        UsuarioSesion yo = ContextoRls.usuarioDeSesion().filter(u -> u.esEstudiante() && u.lista()).orElse(null);
        HttpSession sesion = request.getSession(false);
        if (yo == null || sesion == null) {
            return true;
        }
        Instant ahora = reloj.ahora();
        if (sesion.getAttribute(REVISADA_EN) instanceof Instant antes && Duration.between(antes, ahora).compareTo(CADA) < 0) {
            return true;
        }
        sesion.setMaxInactiveInterval((int) servicio.duracionDeSesion(yo.actor()).toSeconds());
        sesion.setAttribute(REVISADA_EN, ahora);
        return true;
    }
}
