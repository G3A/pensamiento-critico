package pensamiento.web.usuarios;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import pensamiento.web.Pagina;
import pensamiento.web.seguridad.UsuarioSesion;

/** Pantalla mínima de administración (P21 reducida): crear y desactivar cuentas. Solo el administrador. */
@Controller
public class ControladorUsuarios {

    private final ServicioUsuarios servicio;
    private final Pagina.Fabrica paginas;

    public ControladorUsuarios(ServicioUsuarios servicio, Pagina.Fabrica paginas) {
        this.servicio = servicio;
        this.paginas = paginas;
    }

    @GetMapping("/usuarios")
    @Transactional(readOnly = true)
    public String lista(HttpServletRequest request, Model modelo) {
        UsuarioSesion yo = paginas.usuarioActual();
        modelo.addAttribute("pagina", paginas.crear("Usuarios", request));
        modelo.addAttribute("usuarios", servicio.todos(yo.institucionId()));
        modelo.addAttribute("error", request.getParameter("error"));
        return "usuarios";
    }

    @PostMapping("/usuarios")
    @Transactional
    public ResponseEntity<Void> crear(@RequestParam String nombre, @RequestParam(required = false) String pin) {
        UsuarioSesion yo = paginas.usuarioActual();
        try {
            servicio.crearPersona(yo.id(), yo.institucionId(), nombre, pin);
        } catch (ServicioUsuarios.DatosInvalidos | pensamiento.nucleo.puertos.RepositorioUsuarios.NombreRepetido e) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).header("X-Motivo", e.getMessage()).build();
        }
        return ResponseEntity.status(HttpStatus.SEE_OTHER).header("Location", "/usuarios").build();
    }

    @PostMapping("/usuarios/{id}/desactivar")
    @Transactional
    public ResponseEntity<Void> desactivar(@PathVariable UUID id) {
        UsuarioSesion yo = paginas.usuarioActual();
        try {
            servicio.desactivar(yo.id(), yo.institucionId(), id);
        } catch (ServicioUsuarios.DatosInvalidos e) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).header("X-Motivo", e.getMessage()).build();
        }
        return ResponseEntity.status(HttpStatus.SEE_OTHER).header("Location", "/usuarios").build();
    }
}
