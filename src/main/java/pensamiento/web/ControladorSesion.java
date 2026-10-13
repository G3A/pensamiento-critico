package pensamiento.web;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import pensamiento.nucleo.Usuario;
import pensamiento.nucleo.puertos.RepositorioInstituciones;
import pensamiento.nucleo.puertos.RepositorioUsuarios;
import pensamiento.web.seguridad.ContextoRls;
import pensamiento.web.seguridad.RespuestasSesion;
import pensamiento.web.seguridad.RutasReservadas;
import pensamiento.web.seguridad.UsuarioSesion;

/**
 * Entrada por institución (hito 8): /<ruta> es la pantalla "¿Quién eres?" de esa institución. Una ruta que no existe o
 * de una institución suspendida responde 404 sin cuerpo. La institución se fija explícitamente antes de abrir la
 * transacción (RLS sobre usuario filtra por institución). /bloqueo queda como atajo a la última entrada visitada.
 */
@Controller
public class ControladorSesion {

    private final RepositorioUsuarios usuarios;
    private final RepositorioInstituciones instituciones;
    private final Pagina.Fabrica paginas;
    private final TransactionTemplate tx;

    public ControladorSesion(RepositorioUsuarios usuarios, RepositorioInstituciones instituciones, Pagina.Fabrica paginas,
                             PlatformTransactionManager gestor) {
        this.usuarios = usuarios;
        this.instituciones = instituciones;
        this.paginas = paginas;
        this.tx = new TransactionTemplate(gestor);
        this.tx.setReadOnly(true);
    }

    @GetMapping("/{ruta:[a-z0-9][a-z0-9-]{1,38}[a-z0-9]}")
    public String entrada(@PathVariable String ruta, HttpServletRequest request, HttpServletResponse response, Model modelo) {
        Optional<UUID> institucion = RutasReservadas.deInstitucion(ruta).flatMap(r ->
                ContextoRls.con(Optional.empty(), Optional.empty(), () -> tx.execute(e -> instituciones.activaPorRuta(r))));
        if (institucion.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return null;
        }
        Optional<UsuarioSesion> yo = ContextoRls.usuarioDeSesion();
        if (yo.isPresent()) {
            return "redirect:/";      // ya entró a esta institución (otra ruta la rechaza FiltroInstitucionDeSesion)
        }
        UUID id = institucion.get();
        List<Usuario> personas = ContextoRls.conInstitucion(id, () -> tx.execute(e -> usuarios.todos(id)))
                .stream().filter(Usuario::activo).toList();
        RespuestasSesion.recordarRuta(request, response, ruta);
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        String error = request.getParameter("error") != null ? "Nombre o PIN incorrectos" : null;
        modelo.addAttribute("pagina", paginas.crear("¿Quién eres?", request));
        modelo.addAttribute("personas", personas);
        modelo.addAttribute("bloqueo", new RespuestasSesion.ModeloBloqueo(token == null ? "" : token.getToken(), error, false, ruta));
        modelo.addAttribute("volver", RespuestasSesion.destinoSeguro(request.getParameter("volver")));
        modelo.addAttribute("aviso", request.getParameter("bienvenida") != null
                ? "Tu cuenta de administrador está lista: entra con tu nombre y tu PIN." : null);
        return "bloqueo";
    }

    /** Atajo de las versiones anteriores: lleva a la entrada de la última institución visitada, o a la raíz. */
    @GetMapping("/bloqueo")
    public String bloqueo(HttpServletRequest request) {
        return RespuestasSesion.rutaRecordada(request)
                .map(r -> "redirect:/" + r + (request.getParameter("volver") == null ? ""
                        : "?volver=" + URLEncoder.encode(RespuestasSesion.destinoSeguro(request.getParameter("volver")), StandardCharsets.UTF_8)))
                .orElse("redirect:/");
    }

    /** El formulario de la raíz: lleva a /<ruta> si tiene forma de ruta; si no, vuelve a la raíz con el aviso. */
    @GetMapping("/ir")
    public String ir(@RequestParam(name = "institucion", required = false) String escrita) {
        String ruta = escrita == null ? "" : escrita.strip().toLowerCase(java.util.Locale.ROOT);
        return RutasReservadas.deInstitucion(ruta).map(r -> "redirect:/" + r).orElse("redirect:/?ruta-invalida");
    }
}
