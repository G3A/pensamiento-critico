package pensamiento.web.patrones;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import gg.jte.Content;
import gg.jte.TemplateEngine;
import org.springframework.stereotype.Component;

import pensamiento.tecnicas.f4.ResultadoSift;

/** V02 para T19 · SIFT: los pasos activos con su hallazgo, la señal y el recordatorio de minutos por paso. */
@Component
public class RenderizadorSift implements RenderizadorResultado<ResultadoSift> {

    private final TemplateEngine plantillas;

    public RenderizadorSift(TemplateEngine plantillas) {
        this.plantillas = plantillas;
    }

    @Override
    public String patron() {
        return V02.PATRON;
    }

    @Override
    public Class<ResultadoSift> tipo() {
        return ResultadoSift.class;
    }

    @Override
    public Content render(Optional<UUID> idEjecucion, String sufijo, ResultadoSift r, Modo modo) {
        List<V02.Item> items = r.pasos().stream().map(p -> new V02.Item(p.paso().toString(), p.nombre(), p.hecho() ? "hecho" : "pendiente",
                p.hecho() ? "chip chip-ok" : "chip chip-pendiente", p.hecho() ? "respondido" : "sin_responder",
                p.hecho() ? p.hallazgo() : null, "", null)).toList();
        boolean alerta = !"sin alertas".equals(r.senal()) && !"incompleta".equals(r.senal());
        List<V02.Aviso> avisos = List.of(
                new V02.Aviso("Señal: " + r.senal() + ". " + r.motivo(), alerta ? "alerta" : "nota"),
                new V02.Aviso("Hasta " + r.minutos() + (r.minutos() == 1 ? " minuto" : " minutos") + " por paso.", "nota"));
        String tarjeta = "SIFT dice si conviene compartirlo, no si es cierto. Fuente: " + r.fuente() + ".";
        V02 v = new V02(idEjecucion, sufijo, modo, "SIFT antes de compartir", "Afirmación: «" + r.afirmacion() + "»",
                List.of(new V02.Medidor("hechos", "Pasos hechos " + r.hechos() + " de " + r.activos(), r.hechos(), r.activos())),
                List.of(new V02.Seccion(null, items)), avisos, List.of(), r.resumen(), tarjeta);
        return salida -> plantillas.render("tag/v/v02.jte", Map.of("v", v), salida);
    }
}
