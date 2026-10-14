package pensamiento.web;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import pensamiento.nucleo.Expediente;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Tecnica;
import pensamiento.nucleo.puertos.Reloj;
import pensamiento.nucleo.puertos.RepositorioEjecucion;
import pensamiento.nucleo.puertos.RepositorioExpediente;
import pensamiento.nucleo.puertos.RepositorioTecnica;
import pensamiento.web.seguridad.UsuarioSesion;
import pensamiento.web.tecnicas.Fechas;

/**
 * "Mis trabajos" (hito 10, menú de estudiante): lo que la persona guardó, sus ejecuciones más recientes y sus expedientes, y el
 * enlace a su progreso en el Dojo. Solo lo suyo: la RLS por usuario no deja ver lo de nadie más, tampoco a su docente.
 */
@Controller
public class ControladorMisTrabajos {

    static final int CUANTOS = 50;

    /** Una ejecución con el nombre de su técnica y su fecha. */
    public record Trabajo(String enlace, String tecnica, String fecha, String resumen) {
    }

    public record VistaMisTrabajos(List<Trabajo> trabajos, List<Expediente> expedientes) {
    }

    private final RepositorioEjecucion ejecuciones;
    private final RepositorioExpediente expedientes;
    private final RepositorioTecnica tecnicas;
    private final Reloj reloj;
    private final Pagina.Fabrica paginas;

    public ControladorMisTrabajos(RepositorioEjecucion ejecuciones, RepositorioExpediente expedientes, RepositorioTecnica tecnicas, Reloj reloj,
                                  Pagina.Fabrica paginas) {
        this.ejecuciones = ejecuciones;
        this.expedientes = expedientes;
        this.tecnicas = tecnicas;
        this.reloj = reloj;
        this.paginas = paginas;
    }

    @GetMapping("/mis-trabajos")
    @Transactional(readOnly = true)
    public String misTrabajos(HttpServletRequest request, Model modelo) {
        UsuarioSesion yo = paginas.usuarioActual();
        Map<IdTecnica, Tecnica> porId = tecnicas.todas().stream().collect(Collectors.toMap(Tecnica::id, Function.identity()));
        List<Trabajo> trabajos = ejecuciones.recientes(yo.id(), CUANTOS).stream()
                .map(e -> new Trabajo("/ejecuciones/" + e.id(), e.tecnica().valor() + " · " + porId.get(e.tecnica()).nombre(),
                        Fechas.corta(e.creadaEn(), reloj.zona()), e.resumen()))
                .toList();
        modelo.addAttribute("pagina", paginas.crear("Mis trabajos", request));
        modelo.addAttribute("v", new VistaMisTrabajos(trabajos, expedientes.deUsuario(yo.id())));
        return "mis-trabajos";
    }
}
