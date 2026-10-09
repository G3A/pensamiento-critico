package pensamiento.web.biblioteca;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import pensamiento.flujos.BuscadorDePasajes;
import pensamiento.nucleo.Documento;
import pensamiento.nucleo.puertos.Biblioteca;
import pensamiento.nucleo.puertos.ImportadorDocumentos;
import pensamiento.nucleo.puertos.RegistroAuditoria;
import pensamiento.nucleo.puertos.Reloj;
import pensamiento.web.ObjetoNoEncontrado;
import pensamiento.web.Pagina;
import pensamiento.web.seguridad.UsuarioSesion;

/**
 * P13 · Biblioteca: importar (formulario multipart con el progreso de htmx), ver el estado de indexado (la lista se refresca
 * sola mientras algo se procesa), buscar, compartir con la institución, descargar el original y borrar. Un documento privado
 * de otra persona da 404, igual que uno que no existe (RF-03).
 */
@Controller
public class ControladorBiblioteca {

    private final Biblioteca biblioteca;
    private final ImportadorDocumentos importador;
    private final BuscadorDePasajes buscador;
    private final RegistroAuditoria auditoria;
    private final Reloj reloj;
    private final Pagina.Fabrica paginas;

    public ControladorBiblioteca(Biblioteca biblioteca, ImportadorDocumentos importador, BuscadorDePasajes buscador, RegistroAuditoria auditoria,
                                 Reloj reloj, Pagina.Fabrica paginas) {
        this.biblioteca = biblioteca;
        this.importador = importador;
        this.buscador = buscador;
        this.auditoria = auditoria;
        this.reloj = reloj;
        this.paginas = paginas;
    }

    @GetMapping("/biblioteca")
    @Transactional(readOnly = true)
    public String biblioteca(HttpServletRequest request, Model modelo) {
        UsuarioSesion yo = paginas.usuarioActual();
        modelo.addAttribute("pagina", paginas.crear("Biblioteca", request));
        modelo.addAttribute("v", new VistaBiblioteca(yo.id(), biblioteca.visibles(yo.id())));
        return "biblioteca";
    }

    @GetMapping("/biblioteca/lista")
    @Transactional(readOnly = true)
    public String lista(Model modelo) {
        UsuarioSesion yo = paginas.usuarioActual();
        modelo.addAttribute("v", new VistaBiblioteca(yo.id(), biblioteca.visibles(yo.id())));
        modelo.addAttribute("mensaje", "");
        modelo.addAttribute("error", false);
        return "fragmentos/biblioteca/lista";
    }

    @PostMapping("/biblioteca")
    @Transactional
    public String importar(@RequestParam("archivo") MultipartFile archivo, HttpServletResponse respuesta, Model modelo) throws IOException {
        UsuarioSesion yo = paginas.usuarioActual();
        ImportadorDocumentos.Importacion r = importador.importar(yo.id(), yo.institucionId(), archivo.getOriginalFilename(), archivo.getBytes());
        String mensaje;
        boolean error;
        switch (r) {
            case ImportadorDocumentos.Importado ok -> {
                mensaje = "Importado: " + ok.documento().nombre() + ". Se indexa en segundo plano; es privado hasta que lo compartas.";
                error = false;
            }
            case ImportadorDocumentos.Rechazado no -> {
                mensaje = no.motivo();
                error = true;
                respuesta.setStatus(422);
            }
        }
        modelo.addAttribute("v", new VistaBiblioteca(yo.id(), biblioteca.visibles(yo.id())));
        modelo.addAttribute("mensaje", mensaje);
        modelo.addAttribute("error", error);
        return "fragmentos/biblioteca/lista";
    }

    @GetMapping("/biblioteca/buscar")
    public String buscar(@RequestParam(name = "q", required = false) String consulta,
                         @RequestParam(name = "palabras", defaultValue = "false") boolean porPalabras,
                         @RequestParam(name = "afirmacion", required = false) UUID afirmacion, Model modelo) {
        UsuarioSesion yo = paginas.usuarioActual();
        modelo.addAttribute("b", buscador.buscar(yo.id(), consulta, porPalabras));
        modelo.addAttribute("afirmacion", afirmacion == null ? "" : afirmacion.toString());
        return "fragmentos/biblioteca/resultados";
    }

    @PostMapping("/biblioteca/{id}/compartir")
    @Transactional
    public String compartir(@PathVariable UUID id, @RequestParam(name = "compartido", defaultValue = "false") boolean compartido, Model modelo) {
        UsuarioSesion yo = paginas.usuarioActual();
        if (!biblioteca.compartir(yo.id(), id, compartido)) {
            throw new ObjetoNoEncontrado("documento");
        }
        if (compartido) {
            auditoria.registrar(new RegistroAuditoria.Evento(Optional.of(yo.id()), yo.institucionId(), RegistroAuditoria.Accion.COMPARTIR, "documento",
                    Optional.of(id), reloj.ahora()));
        }
        modelo.addAttribute("v", new VistaBiblioteca(yo.id(), biblioteca.visibles(yo.id())));
        modelo.addAttribute("mensaje", compartido ? "Compartido con tu institución: quedó en la auditoría." : "Vuelve a ser privado.");
        modelo.addAttribute("error", false);
        return "fragmentos/biblioteca/lista";
    }

    @PostMapping("/biblioteca/{id}/borrar")
    @Transactional
    public String borrar(@PathVariable UUID id, Model modelo) {
        UsuarioSesion yo = paginas.usuarioActual();
        String nombre = biblioteca.porId(yo.id(), id).filter(d -> d.esDe(yo.id())).map(Documento::nombre)
                .orElseThrow(() -> new ObjetoNoEncontrado("documento"));
        biblioteca.borrar(yo.id(), id);
        auditoria.registrar(new RegistroAuditoria.Evento(Optional.of(yo.id()), yo.institucionId(), RegistroAuditoria.Accion.BORRAR, "documento",
                Optional.of(id), reloj.ahora()));
        modelo.addAttribute("v", new VistaBiblioteca(yo.id(), biblioteca.visibles(yo.id())));
        modelo.addAttribute("mensaje", "Borrado: " + nombre + ". Las fuentes que lo citaban dicen «documento retirado».");
        modelo.addAttribute("error", false);
        return "fragmentos/biblioteca/lista";
    }

    /** El archivo original, siempre como descarga: nunca se abre dentro de la app. */
    @GetMapping("/biblioteca/{id}/original")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> original(@PathVariable UUID id) {
        UsuarioSesion yo = paginas.usuarioActual();
        Documento d = biblioteca.porId(yo.id(), id).orElseThrow(() -> new ObjetoNoEncontrado("documento"));
        byte[] contenido = biblioteca.contenido(yo.id(), id).orElseThrow(() -> new ObjetoNoEncontrado("documento"));
        MediaType tipo = switch (d.tipo()) {
            case PDF -> MediaType.APPLICATION_PDF;
            case MARKDOWN -> new MediaType("text", "markdown", StandardCharsets.UTF_8);
            case TEXTO -> new MediaType("text", "plain", StandardCharsets.UTF_8);
            case CSV -> new MediaType("text", "csv", StandardCharsets.UTF_8);
        };
        return ResponseEntity.ok()
                .contentType(tipo)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(d.nombre(), StandardCharsets.UTF_8).build().toString())
                .header("Cache-Control", "no-store")
                .body(contenido);
    }
}
