package pensamiento.web.manual;

import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import pensamiento.web.ObjetoNoEncontrado;
import pensamiento.web.Pagina;

/**
 * El manual por familia: el índice, cada capítulo dentro de la app y el mismo capítulo como un archivo HTML autocontenido
 * que se descarga y se abre sin conexión (CSS adentro, diagramas como SVG, sin scripts).
 */
@Controller
public class ControladorManual {

    private final ManualPorFamilia manual;
    private final Pagina.Fabrica paginas;

    public ControladorManual(ManualPorFamilia manual, Pagina.Fabrica paginas) {
        this.manual = manual;
        this.paginas = paginas;
    }

    @GetMapping("/manual")
    public String indice(HttpServletRequest request, Model modelo) {
        modelo.addAttribute("pagina", paginas.crear("Manual", request));
        modelo.addAttribute("familias", manual.familias());
        return "manual";
    }

    @GetMapping("/manual/{familia}")
    public String capitulo(@PathVariable String familia, HttpServletRequest request, Model modelo) {
        modelo.addAttribute("pagina", paginas.crear("Manual", request));
        modelo.addAttribute("p", manual.pintado(familia).orElseThrow(() -> new ObjetoNoEncontrado("familia")));
        return "manual-capitulo";
    }

    @GetMapping("/manual/{familia}/descargar")
    public ResponseEntity<byte[]> descargar(@PathVariable String familia) {
        ManualPorFamilia.Pintado p = manual.pintado(familia).orElseThrow(() -> new ObjetoNoEncontrado("familia"));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("manual-" + p.familia().codigo() + ".html").build().toString())
                .contentType(new MediaType("text", "html", StandardCharsets.UTF_8))
                .body(p.descarga().getBytes(StandardCharsets.UTF_8));
    }
}
