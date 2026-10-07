package pensamiento.web;

import java.util.List;
import java.util.Optional;

import io.github.wimdeblauwe.htmx.spring.boot.mvc.HtmxRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import pensamiento.catalogo.Intencion;
import pensamiento.nucleo.Tecnica;
import pensamiento.nucleo.puertos.RepositorioTecnica;

/** P03 mínima: las 49 fichas "Qué es" como pendientes, filtrables por familia y por intención. */
@Controller
public class ControladorCatalogo {

    private final RepositorioTecnica tecnicas;
    private final Pagina.Fabrica paginas;

    public ControladorCatalogo(RepositorioTecnica tecnicas, Pagina.Fabrica paginas) {
        this.tecnicas = tecnicas;
        this.paginas = paginas;
    }

    @GetMapping("/catalogo")
    @Transactional(readOnly = true)
    public String catalogo(@RequestParam(required = false) String familia,
                           @RequestParam(required = false) String intencion,
                           HtmxRequest htmx, HttpServletRequest request, Model modelo) {
        Optional<Intencion> intencionElegida = Optional.ofNullable(intencion).filter(s -> !s.isBlank()).map(Intencion::porClave);
        List<Tecnica> lista = familia == null || familia.isBlank() ? tecnicas.todas() : tecnicas.porFamilia(familia);
        if (intencionElegida.isPresent()) {
            lista = lista.stream().filter(intencionElegida.get()::incluye).toList();
        }
        modelo.addAttribute("pagina", paginas.crear("Catálogo", request));
        modelo.addAttribute("familias", tecnicas.familias());
        modelo.addAttribute("tecnicas", lista);
        modelo.addAttribute("familiaActiva", familia == null ? "" : familia);
        modelo.addAttribute("intencionActiva", intencionElegida.map(Intencion::clave).orElse(""));
        modelo.addAttribute("total", tecnicas.contar());
        return htmx.isHtmxRequest() ? "fragmentos/rejilla-catalogo" : "catalogo";
    }
}
