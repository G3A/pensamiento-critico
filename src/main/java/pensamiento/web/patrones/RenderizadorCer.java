package pensamiento.web.patrones;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import gg.jte.Content;
import gg.jte.TemplateEngine;
import org.springframework.stereotype.Component;

import pensamiento.tecnicas.f1.ResultadoCer;

/** V10 para T03 · Afirmación, evidencia, razonamiento (CER): la barra de completitud y cada pieza con su estado. */
@Component
public class RenderizadorCer implements RenderizadorResultado<ResultadoCer> {

    private final TemplateEngine plantillas;

    public RenderizadorCer(TemplateEngine plantillas) {
        this.plantillas = plantillas;
    }

    @Override
    public String patron() {
        return V10.PATRON;
    }

    @Override
    public Class<ResultadoCer> tipo() {
        return ResultadoCer.class;
    }

    @Override
    public Content render(Optional<UUID> idEjecucion, String sufijo, ResultadoCer r, Modo modo) {
        List<V10.Item> items = r.piezas().stream().map(p -> new V10.Item(p.pieza().toString(), p.pieza().nombre(), p.estado().texto(),
                switch (p.estado()) {
                    case COMPLETA -> "chip chip-ok";
                    case CORTA -> "chip chip-pendiente";
                    case FALTA -> "chip chip-aviso";
                }, p.texto(), p.falta())).toList();
        String tarjeta = r.completas() == r.total()
                ? "Están las " + (r.total() == 5 ? "cinco" : "tres") + " piezas. Que estén no las hace ciertas: la evidencia todavía se puede verificar."
                : "Cada pieza que falta trae la pregunta que la completaría.";
        V10 v = new V10(idEjecucion, sufijo, modo, "Afirmo, muestro, explico", null,
                List.of(new V10.Barra("completitud", "Completitud " + r.completas() + " de " + r.total(), 0, r.total(), r.completas())),
                "Las piezas", items, null, List.of(), List.of(), r.resumen(), tarjeta);
        return salida -> plantillas.render("tag/v/v10.jte", Map.of("v", v), salida);
    }
}
