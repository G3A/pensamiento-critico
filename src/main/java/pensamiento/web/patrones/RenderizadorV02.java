package pensamiento.web.patrones;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import gg.jte.Content;
import gg.jte.TemplateEngine;
import org.springframework.stereotype.Component;

import pensamiento.tecnicas.f1.ResultadoToulmin;

/** V02, lista de verificación con estado: pinta tag/v/v02.jte con el record tipado V02. */
@Component
public class RenderizadorV02 implements RenderizadorResultado<ResultadoToulmin> {

    private final TemplateEngine plantillas;

    public RenderizadorV02(TemplateEngine plantillas) {
        this.plantillas = plantillas;
    }

    @Override
    public String patron() {
        return V02.PATRON;
    }

    @Override
    public Class<ResultadoToulmin> tipo() {
        return ResultadoToulmin.class;
    }

    @Override
    public Content render(Optional<UUID> idEjecucion, String sufijo, ResultadoToulmin valor, Modo modo) {
        V02 v = new V02(idEjecucion, sufijo, valor, modo);
        return salida -> plantillas.render("tag/v/v02.jte", Map.of("v", v), salida);
    }
}
