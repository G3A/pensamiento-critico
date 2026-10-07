package pensamiento.web.tecnicas;

import java.util.Optional;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import pensamiento.expediente.ServicioExpedientes;
import pensamiento.nucleo.Ejecucion;
import pensamiento.nucleo.Expediente;
import pensamiento.nucleo.Tecnica;
import pensamiento.nucleo.puertos.Reloj;
import pensamiento.nucleo.puertos.RepositorioEjecucion;
import pensamiento.nucleo.puertos.RepositorioExpediente;
import pensamiento.nucleo.puertos.RepositorioTecnica;
import pensamiento.web.ObjetoNoEncontrado;
import pensamiento.web.Pagina;
import pensamiento.web.patrones.Modo;
import pensamiento.web.patrones.Renderizadores;
import pensamiento.web.seguridad.UsuarioSesion;

/** Una ejecución guardada: verla completa y asociarla a un expediente (P07, P09). */
@Controller
public class ControladorEjecuciones {

    private final RepositorioEjecucion ejecuciones;
    private final RepositorioExpediente expedientes;
    private final RepositorioTecnica tecnicas;
    private final ServicioExpedientes servicioExpedientes;
    private final MotorTecnicas motor;
    private final Renderizadores renderizadores;
    private final Reloj reloj;
    private final Pagina.Fabrica paginas;

    public ControladorEjecuciones(RepositorioEjecucion ejecuciones, RepositorioExpediente expedientes, RepositorioTecnica tecnicas,
                                  ServicioExpedientes servicioExpedientes, MotorTecnicas motor, Renderizadores renderizadores, Reloj reloj,
                                  Pagina.Fabrica paginas) {
        this.ejecuciones = ejecuciones;
        this.expedientes = expedientes;
        this.tecnicas = tecnicas;
        this.servicioExpedientes = servicioExpedientes;
        this.motor = motor;
        this.renderizadores = renderizadores;
        this.reloj = reloj;
        this.paginas = paginas;
    }

    @GetMapping("/ejecuciones/{id}")
    @Transactional(readOnly = true)
    public String ver(@PathVariable UUID id, HttpServletRequest request, Model modelo) {
        UsuarioSesion yo = paginas.usuarioActual();
        Ejecucion e = ejecuciones.porId(yo.id(), id).orElseThrow(() -> new ObjetoNoEncontrado("ejecución"));
        Tecnica t = tecnicas.porId(e.tecnica()).orElseThrow(() -> new ObjetoNoEncontrado("técnica"));
        modelo.addAttribute("pagina", paginas.crear(t.nombreLlano(), request));
        modelo.addAttribute("tecnica", t);
        modelo.addAttribute("ejecucion", e);
        modelo.addAttribute("fecha", Fechas.corta(e.creadaEn(), reloj.zona()));
        modelo.addAttribute("expediente", e.expedienteId().flatMap(x -> expedientes.porId(yo.id(), x)).orElse(null));
        modelo.addAttribute("expedientes", expedientes.deUsuario(yo.id()));
        modelo.addAttribute("resultado", renderizadores.render(t, Optional.of(e.id()), "", motor.valorDe(e), Modo.COMPLETO));
        return "ejecucion";
    }

    /** Asocia a un expediente existente, a uno nuevo, o desasocia. Devuelve el mismo formulario con el estado. */
    @PostMapping("/ejecuciones/{id}/expediente")
    @Transactional
    public String asociar(@PathVariable UUID id, @RequestParam(required = false) String expediente,
                          @RequestParam(required = false) String nuevo, HttpServletResponse respuesta, Model modelo) {
        UsuarioSesion yo = paginas.usuarioActual();
        Optional<UUID> expedienteId = Optional.ofNullable(expediente).filter(s -> !s.isBlank()).map(ControladorEjecuciones::uuid);
        Optional<Optional<Expediente>> resultado;
        try {
            resultado = servicioExpedientes.asociar(yo.id(), yo.institucionId(), id, expedienteId, Optional.ofNullable(nuevo));
        } catch (ServicioExpedientes.NombreInvalido e) {
            respuesta.setStatus(422);
            return formulario(yo, id, e.getMessage(), modelo);
        }
        if (resultado.isEmpty()) {
            throw new ObjetoNoEncontrado("ejecución");
        }
        respuesta.setHeader("HX-Trigger", "expediente-cambiado");
        String mensaje = resultado.get().map(x -> "Asociada al expediente \"" + x.nombre() + "\".").orElse("Sin expediente.");
        return formulario(yo, id, mensaje, modelo);
    }

    private String formulario(UsuarioSesion yo, UUID id, String mensaje, Model modelo) {
        Ejecucion e = ejecuciones.porId(yo.id(), id).orElseThrow(() -> new ObjetoNoEncontrado("ejecución"));
        modelo.addAttribute("ejecucion", e);
        modelo.addAttribute("expedientes", expedientes.deUsuario(yo.id()));
        modelo.addAttribute("mensaje", mensaje);
        return "fragmentos/asociar";
    }

    private static UUID uuid(String texto) {
        try {
            return UUID.fromString(texto);
        } catch (IllegalArgumentException ex) {
            throw new ObjetoNoEncontrado("expediente");
        }
    }
}
