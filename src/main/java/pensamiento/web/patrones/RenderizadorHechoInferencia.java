package pensamiento.web.patrones;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import gg.jte.Content;
import gg.jte.TemplateEngine;
import org.springframework.stereotype.Component;

import pensamiento.tecnicas.f3.ResultadoHechoInferencia;

/** V05 para T17 · Hecho, inferencia, juicio: cada oración marcada con su grupo y, debajo, qué se puede hacer con ella. */
@Component
public class RenderizadorHechoInferencia implements RenderizadorResultado<ResultadoHechoInferencia> {

    private final TemplateEngine plantillas;

    public RenderizadorHechoInferencia(TemplateEngine plantillas) {
        this.plantillas = plantillas;
    }

    @Override
    public String patron() {
        return V05.PATRON;
    }

    @Override
    public Class<ResultadoHechoInferencia> tipo() {
        return ResultadoHechoInferencia.class;
    }

    @Override
    public Content render(Optional<UUID> idEjecucion, String sufijo, ResultadoHechoInferencia r, Modo modo) {
        List<V05.Marca> marcas = r.oraciones().stream().map(o -> new V05.Marca("O" + o.numero(), o.inicio(), o.fin(),
                "marca-texto grupo-" + (o.grupo() == null ? "sin-etiquetar" : o.grupo()))).toList();
        List<V05.Nota> notas = r.oraciones().stream().map(o -> new V05.Nota("O" + o.numero(), "oracion-tipo",
                o.grupo() == null ? "sin etiquetar" : o.grupo() + (o.delModelo() ? " · propuesta del modelo adoptada" : ""),
                o.grupo() == null ? "chip chip-pendiente" : "chip", o.texto(), List.of(new V05.Linea("linea-tipo", null, o.linea())))).toList();
        V05 v = new V05(idEjecucion, sufijo, modo, "Qué puedes verificar y qué no", r.texto(), marcas, "lista-oraciones", notas,
                r.propuestas().stream().filter(p -> !p.adoptada()).toList(), r.resumen(),
                "Un hecho puede verificarse; una inferencia necesita evidencia; un juicio no se verifica, se discute con razones.");
        return salida -> plantillas.render("tag/v/v05.jte", Map.of("v", v), salida);
    }
}
