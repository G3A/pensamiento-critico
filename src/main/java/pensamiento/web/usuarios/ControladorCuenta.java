package pensamiento.web.usuarios;

import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import pensamiento.web.Pagina;
import pensamiento.web.seguridad.CambioDeEtapa;
import pensamiento.web.seguridad.UsuarioSesion;

/**
 * Mi cuenta (hito 9, RF-17): cada persona cambia su contraseña dando la actual. Y la etapa obligatoria de quien entró con
 * el PIN de las versiones anteriores: elige su contraseña antes de seguir (/cuenta/contrasena-nueva).
 */
@Controller
public class ControladorCuenta {

    /** Lo que pinta Mi cuenta y la elección de contraseña: quién es, el error y el aviso. */
    public record VistaCuenta(UsuarioSesion yo, Optional<String> error, Optional<String> aviso) {
    }

    private final ServicioUsuarios servicio;
    private final Pagina.Fabrica paginas;

    public ControladorCuenta(ServicioUsuarios servicio, Pagina.Fabrica paginas) {
        this.servicio = servicio;
        this.paginas = paginas;
    }

    @GetMapping("/cuenta")
    public String cuenta(@RequestParam(required = false) String listo, HttpServletRequest request, Model modelo) {
        return pintar("cuenta", "Mi cuenta", request, modelo, Optional.empty(),
                listo == null ? Optional.empty() : Optional.of("Tu contraseña quedó cambiada."));
    }

    @PostMapping("/cuenta/contrasena")
    public String cambiar(@RequestParam(defaultValue = "") String actual, @RequestParam(defaultValue = "") String nueva,
                          @RequestParam(defaultValue = "") String repetida, HttpServletRequest request, HttpServletResponse response,
                          Model modelo) {
        UsuarioSesion yo = paginas.usuarioActual();
        try {
            servicio.cambiarContrasena(yo.institucionId(), yo.id(), actual, nueva, repetida);
            return "redirect:/cuenta?listo";
        } catch (ServicioUsuarios.DatosInvalidos e) {
            response.setStatus(422);
            return pintar("cuenta", "Mi cuenta", request, modelo, Optional.of(e.getMessage()), Optional.empty());
        }
    }

    @GetMapping("/cuenta/contrasena-nueva")
    public String elegir(HttpServletRequest request, Model modelo) {
        if (paginas.usuarioActual().etapa() != UsuarioSesion.Etapa.ELEGIR_CONTRASENA) {
            return "redirect:" + paginas.usuarioActual().etapa().pantalla();
        }
        return pintar("cuenta-contrasena-nueva", "Elige tu contraseña", request, modelo, Optional.empty(), Optional.empty());
    }

    @PostMapping("/cuenta/contrasena-nueva")
    public String elegida(@RequestParam(defaultValue = "") String nueva, @RequestParam(defaultValue = "") String repetida,
                          HttpServletRequest request, HttpServletResponse response, Model modelo) {
        UsuarioSesion yo = paginas.usuarioActual();
        if (yo.etapa() != UsuarioSesion.Etapa.ELEGIR_CONTRASENA) {
            return "redirect:" + yo.etapa().pantalla();
        }
        try {
            servicio.reemplazarPin(yo.institucionId(), yo.id(), nueva, repetida);
        } catch (ServicioUsuarios.DatosInvalidos e) {
            response.setStatus(422);
            return pintar("cuenta-contrasena-nueva", "Elige tu contraseña", request, modelo, Optional.of(e.getMessage()), Optional.empty());
        }
        UsuarioSesion siguiente = yo.enEtapa(yo.rol().exigeLlave() ? UsuarioSesion.Etapa.REGISTRAR_LLAVE : UsuarioSesion.Etapa.LISTA, false);
        CambioDeEtapa.aplicar(request, response, siguiente);
        return "redirect:" + siguiente.etapa().pantalla();
    }

    private String pintar(String plantilla, String titulo, HttpServletRequest request, Model modelo, Optional<String> error, Optional<String> aviso) {
        modelo.addAttribute("pagina", paginas.crear(titulo, request));
        modelo.addAttribute("v", new VistaCuenta(paginas.usuarioActual(), error, aviso));
        return plantilla;
    }
}
