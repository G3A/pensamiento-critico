package pensamiento.web;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import pensamiento.nucleo.Usuario;
import pensamiento.nucleo.puertos.RepositorioUsuarios;
import pensamiento.web.seguridad.ContextoRls;
import pensamiento.web.seguridad.RespuestasSesion;

/**
 * Pantalla de bloqueo "¿Quién eres?": lista las personas activas de la instalación y pide el PIN.
 * No hay sesión todavía, así que la institución se fija de forma explícita antes de abrir la transacción
 * (RLS sobre la tabla usuario filtra por institución).
 */
@Controller
public class ControladorSesion {

    private final RepositorioUsuarios usuarios;
    private final Pagina.Fabrica paginas;
    private final TransactionTemplate tx;

    public ControladorSesion(RepositorioUsuarios usuarios, Pagina.Fabrica paginas, PlatformTransactionManager gestor) {
        this.usuarios = usuarios;
        this.paginas = paginas;
        this.tx = new TransactionTemplate(gestor);
        this.tx.setReadOnly(true);
    }

    @GetMapping("/bloqueo")
    public String bloqueo(HttpServletRequest request, Model modelo) {
        List<Usuario> personas = tx.execute(e -> usuarios.institucionUnica())
                .map(inst -> ContextoRls.conInstitucion(inst, () -> tx.execute(e -> usuarios.todos(inst))))
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
