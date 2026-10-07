package pensamiento.web;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import pensamiento.nucleo.Usuario;
import pensamiento.nucleo.puertos.RepositorioUsuarios;
import pensamiento.web.seguridad.ContextoRls;
import pensamiento.web.seguridad.RespuestasSesion;

/** Pantalla de bloqueo "¿Quién eres?": lista las personas de la instalación y pide el PIN. */
@Controller
public class ControladorSesion {

    private final RepositorioUsuarios usuarios;
    private final Pagina.Fabrica paginas;

    public ControladorSesion(RepositorioUsuarios usuarios, Pagina.Fabrica paginas) {
        this.usuarios = usuarios;
        this.paginas = paginas;
    }

    @GetMapping("/bloqueo")
    @Transactional(readOnly = true)
    public String bloqueo(HttpServletRequest request, Model modelo) {
        List<Usuario> personas = usuarios.institucionUnica()
                .map(inst -> ContextoRls.conInstitucion(inst, () -> usuarios.todos(inst)))
                .orElse(List.of())
                .stream().filter(Usuario::activo).toList();
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        String error = request.getParameter("error") != null ? "Nombre o PIN incorrectos" : null;
        modelo.addAttribute("pagina", paginas.crear("¿Quién eres?", request));
        modelo.addAttribute("personas", personas);
        modelo.addAttribute("bloqueo", new RespuestasSesion.ModeloBloqueo(token == null ? "" : token.getToken(), error, false));
        modelo.addAttribute("volver", request.getParameter("volver") == null ? "/" : request.getParameter("volver"));
        return "bloqueo";
    }
}
