package pensamiento.web.patrones;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import gg.jte.Content;
import gg.jte.TemplateEngine;
import org.springframework.stereotype.Component;

import pensamiento.tecnicas.f3.ResultadoFalacias;

/**
 * V05, texto propio marcado: pinta tag/v/v05.jte con el record tipado V05. La fila del hito 2 nombra solo V01 y
 * V02, pero el catálogo y la sección 7b asignan V05 a T13 · Falacias como esquemas fallidos.
 */
@Component
public class RenderizadorV05 implements RenderizadorResultado<ResultadoFalacias> {

    private final TemplateEngine plantillas;

    public RenderizadorV05(TemplateEngine plantillas) {
        this.plantillas = plantillas;
    }

    @Override
    public String patron() {
        return V05.PATRON;
    }

    @Override
    public Class<ResultadoFalacias> tipo() {
        return ResultadoFalacias.class;
    }

    @Override
    public Content render(Optional<UUID> idEjecucion, String sufijo, ResultadoFalacias valor, Modo modo) {
        V05 v = new V05(idEjecucion, sufijo, valor, modo);
        return salida -> plantillas.render("tag/v/v05.jte", Map.of("v", v), salida);
    }
}
