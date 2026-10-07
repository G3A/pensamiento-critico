package pensamiento.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@ControllerAdvice
public class ManejadorErrores {

    private final Pagina.Fabrica paginas;

    public ManejadorErrores(Pagina.Fabrica paginas) {
        this.paginas = paginas;
    }

    @ExceptionHandler({ObjetoNoEncontrado.class, NoResourceFoundException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String noEncontrado(HttpServletRequest request, Model modelo) {
        modelo.addAttribute("pagina", paginas.crear("No encontrado", request));
        modelo.addAttribute("mensaje", "No existe lo que buscas, o no es tuyo.");
        return "no-encontrado";
    }
}
