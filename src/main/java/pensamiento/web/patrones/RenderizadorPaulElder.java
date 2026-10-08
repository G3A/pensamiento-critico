package pensamiento.web.patrones;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import gg.jte.Content;
import gg.jte.TemplateEngine;
import org.springframework.stereotype.Component;

import pensamiento.tecnicas.f1.ResultadoPaulElder;

/** V02 para T04 · Elementos y estándares de Paul-Elder: elementos llenos o vacíos y estándares puntuados, sin nota global. */
@Component
public class RenderizadorPaulElder implements RenderizadorResultado<ResultadoPaulElder> {

    private final TemplateEngine plantillas;

    public RenderizadorPaulElder(TemplateEngine plantillas) {
        this.plantillas = plantillas;
    }

    @Override
    public String patron() {
        return V02.PATRON;
    }

    @Override
    public Class<ResultadoPaulElder> tipo() {
        return ResultadoPaulElder.class;
    }

    @Override
    public Content render(Optional<UUID> idEjecucion, String sufijo, ResultadoPaulElder r, Modo modo) {
        List<V02.Item> elementos = r.elementos().stream().map(e -> new V02.Item(e.elemento(), e.nombre(),
                e.lleno() ? (e.delModelo() ? "lleno · del modelo, adoptado" : "lleno") : "vacío", e.lleno() ? "chip chip-ok" : "chip chip-pendiente",
                e.lleno() ? "completa" : "falta", e.texto(), "", e.falta())).toList();
        List<V02.Item> estandares = r.estandares().stream().map(s -> new V02.Item(s.estandar(), s.nombre(), s.estado(),
                switch (s.estado()) {
                    case "puntuado" -> "chip chip-ok";
                    case "bajo" -> "chip chip-aviso";
                    default -> "chip";
                }, s.estado().replace(' ', '-'), s.puntaje() == null ? null : String.valueOf(s.puntaje()), "", s.pregunta())).toList();
        String suficiente = r.suficiente() ? "Suficiente: " + r.llenos() + " de " + r.elementos().size() + " elementos, tu umbral es " + r.umbral() + "."
                : "Todavía no es suficiente: " + r.llenos() + " de " + r.elementos().size() + " elementos, tu umbral es " + r.umbral() + ".";
        V02 v = new V02(idEjecucion, sufijo, modo, "Las piezas del razonamiento", "Tema: " + r.tema(),
                List.of(new V02.Medidor("elementos", "Elementos " + r.llenos() + " de " + r.elementos().size(), r.llenos(), r.elementos().size()),
                        new V02.Medidor("estandares", "Estándares puntuados " + r.puntuados() + " de " + r.estandares().size(), r.puntuados(),
                                Math.max(1, r.estandares().size()))),
                List.of(new V02.Seccion("Elementos", elementos), new V02.Seccion("Estándares", estandares)),
                List.of(new V02.Aviso(suficiente, "nota")), r.propuestas().stream().filter(p -> !p.adoptada()).toList(), r.resumen(),
                "Están los elementos que activaste o falta alguno. Que estén no los hace ciertos: revisa los supuestos.");
        return salida -> plantillas.render("tag/v/v02.jte", Map.of("v", v), salida);
    }
}
