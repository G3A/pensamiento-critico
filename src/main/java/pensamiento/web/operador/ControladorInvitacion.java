package pensamiento.web.operador;

import java.util.Optional;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import pensamiento.web.Pagina;
import pensamiento.web.seguridad.ContextoRls;
import pensamiento.web.seguridad.RutasReservadas;
import pensamiento.web.usuarios.ServicioUsuarios;

/**
 * /<ruta>/invitacion: quien recibió la invitación del operador escribe el código, su nombre y su contraseña, y queda como primer
 * administrador de la institución. Un código que no sirve (mal escrito, usado, caducado o de una institución que ya tiene
 * cuentas) responde 410; una ruta que no existe o está suspendida, 404 sin cuerpo.
 */
@Controller
public class ControladorInvitacion {

    /** Lo que pinta el formulario de canje. */
    public record VistaInvitacion(String ruta, Optional<String> error, String nombreEscrito) {
    }

    private static final String RUTA = "/{ruta:[a-z0-9][a-z0-9-]{1,38}[a-z0-9]}/invitacion";

    private final ServicioOperador servicio;
    private final Pagina.Fabrica paginas;
    private final TransactionTemplate tx;

    public ControladorInvitacion(ServicioOperador servicio, Pagina.Fabrica paginas, PlatformTransactionManager gestor) {
        this.servicio = servicio;
        this.paginas = paginas;
        this.tx = new TransactionTemplate(gestor);
    }

    @GetMapping(RUTA)
    public String formulario(@PathVariable String ruta, HttpServletRequest request, HttpServletResponse response, Model modelo) {
        if (institucion(ruta).isEmpty()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return null;
        }
        return pintar(request, modelo, new VistaInvitacion(ruta, Optional.empty(), ""));
    }

    @PostMapping(RUTA)
    public String canjear(@PathVariable String ruta, @RequestParam(defaultValue = "") String codigo,
                          @RequestParam(defaultValue = "") String nombre, @RequestParam(defaultValue = "") String contrasena,
                          @RequestParam(defaultValue = "") String repetida,
                          HttpServletRequest request, HttpServletResponse response, Model modelo) {
        Optional<UUID> institucion = institucion(ruta);
        if (institucion.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return null;
        }
        UUID id = institucion.get();
        try {
            if (!contrasena.equals(repetida)) {
                throw new ServicioUsuarios.DatosInvalidos("Las dos contraseñas no coinciden.");
            }
            ContextoRls.conInstitucion(id, () -> tx.execute(e -> servicio.canjear(id, codigo, nombre, contrasena)));
            return "redirect:/" + ruta + "?bienvenida";
        } catch (ServicioOperador.InvitacionNoValida e) {
            response.setStatus(HttpServletResponse.SC_GONE);
            return pintar(request, modelo, new VistaInvitacion(ruta, Optional.of(e.getMessage()), nombre));
        } catch (ServicioUsuarios.DatosInvalidos e) {
            response.setStatus(422);
            return pintar(request, modelo, new VistaInvitacion(ruta, Optional.of(e.getMessage()), nombre));
        }
    }

    private Optional<UUID> institucion(String ruta) {
        return RutasReservadas.deInstitucion(ruta).flatMap(r ->
                ContextoRls.con(Optional.empty(), Optional.empty(), () -> tx.execute(e -> servicio.institucionActiva(r))));
    }

    private String pintar(HttpServletRequest request, Model modelo, VistaInvitacion vista) {
        modelo.addAttribute("pagina", paginas.crear("Tengo una invitación", request));
        modelo.addAttribute("v", vista);
        return "operador/invitacion";
    }
}
