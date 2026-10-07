package pensamiento.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import pensamiento.catalogo.Intencion;

/** P02 en su estado vacío: las seis intenciones y el aviso de modo plantillas. */
@Controller
public class ControladorInicio {

    private final Pagina.Fabrica paginas;

    public ControladorInicio(Pagina.Fabrica paginas) {
        this.paginas = paginas;
    }

    @GetMapping("/")
    public String inicio(HttpServletRequest request, Model modelo) {
        modelo.addAttribute("pagina", paginas.crear("Inicio", request));
        modelo.addAttribute("intenciones", Intencion.values());
        return "inicio";
    }
}
