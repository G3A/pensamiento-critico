package pensamiento.web;

import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Component;

import pensamiento.nucleo.puertos.EstadoIa;
import pensamiento.nucleo.puertos.Ia;
import pensamiento.web.seguridad.ContextoRls;
import pensamiento.web.seguridad.UsuarioSesion;

/** Lo que el layout necesita en toda página: título, quién está, estado de la IA y el token CSRF para htmx. */
public record Pagina(String titulo, Optional<UsuarioSesion> usuario, EstadoIa ia, String csrf) {

    public boolean modoPlantillas() {
        return !ia.disponible();
    }

    /** Los modelos listos, para la cabecera: "qwen3:4b · bge-m3". */
    public String modelos() {
        return String.join(" · ", ia.modelos().stream().map(m -> m.replace(":latest", "")).toList());
    }

    public boolean esAdministrador() {
        return usuario.map(UsuarioSesion::esAdministrador).orElse(false);
    }

    @Component
    public static class Fabrica {

        private final Ia ia;

        public Fabrica(Ia ia) {
            this.ia = ia;
        }

        public Pagina crear(String titulo, HttpServletRequest request) {
            CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            return new Pagina(titulo, ContextoRls.usuarioDeSesion(), ia.estado(), token == null ? "" : token.getToken());
        }

        public UsuarioSesion usuarioActual() {
            return ContextoRls.usuarioDeSesion().orElseThrow(() -> new IllegalStateException("No hay sesión"));
        }
    }
}
