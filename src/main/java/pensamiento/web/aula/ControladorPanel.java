package pensamiento.web.aula;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import io.github.wimdeblauwe.htmx.spring.boot.mvc.HtmxRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import pensamiento.flujos.DojoDeRazonamiento;
import pensamiento.nucleo.BancoDojo;
import pensamiento.nucleo.PanelGrupo;
import pensamiento.tecnicas.f8.TemaDojo;
import pensamiento.web.Pagina;
import pensamiento.web.seguridad.UsuarioSesion;

/**
 * Panel del grupo (hito 10, entregable 7, RF-22): solo agregados, nunca un texto de un estudiante. Por decisión del dueño
 * (2026-10-14) los retos se rotulan de las dos formas: "aciertos por concepto" para identificar, analizar y evaluar, y los de
 * "crear" (rúbrica) en una tabla aparte rotulada "práctica, no nota". Se actualiza solo cada 30 segundos con htmx.
 */
@Controller
public class ControladorPanel {

    /** Una fila de aciertos: el concepto con su nombre del banco, intentos, aciertos y personas. */
    public record FilaConcepto(String nombre, int intentos, int aciertos, int personas) {
        public int porcentaje() {
            return intentos == 0 ? 0 : Math.round(100f * aciertos / intentos);
        }
    }

    /** Lo que pinta el panel. */
    public record VistaPanel(ServicioAula.Panel panel, String tema, List<FilaConcepto> conceptos, List<FilaConcepto> crear,
                             List<PanelGrupo.Nivel> bloom) {
        public PanelGrupo a() {
            return panel.agregados();
        }
    }

    private static final Map<String, Integer> ORDEN_BLOOM = Map.of("sin empezar", 0, "identificar", 1, "analizar", 2, "evaluar", 3, "crear", 4);

    private final ServicioAula servicio;
    private final Pagina.Fabrica paginas;
    private final BancoDojo banco;

    public ControladorPanel(ServicioAula servicio, Pagina.Fabrica paginas, DojoDeRazonamiento dojo) {
        this.servicio = servicio;
        this.paginas = paginas;
        this.banco = dojo.banco();
    }

    @GetMapping("/grupos/{id}/panel")
    public String panel(@PathVariable UUID id, HtmxRequest htmx, HttpServletRequest request, Model modelo) {
        UsuarioSesion yo = paginas.usuarioActual();
        ServicioAula.Panel p = servicio.panel(yo.actor(), id);
        String tema = p.grupo().tema().map(t -> TemaDojo.de(t).cita()).orElse("");
        List<FilaConcepto> conceptos = p.agregados().conceptos().stream().filter(c -> !c.crear()).map(this::fila).toList();
        List<FilaConcepto> crear = p.agregados().conceptos().stream().filter(PanelGrupo.Concepto::crear).map(this::fila).toList();
        List<PanelGrupo.Nivel> bloom = p.agregados().bloom().stream()
                .sorted(java.util.Comparator.comparingInt(n -> ORDEN_BLOOM.getOrDefault(n.nivel(), 9))).toList();
        modelo.addAttribute("pagina", paginas.crear("Panel · " + p.grupo().nombre(), request));
        modelo.addAttribute("v", new VistaPanel(p, tema, conceptos, crear, bloom));
        return htmx.isHtmxRequest() ? "fragmentos/panel" : "panel";
    }

    private FilaConcepto fila(PanelGrupo.Concepto c) {
        String nombre = banco.concepto(c.concepto()).map(BancoDojo.Concepto::nombre).orElse(c.concepto());
        return new FilaConcepto(nombre, c.intentos(), c.aciertos(), c.personas());
    }

    public static String dias(Optional<Integer> dias) {
        return dias.map(d -> d == 1 ? "hace 1 día" : "hace " + d + " días").orElse("nunca practicó");
    }
}
