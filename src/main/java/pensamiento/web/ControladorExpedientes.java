package pensamiento.web;

import java.net.URI;
import java.util.Optional;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import pensamiento.nucleo.Expediente;
import pensamiento.nucleo.puertos.RegistroAuditoria;
import pensamiento.nucleo.puertos.Reloj;
import pensamiento.nucleo.puertos.RepositorioExpediente;
import pensamiento.web.seguridad.UsuarioSesion;

/**
 * Expediente mínimo del hito 0: crear y abrir por identificador. Sirve para probar RF-03: el perfil B
 * recibe 404 al pedir un expediente del perfil A. La vista completa (P09) llega en el hito 1.
 */
@Controller
public class ControladorExpedientes {

    private final RepositorioExpediente expedientes;
    private final RegistroAuditoria auditoria;
    private final Reloj reloj;
    private final Pagina.Fabrica paginas;

    public ControladorExpedientes(RepositorioExpediente expedientes, RegistroAuditoria auditoria, Reloj reloj, Pagina.Fabrica paginas) {
        this.expedientes = expedientes;
        this.auditoria = auditoria;
        this.reloj = reloj;
        this.paginas = paginas;
    }

    @PostMapping("/expedientes")
    @Transactional
    public ResponseEntity<Void> crear(@RequestParam String nombre) {
        UsuarioSesion yo = paginas.usuarioActual();
        String limpio = nombre == null ? "" : nombre.trim();
        if (limpio.isEmpty() || limpio.length() > 120) {
            return ResponseEntity.unprocessableContent().header("X-Motivo", "El nombre del expediente es obligatorio").build();
        }
        Expediente creado = expedientes.guardar(new Expediente(UUID.randomUUID(), yo.id(), yo.institucionId(), limpio,
                Optional.empty(), Expediente.Estado.ABIERTO, reloj.ahora()));
        auditoria.registrar(new RegistroAuditoria.Evento(Optional.of(yo.id()), yo.institucionId(),
                RegistroAuditoria.Accion.CREAR, "expediente", Optional.of(creado.id()), reloj.ahora()));
        return ResponseEntity.created(URI.create("/expedientes/" + creado.id())).build();
    }

    @GetMapping("/expedientes/{id}")
    @Transactional(readOnly = true)
    public String abrir(@PathVariable UUID id, HttpServletRequest request, Model modelo) {
        UsuarioSesion yo = paginas.usuarioActual();
        Expediente expediente = expedientes.porId(yo.id(), id).orElseThrow(() -> new ObjetoNoEncontrado("expediente"));
        modelo.addAttribute("pagina", paginas.crear(expediente.nombre(), request));
        modelo.addAttribute("expediente", expediente);
        return "expediente";
    }
}
