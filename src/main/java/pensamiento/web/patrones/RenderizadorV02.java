package pensamiento.web.patrones;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import gg.jte.Content;
import gg.jte.TemplateEngine;
import org.springframework.stereotype.Component;

import pensamiento.tecnicas.f1.ResultadoToulmin;

/** V02 para T02 · Modelo de Toulmin: cada parte con su estado y, si no está completa, la pregunta que la completaría. */
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
        List<V02.Item> items = valor.partes().stream().map(p -> new V02.Item(p.parte().toString(), p.parte().nombre(), p.estado().texto(),
                claseEstado(p), p.estado().toString(), p.texto(), complemento(p), p.falta())).toList();
        String tarjeta = valor.completas() == valor.total()
                ? "Están todas las partes. Que estén no las hace ciertas: los datos y el respaldo todavía se pueden verificar."
                : "Cada parte que falta trae la pregunta que la completaría.";
        V02 v = new V02(idEjecucion, sufijo, modo, "Las partes del argumento", null,
                List.of(new V02.Medidor("completitud", "Completitud " + valor.completas() + " de " + valor.total(), valor.completas(), valor.total())),
                List.of(new V02.Seccion(null, items)), List.of(), List.of(), valor.resumen(), tarjeta);
        return salida -> plantillas.render("tag/v/v02.jte", Map.of("v", v), salida);
    }

    private static String claseEstado(ResultadoToulmin.ParteEvaluada p) {
        return switch (p.estado()) {
            case COMPLETA -> "chip chip-ok";
            case FALTA -> "chip chip-aviso";
            case SIN_FUENTE, SIN_RESPONDER -> "chip chip-pendiente";
        };
    }

    /** "Fuente: …" o "Respuesta: …", según la parte. */
    private static String complemento(ResultadoToulmin.ParteEvaluada p) {
        if (p.complemento() == null) {
            return "";
        }
        return (p.parte() == ResultadoToulmin.Parte.RESPALDO ? "Fuente: " : "Respuesta: ") + p.complemento();
    }
}
