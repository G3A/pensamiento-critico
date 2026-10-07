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

import pensamiento.catalogo.Intencion;
import pensamiento.nucleo.Ejecucion;
import pensamiento.nucleo.Expediente;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.PendienteGuardado;
import pensamiento.nucleo.Tecnica;
import pensamiento.nucleo.puertos.Reloj;
import pensamiento.nucleo.puertos.RepositorioEjecucion;
import pensamiento.nucleo.puertos.RepositorioExpediente;
import pensamiento.nucleo.puertos.RepositorioTecnica;
import pensamiento.web.expedientes.MuestraExpediente;
import pensamiento.web.seguridad.UsuarioSesion;
import pensamiento.web.tecnicas.Fechas;

/**
 * P02 · Inicio y P22 · primer uso (RF-08). Vacío: las seis intenciones, el expediente de muestra en modo lectura
 * y "Empieza con un ejemplo", que abre T28 · Análisis de hipótesis en competencia (ACH) con el ejemplo de la
 * panadería cargado. Con datos: pendientes, recientes y expedientes.
 */
@Controller
public class ControladorInicio {

    static final IdTecnica PRIMER_EJEMPLO_TECNICA = IdTecnica.de("T28");
    static final String PRIMER_EJEMPLO_TITULO = "Las ventas de los sábados";

    /** Una ejecución reciente con su técnica y su fecha legible. */
    public record Reciente(Ejecucion ejecucion, Tecnica tecnica, String fecha) {
    }

    /** Lo que pinta el Inicio. */
    public record VistaInicio(Intencion[] intenciones, boolean vacio, String primerEjemplo, String nombreMuestra,
                              List<PendienteGuardado> pendientes, List<Reciente> recientes, List<Expediente> expedientes) {
    }

    private final RepositorioEjecucion ejecuciones;
    private final RepositorioExpediente expedientes;
    private final RepositorioTecnica tecnicas;
    private final Reloj reloj;
    private final Pagina.Fabrica paginas;

    public ControladorInicio(RepositorioEjecucion ejecuciones, RepositorioExpediente expedientes, RepositorioTecnica tecnicas, Reloj reloj,
                             Pagina.Fabrica paginas) {
        this.ejecuciones = ejecuciones;
        this.expedientes = expedientes;
        this.tecnicas = tecnicas;
        this.reloj = reloj;
        this.paginas = paginas;
    }

    @GetMapping("/")
    @Transactional(readOnly = true)
    public String inicio(HttpServletRequest request, Model modelo) {
        UsuarioSesion yo = paginas.usuarioActual();
        Map<IdTecnica, Tecnica> porId = tecnicas.todas().stream().collect(Collectors.toMap(Tecnica::id, Function.identity()));
        List<Reciente> recientes = ejecuciones.recientes(yo.id(), 5).stream()
                .map(e -> new Reciente(e, porId.get(e.tecnica()), Fechas.corta(e.creadaEn(), reloj.zona()))).toList();
        List<Expediente> suyos = expedientes.deUsuario(yo.id());
        String primerEjemplo = tecnicas.ejemplos(PRIMER_EJEMPLO_TECNICA).stream().filter(e -> e.titulo().equals(PRIMER_EJEMPLO_TITULO))
                .findFirst().map(e -> "/tecnicas/" + PRIMER_EJEMPLO_TECNICA + "?ejemplo=" + e.id()).orElse("/tecnicas/" + PRIMER_EJEMPLO_TECNICA);
        modelo.addAttribute("pagina", paginas.crear("Inicio", request));
        modelo.addAttribute("v", new VistaInicio(Intencion.values(), recientes.isEmpty() && suyos.isEmpty(), primerEjemplo,
                MuestraExpediente.NOMBRE, ejecuciones.pendientes(yo.id()), recientes, suyos));
        return "inicio";
    }
}
