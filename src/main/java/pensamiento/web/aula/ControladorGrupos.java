package pensamiento.web.aula;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import pensamiento.nucleo.Grupo;
import pensamiento.nucleo.Usuario;
import pensamiento.nucleo.puertos.RepositorioAula;
import pensamiento.web.Pagina;
import pensamiento.web.seguridad.UsuarioSesion;
import pensamiento.web.usuarios.ServicioUsuarios;

/**
 * /grupos (hito 10, RF-19): el administrador ve todos los grupos, los crea y asigna su docente; el docente ve solo los suyos
 * ("Mis grupos"). Un grupo ajeno responde 404, igual que uno que no existe.
 */
@Controller
public class ControladorGrupos {

    /** Una fila de /grupos: el grupo, el nombre de su docente y cuántos estudiantes tiene. */
    public record FilaGrupo(Grupo grupo, String docente, int estudiantes) {
    }

    /** Lo que pinta /grupos. */
    public record VistaGrupos(List<FilaGrupo> filas, List<Usuario> docentes, boolean administra, Optional<String> error, String nombreEscrito) {
    }

    /** Lo que pinta /grupos/{id}. */
    public record VistaGrupo(Grupo grupo, String docente, List<RepositorioAula.Miembro> miembros, List<Usuario> docentes, boolean administra,
                             Optional<String> aviso, Optional<String> error) {
    }

    private final ServicioAula servicio;
    private final Pagina.Fabrica paginas;

    public ControladorGrupos(ServicioAula servicio, Pagina.Fabrica paginas) {
        this.servicio = servicio;
        this.paginas = paginas;
    }

    @GetMapping("/grupos")
    public String grupos(HttpServletRequest request, Model modelo) {
        return pintarGrupos(request, modelo, Optional.empty(), "");
    }

    @PostMapping("/grupos")
    public String crear(@RequestParam(defaultValue = "") String nombre, @RequestParam(defaultValue = "") String docente,
                        HttpServletRequest request, HttpServletResponse response, Model modelo) {
        UsuarioSesion yo = paginas.usuarioActual();
        try {
            Grupo g = servicio.crearGrupo(yo.actor(), nombre, uuid(docente));
            return "redirect:/grupos/" + g.id() + "?aviso=creado";
        } catch (ServicioUsuarios.DatosInvalidos e) {
            response.setStatus(422);
            return pintarGrupos(request, modelo, Optional.of(e.getMessage()), nombre);
        }
    }

    @GetMapping("/grupos/{id}")
    public String grupo(@PathVariable UUID id, @RequestParam(required = false) String aviso, HttpServletRequest request, Model modelo) {
        return pintarGrupo(request, modelo, id, Optional.ofNullable(aviso).map(ControladorGrupos::textoDeAviso), Optional.empty());
    }

    @PostMapping("/grupos/{id}/docente")
    public String asignarDocente(@PathVariable UUID id, @RequestParam(defaultValue = "") String docente, HttpServletRequest request,
                                 HttpServletResponse response, Model modelo) {
        UsuarioSesion yo = paginas.usuarioActual();
        try {
            servicio.asignarDocente(yo.actor(), id, uuid(docente));
            return "redirect:/grupos/" + id + "?aviso=docente";
        } catch (ServicioUsuarios.DatosInvalidos e) {
            response.setStatus(422);
            return pintarGrupo(request, modelo, id, Optional.empty(), Optional.of(e.getMessage()));
        }
    }

    private String pintarGrupos(HttpServletRequest request, Model modelo, Optional<String> error, String nombreEscrito) {
        UsuarioSesion yo = paginas.usuarioActual();
        RepositorioAula.Actor actor = yo.actor();
        List<Usuario> docentes = yo.esAdministrador() ? servicio.docentes(actor) : List.of();
        Map<UUID, String> nombres = new HashMap<>(docentes.stream().collect(Collectors.toMap(Usuario::id, Usuario::nombre)));
        nombres.put(yo.id(), yo.nombre());
        List<FilaGrupo> filas = servicio.grupos(actor).stream()
                .map(g -> new FilaGrupo(g, g.docenteId().map(d -> nombres.getOrDefault(d, "otra cuenta")).orElse("sin docente"),
                        servicio.miembros(actor, g.id()).size()))
                .toList();
        modelo.addAttribute("pagina", paginas.crear(yo.esAdministrador() ? "Grupos" : "Mis grupos", request));
        modelo.addAttribute("v", new VistaGrupos(filas, docentes, yo.esAdministrador(), error, nombreEscrito));
        return "grupos";
    }

    private String pintarGrupo(HttpServletRequest request, Model modelo, UUID id, Optional<String> aviso, Optional<String> error) {
        UsuarioSesion yo = paginas.usuarioActual();
        RepositorioAula.Actor actor = yo.actor();
        Grupo g = servicio.grupo(actor, id);
        String docente = g.docenteId().map(d -> servicio.nombreDe(actor, d).orElse("otra cuenta")).orElse("sin docente");
        modelo.addAttribute("pagina", paginas.crear(g.nombre(), request));
        modelo.addAttribute("v", new VistaGrupo(g, docente, servicio.miembros(actor, id), yo.esAdministrador() ? servicio.docentes(actor) : List.of(),
                yo.esAdministrador(), aviso, error));
        return "grupo";
    }

    private static Optional<UUID> uuid(String valor) {
        try {
            return valor == null || valor.isBlank() ? Optional.empty() : Optional.of(UUID.fromString(valor));
        } catch (IllegalArgumentException e) {
            throw new ServicioUsuarios.DatosInvalidos("Elige un docente de la lista.");
        }
    }

    private static String textoDeAviso(String motivo) {
        return switch (motivo) {
            case "creado" -> "Listo: el grupo quedó creado.";
            case "docente" -> "Listo: el grupo tiene su docente.";
            case "elige" -> "Marca al menos un estudiante activo del grupo para darle un código nuevo.";
            default -> "";
        };
    }
}
