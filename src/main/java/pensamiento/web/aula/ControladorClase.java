package pensamiento.web.aula;

import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import pensamiento.nucleo.ClaseDeAula;
import pensamiento.nucleo.Grupo;
import pensamiento.nucleo.puertos.Reloj;
import pensamiento.web.Pagina;
import pensamiento.web.seguridad.UsuarioSesion;

/**
 * Abrir y terminar la clase de un grupo (hito 10, entregable 4). Al abrirla, el docente ve un código corto y un QR grande para
 * proyectar (generado en el servidor como SVG, sin script) que lleva a la entrada de la institución con el código: ahí los
 * estudiantes siguen entrando con su nombre y su contraseña. Mientras la clase está abierta, la sesión de un estudiante del
 * grupo dura lo que la clase. El código en claro solo vive en la sesión del docente (la base guarda su hash), para que pueda
 * recargar la página sin perderlo.
 */
@Controller
public class ControladorClase {

    static final String CODIGOS_EN_SESION = "aula.codigos";

    /** Lo que pinta /grupos/{id}/clase. */
    public record VistaClase(Grupo grupo, Optional<ClaseDeAula> clase, Optional<String> codigo, Optional<String> direccion, Optional<String> qr,
                             String cierra) {
    }

    private final ServicioAula servicio;
    private final Pagina.Fabrica paginas;
    private final ZoneId zona;

    public ControladorClase(ServicioAula servicio, Pagina.Fabrica paginas, Reloj reloj) {
        this.servicio = servicio;
        this.paginas = paginas;
        this.zona = reloj.zona();
    }

    @GetMapping("/grupos/{id}/clase")
    public String clase(@PathVariable UUID id, HttpServletRequest request, Model modelo) {
        UsuarioSesion yo = paginas.usuarioActual();
        Grupo g = servicio.grupoQueAdministro(yo.actor(), id);
        Optional<ClaseDeAula> clase = servicio.claseAbierta(yo.actor(), id);
        Optional<String> codigo = clase.flatMap(c -> Optional.ofNullable(codigos(request.getSession()).get(c.id())));
        Optional<String> direccion = codigo.map(c -> ServletUriComponentsBuilder.fromContextPath(request).path("/" + yo.ruta())
                .queryParam("clase", c).build().toUriString());
        Optional<String> qr = direccion.map(d -> QrSvg.de(d, "QR de la clase de " + g.nombre()));
        String cierra = clase.map(c -> java.time.format.DateTimeFormatter.ofPattern("h:mm a").withZone(zona).format(c.cierraEn())).orElse("");
        modelo.addAttribute("pagina", paginas.crear("Clase · " + g.nombre(), request));
        modelo.addAttribute("v", new VistaClase(g, clase, codigo, direccion, qr, cierra));
        return "clase";
    }

    @PostMapping("/grupos/{id}/clase")
    public String abrir(@PathVariable UUID id, @RequestParam(defaultValue = "50") int minutos, HttpServletRequest request) {
        UsuarioSesion yo = paginas.usuarioActual();
        ServicioAula.ClaseAbierta abierta = servicio.abrirClase(yo.actor(), id, minutos);
        codigos(request.getSession()).put(abierta.clase().id(), abierta.codigo());
        return "redirect:/grupos/" + id + "/clase";
    }

    @PostMapping("/grupos/{id}/clase/terminar")
    public String terminar(@PathVariable UUID id) {
        UsuarioSesion yo = paginas.usuarioActual();
        servicio.terminarClase(yo.actor(), id);
        return "redirect:/grupos/" + id + "?aviso=terminada";
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, String> codigos(HttpSession sesion) {
        Object guardados = sesion.getAttribute(CODIGOS_EN_SESION);
        if (guardados instanceof HashMap<?, ?> mapa) {
            return (Map<UUID, String>) mapa;
        }
        HashMap<UUID, String> nuevos = new HashMap<>();
        sesion.setAttribute(CODIGOS_EN_SESION, nuevos);
        return nuevos;
    }
}
