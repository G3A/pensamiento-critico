package pensamiento.web.patrones;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import gg.jte.Content;
import gg.jte.TemplateEngine;
import org.springframework.stereotype.Component;

import pensamiento.tecnicas.f5.ResultadoAch;

/** V03a, matriz de consistencia: pinta tag/v/v03a.jte con el record tipado V03a. */
@Component
public class RenderizadorV03a implements RenderizadorResultado<ResultadoAch> {

    private final TemplateEngine plantillas;

    public RenderizadorV03a(TemplateEngine plantillas) {
        this.plantillas = plantillas;
    }

    @Override
    public String patron() {
        return V03a.PATRON;
    }

    @Override
    public Class<ResultadoAch> tipo() {
        return ResultadoAch.class;
    }

    @Override
    public Content render(Optional<UUID> idEjecucion, String sufijo, ResultadoAch valor, Modo modo) {
        V03a v = new V03a(idEjecucion, sufijo, valor, modo);
        return salida -> plantillas.render("tag/v/v03a.jte", Map.of("v", v), salida);
    }
}
