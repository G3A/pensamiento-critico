package pensamiento.web.patrones;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import gg.jte.Content;
import gg.jte.TemplateEngine;
import org.springframework.stereotype.Component;

import pensamiento.tecnicas.f3.ResultadoSesgos;

/** V13a para T14 · Sesgos cognitivos: los probables primero, con su señal, y los antídotos debajo. */
@Component
public class RenderizadorSesgos implements RenderizadorResultado<ResultadoSesgos> {

    private final TemplateEngine plantillas;

    public RenderizadorSesgos(TemplateEngine plantillas) {
        this.plantillas = plantillas;
    }

    @Override
    public String patron() {
        return V13a.PATRON;
    }

    @Override
    public Class<ResultadoSesgos> tipo() {
        return ResultadoSesgos.class;
    }

    @Override
    public Content render(Optional<UUID> idEjecucion, String sufijo, ResultadoSesgos r, Modo modo) {
        List<V13a.Item> items = r.sesgos().stream().map(s -> new V13a.Item(s.id(), s.nombre(), s.probable() ? "probable" : "sin señal",
                s.probable() ? "chip chip-aviso" : "chip", s.senal())).toList();
        String tarjeta = r.probables() == 0
                ? "Que no haya señal no prueba que no haya sesgos: solo que no los marcaste ni aparecen en el texto."
                : "Una señal no prueba el sesgo: dice dónde mirar. El antídoto es lo que puedes hacer antes de decidir.";
        V13a v = new V13a(idEjecucion, sufijo, modo, "Atajos que pueden estar empujando tu juicio",
                ("lectura".equals(r.contexto()) ? "Lo que leíste: " : "Tu decisión: ") + "«" + r.situacion() + "»",
                items, "Antídotos sugeridos", r.antidotos(), r.resumen(), tarjeta);
        return salida -> plantillas.render("tag/v/v13a.jte", Map.of("v", v), salida);
    }
}
