package pensamiento.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import pensamiento.expediente.ServicioRespaldo;
import pensamiento.nucleo.puertos.Reloj;
import pensamiento.web.seguridad.UsuarioSesion;

/**
 * P21 · Mis datos (RF-12): exportar los datos de la persona como JSON con identificadores uuidv7 e importarlos de
 * vuelta. Importar es idempotente y rechaza identificadores de otra persona; ambas acciones quedan en auditoría.
 */
@Controller
public class ControladorMisDatos {

    /** Tope del archivo a importar: muy por encima de los datos de una persona. */
    static final long TOPE_BYTES = 5L * 1024 * 1024;

    private final ServicioRespaldo respaldo;
    private final Reloj reloj;
    private final Pagina.Fabrica paginas;

    public ControladorMisDatos(ServicioRespaldo respaldo, Reloj reloj, Pagina.Fabrica paginas) {
        this.respaldo = respaldo;
        this.reloj = reloj;
        this.paginas = paginas;
    }

    @GetMapping("/mis-datos")
    public String pagina(HttpServletRequest request, Model modelo) {
        modelo.addAttribute("pagina", paginas.crear("Mis datos", request));
        modelo.addAttribute("mensaje", "");
        modelo.addAttribute("error", "");
        return "mis-datos";
    }

    @GetMapping("/mis-datos/exportar")
    @Transactional
    public ResponseEntity<byte[]> exportar() {
        UsuarioSesion yo = paginas.usuarioActual();
        String json = respaldo.exportarComoTexto(yo.id(), yo.institucionId(), yo.nombre());
        String archivo = "mis-datos-" + LocalDate.ofInstant(reloj.ahora(), reloj.zona()) + ".json";
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(archivo).build().toString())
                .body(json.getBytes(StandardCharsets.UTF_8));
    }

    @PostMapping("/mis-datos/importar")
    @Transactional(rollbackFor = Exception.class)
    public String importar(@RequestParam("archivo") MultipartFile archivo, HttpServletRequest request, HttpServletResponse respuesta,
                           Model modelo) throws IOException {
        UsuarioSesion yo = paginas.usuarioActual();
        modelo.addAttribute("pagina", paginas.crear("Mis datos", request));
        modelo.addAttribute("mensaje", "");
        if (archivo.isEmpty() || archivo.getSize() > TOPE_BYTES) {
            respuesta.setStatus(422);
            modelo.addAttribute("error", archivo.isEmpty() ? "Elige un archivo .json exportado de esta aplicación." : "El archivo pasa de 5 MB.");
            return "mis-datos";
        }
        try {
            ServicioRespaldo.Importacion r = respaldo.importarTexto(yo.id(), yo.institucionId(), new String(archivo.getBytes(), StandardCharsets.UTF_8));
            modelo.addAttribute("error", "");
            modelo.addAttribute("mensaje", "Importación lista: " + r.ejecucionesNuevas() + " ejecuciones nuevas, " + r.ejecucionesYaEstaban()
                    + " ya estaban; " + r.expedientesNuevos() + " expedientes nuevos, " + r.expedientesActualizados() + " actualizados; "
                    + r.configuraciones() + " configuraciones.");
            return "mis-datos";
        } catch (ServicioRespaldo.IdentificadorAjeno | ServicioRespaldo.ArchivoInvalido e) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            respuesta.setStatus(422);
            modelo.addAttribute("error", e.getMessage());
            return "mis-datos";
        } catch (IllegalArgumentException e) {
            // Un valor desconocido a mitad del archivo: nada de lo ya escrito se conserva.
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            respuesta.setStatus(422);
            modelo.addAttribute("error", "El archivo tiene datos que esta aplicación no reconoce. No se importó nada.");
            return "mis-datos";
        }
    }
}
