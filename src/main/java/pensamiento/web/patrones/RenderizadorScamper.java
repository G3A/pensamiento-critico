package pensamiento.web.patrones;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import gg.jte.Content;
import gg.jte.TemplateEngine;
import org.springframework.stereotype.Component;

import pensamiento.tecnicas.f7.ResultadoScamper;

/** V03c para T44 · SCAMPER y pensamiento lateral: una celda por operador activo y las ideas que pasan a comparar. */
@Component
public class RenderizadorScamper implements RenderizadorResultado<ResultadoScamper> {

    private final TemplateEngine plantillas;

    public RenderizadorScamper(TemplateEngine plantillas) {
        this.plantillas = plantillas;
    }

    @Override
    public String patron() {
        return V03c.PATRON;
    }

    @Override
    public Class<ResultadoScamper> tipo() {
        return ResultadoScamper.class;
    }

    @Override
    public Content render(Optional<UUID> idEjecucion, String sufijo, ResultadoScamper r, Modo modo) {
        var celdas = r.celdas().stream().map(c -> new V03c.Celda(c.operador(), c.letra(), c.nombre(),
                c.ideas().stream().map(i -> new V03c.Texto(i.texto(), i.seleccionada())).toList(),
                c.corta() ? (c.ideas().isEmpty() ? "sin ideas" : "faltan ideas") : null)).toList();
        String nota = r.minutosPorOperador() > 0 ? "Tiempo sugerido: " + r.minutosPorOperador() + (r.minutosPorOperador() == 1 ? " minuto" : " minutos")
                + " por operador." : null;
        V03c v = new V03c(idEjecucion, sufijo, modo, "SCAMPER y pensamiento lateral", "Problema: " + r.problema(), celdas, nota,
                "Pasan a comparar", r.seleccionadas(), r.avisos(), r.resumen(),
                "Primero muchas ideas, después elegir: las seleccionadas pasan a la matriz ponderada para compararlas.");
        return salida -> plantillas.render("tag/v/v03c.jte", Map.of("v", v), salida);
    }
}
