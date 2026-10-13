package pensamiento.web.operador;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import pensamiento.nucleo.puertos.RepositorioOperador;
import pensamiento.web.Pagina;
import pensamiento.web.seguridad.ContextoRls;
import pensamiento.web.tecnicas.Fechas;

/**
 * Panel del operador del servicio (/operador): la lista de instituciones (nombre, ruta, estado y si ya tiene cuentas),
 * crear una, suspenderla o reactivarla e invitar a su primer administrador. El código de invitación se muestra una sola vez.
 * Todo corre sin usuario ni institución fijados: RLS no le deja ver ninguna fila de ninguna institución.
 */
@Controller
public class ControladorOperador {

    /** Lo que pinta el panel. {@code invitacion} y {@code invitada} solo después de invitar; {@code error} si algo no se pudo. */
    public record VistaOperador(List<RepositorioOperador.InstitucionDelOperador> instituciones, List<RepositorioOperador.Evento> auditoria,
                                Optional<ServicioOperador.Invitacion> invitacion, Optional<String> invitada, Optional<String> error,
                                String nombreEscrito, String rutaEscrita, java.time.ZoneId zona) {

        public String fecha(java.time.Instant instante) {
            return Fechas.corta(instante, zona);
        }
    }

    private final ServicioOperador servicio;
    private final Pagina.Fabrica paginas;
    private final TransactionTemplate tx;
    private final pensamiento.nucleo.puertos.Reloj reloj;

    public ControladorOperador(ServicioOperador servicio, Pagina.Fabrica paginas, PlatformTransactionManager gestor,
                               pensamiento.nucleo.puertos.Reloj reloj) {
        this.servicio = servicio;
        this.paginas = paginas;
        this.tx = new TransactionTemplate(gestor);
        this.reloj = reloj;
    }

    @GetMapping("/operador/entrar")
    public String entrar(HttpServletRequest request, Model modelo) {
        modelo.addAttribute("pagina", paginas.crear("Operador del servicio", request));
        modelo.addAttribute("error", request.getParameter("error") != null);
        return "operador/entrar";
    }

    @GetMapping("/operador")
    public String panel(HttpServletRequest request, Model modelo) {
        return pintar(request, modelo, Optional.empty(), Optional.empty(), Optional.empty(), "", "");
    }

    @PostMapping("/operador/instituciones")
    public String crear(@RequestParam(defaultValue = "") String nombre, @RequestParam(defaultValue = "") String ruta,
                        HttpServletRequest request, HttpServletResponse response, Model modelo) {
        try {
            sinContexto(() -> servicio.crearInstitucion(yo(), nombre, ruta));
            return "redirect:/operador";
        } catch (ServicioOperador.DatosInvalidos | RepositorioOperador.RutaRepetida e) {
            response.setStatus(422);
            return pintar(request, modelo, Optional.empty(), Optional.empty(), Optional.of(e.getMessage()), nombre, ruta);
        }
    }

    @PostMapping("/operador/instituciones/{id}/suspender")
    public String suspender(@PathVariable UUID id) {
        sinContexto(() -> servicio.suspender(yo(), id));
        return "redirect:/operador";
    }

    @PostMapping("/operador/instituciones/{id}/reactivar")
    public String reactivar(@PathVariable UUID id) {
        sinContexto(() -> servicio.reactivar(yo(), id));
        return "redirect:/operador";
    }

    @PostMapping("/operador/instituciones/{id}/invitar")
    public String invitar(@PathVariable UUID id, HttpServletRequest request, Model modelo) {
        ServicioOperador.Invitacion invitacion = sinContexto(() -> servicio.invitar(yo(), id));
        Optional<String> ruta = sinContexto(() -> servicio.instituciones()).stream()
                .filter(i -> i.institucion().id().equals(id)).map(i -> i.institucion().ruta()).findFirst();
        return pintar(request, modelo, Optional.of(invitacion), ruta, Optional.empty(), "", "");
    }

    private String pintar(HttpServletRequest request, Model modelo, Optional<ServicioOperador.Invitacion> invitacion,
                          Optional<String> invitada, Optional<String> error, String nombre, String ruta) {
        modelo.addAttribute("pagina", paginas.crear("Operador del servicio", request));
        modelo.addAttribute("v", new VistaOperador(sinContexto(servicio::instituciones), sinContexto(() -> servicio.auditoria(20)),
                invitacion, invitada, error, nombre, ruta, reloj.zona()));
        return "operador/panel";
    }

    private <T> T sinContexto(Supplier<T> accion) {
        return ContextoRls.con(Optional.empty(), Optional.empty(), () -> tx.execute(e -> accion.get()));
    }

    private static UUID yo() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof OperadorSesion operador) {
            return operador.id();
        }
        throw new IllegalStateException("No hay sesión de operador");
    }
}
