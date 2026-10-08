package pensamiento.web.patrones;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import gg.jte.Content;
import gg.jte.TemplateEngine;
import org.springframework.stereotype.Component;

import pensamiento.tecnicas.f6.ResultadoSteelman;

/**
 * V04 para T34 · Steelmanning: a la izquierda la postura contraria como la escribió la persona y su cita; a la derecha
 * el steelman vigente y las razones que agrega. Nunca dice "correcto": lista las preguntas sin responder.
 */
@Component
public class RenderizadorSteelman implements RenderizadorResultado<ResultadoSteelman> {

    private final TemplateEngine plantillas;

    public RenderizadorSteelman(TemplateEngine plantillas) {
        this.plantillas = plantillas;
    }

    @Override
    public String patron() {
        return V04.PATRON;
    }

    @Override
    public Class<ResultadoSteelman> tipo() {
        return ResultadoSteelman.class;
    }

    @Override
    public Content render(Optional<UUID> idEjecucion, String sufijo, ResultadoSteelman r, Modo modo) {
        V04 v = new V04(idEjecucion, sufijo, modo, "Postura contraria y su mejor versión", null, izquierda(r), derecha(r),
                new V04.Estado("Estado", r.estado().texto(), r.estado() == ResultadoSteelman.Estado.POR_CONFIRMAR ? "chip chip-pendiente" : "chip chip-aviso",
                        r.estado() == ResultadoSteelman.Estado.POR_CONFIRMAR ? "Falta lo único que la app no puede responder." : null),
                "Preguntas sin responder", r.preguntas(), r.propuestas(), r.resumen(), tarjeta(r));
        return salida -> plantillas.render("tag/v/v04.jte", Map.of("v", v), salida);
    }

    private static V04.Columna izquierda(ResultadoSteelman r) {
        List<V04.Item> items = new ArrayList<>();
        items.add(new V04.Item("«" + r.posturaOriginal() + "»", "como la escribiste", "chip", null));
        if (r.cita() != null) {
            items.add(new V04.Item("«" + r.cita() + "»", "cita de quien la sostiene", "chip", null));
        }
        return new V04.Columna("Postura contraria", items, "");
    }

    private static V04.Columna derecha(ResultadoSteelman r) {
        List<V04.Item> items = new ArrayList<>();
        if (r.tieneSteelman()) {
            items.add(new V04.Item(r.steelman(), r.delModelo() ? "del modelo · adoptado por ti" : "escrito por ti",
                    r.delModelo() ? "chip chip-ok" : "chip", r.palabras() + " de " + r.maximo() + " palabras como máximo"));
        }
        r.razones().forEach(razon -> items.add(new V04.Item(razon, "razón añadida", "chip", null)));
        return new V04.Columna("Steelman", items, "Todavía no hay steelman.");
    }

    private static String tarjeta(ResultadoSteelman r) {
        if (!r.tieneSteelman()) {
            return "Escribe la mejor razón que tendría quien piensa así, o pide una propuesta al modelo y adóptala si la reconoces.";
        }
        return "Juzga tú si representa a quien se opone: la app no puede saber si la firmaría.";
    }
}
