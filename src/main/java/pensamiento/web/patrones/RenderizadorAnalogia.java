package pensamiento.web.patrones;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import gg.jte.Content;
import gg.jte.TemplateEngine;
import org.springframework.stereotype.Component;

import pensamiento.tecnicas.f1.ResultadoAnalogia;

/** V04 para T07 · Razonamiento por analogía: similitudes frente a diferencias, con la clave marcada y la fuerza. */
@Component
public class RenderizadorAnalogia implements RenderizadorResultado<ResultadoAnalogia> {

    private final TemplateEngine plantillas;

    public RenderizadorAnalogia(TemplateEngine plantillas) {
        this.plantillas = plantillas;
    }

    @Override
    public String patron() {
        return V04.PATRON;
    }

    @Override
    public Class<ResultadoAnalogia> tipo() {
        return ResultadoAnalogia.class;
    }

    @Override
    public Content render(Optional<UUID> idEjecucion, String sufijo, ResultadoAnalogia r, Modo modo) {
        List<V04.Item> similitudes = r.similitudes().stream().map(s -> new V04.Item(s, null, null, null)).toList();
        List<V04.Item> diferencias = r.diferencias().stream().map(d -> new V04.Item(d.texto(),
                d.clave() ? (d.verificada() ? "clave · verificada" : "clave · sin verificar") : d.delModelo() ? "del modelo · adoptada por ti" : null,
                d.clave() ? "chip chip-aviso" : "chip chip-ok", null)).toList();
        String clase = switch (r.fuerza()) {
            case FUERTE -> "chip chip-ok";
            case MEDIA, INCOMPLETA -> "chip chip-pendiente";
            case DEBIL -> "chip chip-aviso";
        };
        V04 v = new V04(idEjecucion, sufijo, modo, "¿Se parecen en lo que importa?", "«" + r.caso() + "» → «" + r.conclusion() + "»",
                new V04.Columna("Similitudes", similitudes, "Todavía no hay similitudes."),
                new V04.Columna("Diferencias relevantes", diferencias, "Todavía no hay diferencias."),
                new V04.Estado("Fuerza", r.fuerza().texto(), clase, r.motivo()), "", List.of(), r.propuestas(), r.resumen(),
                "La fuerza sale de lo que escribiste: una diferencia que no anotaste puede cambiarla.");
        return salida -> plantillas.render("tag/v/v04.jte", Map.of("v", v), salida);
    }
}
