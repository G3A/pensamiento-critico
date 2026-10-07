package pensamiento.web;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.github.wimdeblauwe.htmx.spring.boot.mvc.HtmxRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import pensamiento.expediente.ServicioExpedientes;
import pensamiento.nucleo.Expediente;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Tecnica;
import pensamiento.nucleo.puertos.Reloj;
import pensamiento.nucleo.puertos.RepositorioExpediente;
import pensamiento.nucleo.puertos.RepositorioTecnica;
import pensamiento.web.expedientes.MuestraExpediente;
import pensamiento.web.expedientes.VistaExpediente;
import pensamiento.web.seguridad.UsuarioSesion;
import pensamiento.web.tecnicas.MotorTecnicas;
import pensamiento.web.patrones.Renderizadores;

/**
 * Expediente (P09, RF-07): lista, creación, vista con línea de tiempo, resumen por familia y "qué falta para
 * cerrar", y borrado lógico que desasocia sus ejecuciones. El expediente de muestra es contenido del catálogo
 * pintado en modo lectura, no filas de usuario.
 */
@Controller
public class ControladorExpedientes {

    private final ServicioExpedientes servicio;
    private final RepositorioExpediente expedientes;
    private final RepositorioTecnica tecnicas;
    private final MotorTecnicas motor;
    private final Renderizadores renderizadores;
    private final MuestraExpediente muestra;
    private final Reloj reloj;
    private final Pagina.Fabrica paginas;

    public ControladorExpedientes(ServicioExpedientes servicio, RepositorioExpediente expedientes, RepositorioTecnica tecnicas,
                                  MotorTecnicas motor, Renderizadores renderizadores, MuestraExpediente muestra, Reloj reloj,
                                  Pagina.Fabrica paginas) {
        this.servicio = servicio;
        this.expedientes = expedientes;
        this.tecnicas = tecnicas;
        this.motor = motor;
        this.renderizadores = renderizadores;
        this.muestra = muestra;
        this.reloj = reloj;
        this.paginas = paginas;
    }

    @GetMapping("/expedientes")
    @Transactional(readOnly = true)
    public String lista(HttpServletRequest request, Model modelo) {
        UsuarioSesion yo = paginas.usuarioActual();
        modelo.addAttribute("pagina", paginas.crear("Expedientes", request));
        modelo.addAttribute("expedientes", expedientes.deUsuario(yo.id()));
        modelo.addAttribute("error", "");
        return "expedientes";
    }

    /** Crear: con htmx o formulario simple vuelve 303 a la vista; la API de pruebas recibe 201 con Location. */
    @PostMapping("/expedientes")
    @Transactional
    public Object crear(@RequestParam(required = false) String nombre, HtmxRequest htmx, HttpServletRequest request,
                        HttpServletResponse respuesta, Model modelo) {
        UsuarioSesion yo = paginas.usuarioActual();
        try {
            Expediente creado = servicio.crear(yo.id(), yo.institucionId(), nombre);
            respuesta.setHeader("HX-Trigger", "expediente-cambiado");
            if (htmx.isHtmxRequest()) {
                respuesta.setHeader("HX-Redirect", "/expedientes/" + creado.id());
                return ResponseEntity.noContent().build();
            }
            return ResponseEntity.created(URI.create("/expedientes/" + creado.id())).build();
        } catch (ServicioExpedientes.NombreInvalido e) {
            respuesta.setStatus(422);
            modelo.addAttribute("pagina", paginas.crear("Expedientes", request));
            modelo.addAttribute("expedientes", expedientes.deUsuario(yo.id()));
            modelo.addAttribute("error", e.getMessage());
            return "expedientes";
        }
    }

    @GetMapping("/expedientes/muestra")
    @Transactional(readOnly = true)
    public String muestra(HttpServletRequest request, Model modelo) {
        VistaExpediente vista = muestra.vista();
        modelo.addAttribute("pagina", paginas.crear(vista.expediente().nombre(), request));
        modelo.addAttribute("v", vista);
        return "expediente";
    }

    @GetMapping("/expedientes/{id}")
    @Transactional(readOnly = true)
    public String abrir(@PathVariable UUID id, HttpServletRequest request, Model modelo) {
        UsuarioSesion yo = paginas.usuarioActual();
        ServicioExpedientes.Vista vista = servicio.vista(yo.id(), id).orElseThrow(() -> new ObjetoNoEncontrado("expediente"));
        Map<IdTecnica, Tecnica> porId = tecnicas.todas().stream().collect(Collectors.toMap(Tecnica::id, Function.identity()));
        modelo.addAttribute("pagina", paginas.crear(vista.expediente().nombre(), request));
        modelo.addAttribute("v", VistaExpediente.de(vista, porId, false, e -> renderizadores.render(porId.get(e.tecnica()),
                Optional.of(e.id()), "", motor.valorDe(e), pensamiento.web.patrones.Modo.LECTURA), reloj.zona()));
        return "expediente";
    }

    @PostMapping("/expedientes/{id}/borrar")
    @Transactional
    public ResponseEntity<Void> borrar(@PathVariable UUID id, HtmxRequest htmx, HttpServletResponse respuesta) {
        UsuarioSesion yo = paginas.usuarioActual();
        if (!servicio.borrar(yo.id(), yo.institucionId(), id)) {
            throw new ObjetoNoEncontrado("expediente");
        }
        if (htmx.isHtmxRequest()) {
            // El navegador seguiría un 303 por su cuenta y htmx pintaría la lista dentro del formulario.
            respuesta.setHeader("HX-Trigger", "expediente-cambiado");
            respuesta.setHeader("HX-Redirect", "/expedientes");
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.status(303).location(URI.create("/expedientes")).build();
    }
}
