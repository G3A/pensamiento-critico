package pensamiento.web;

import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Component;

import pensamiento.ia.MonitorIa;
import pensamiento.nucleo.puertos.EstadoIa;
import pensamiento.web.seguridad.ContextoRls;
import pensamiento.web.seguridad.UsuarioSesion;

/** Lo que el layout necesita en toda página: título, quién está, estado de la IA y el token CSRF para htmx. */
public record Pagina(String titulo, Optional<UsuarioSesion> usuario, EstadoIa ia, String csrf) {

    public boolean modoPlantillas() {
        return !ia.disponible();
    }

    public boolean esAdministrador() {
        return usuario.map(UsuarioSesion::esAdministrador).orElse(false);
    }

    @Component
    public static class Fabrica {

        private final MonitorIa monitorIa;

        public Fabrica(MonitorIa monitorIa) {
            this.monitorIa = monitorIa;
        }

        public Pagina crear(String titulo, HttpServletRequest request) {
            CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            return new Pagina(titulo, ContextoRls.usuarioDeSesion(), monitorIa.estado(), token == null ? "" : token.getToken());
        }

        public UsuarioSesion usuarioActual() {
            return ContextoRls.usuarioDeSesion().orElseThrow(() -> new IllegalStateException("No hay sesión"));
        }
    }
}
