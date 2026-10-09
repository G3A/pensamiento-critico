package pensamiento.web.patrones;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import gg.jte.Content;
import gg.jte.TemplateEngine;
import org.springframework.stereotype.Component;

import pensamiento.tecnicas.f2.ResultadoPreguntasSocraticas;

/**
 * V09 para T08 · Preguntas socráticas: la transcripción (la postura, cada pregunta con su tipo, su elemento y por qué la
 * eligió el motor, y cada respuesta), la pregunta que sigue o la de cierre y, al lado, los ocho elementos de Paul-Elder y
 * los estándares puntuados por reglas. Nunca un puntaje global.
 */
@Component
public class RenderizadorPreguntasSocraticas implements RenderizadorResultado<ResultadoPreguntasSocraticas> {

    private final TemplateEngine plantillas;

    public RenderizadorPreguntasSocraticas(TemplateEngine plantillas) {
        this.plantillas = plantillas;
    }

    @Override
    public String patron() {
        return V09.PATRON;
    }

    @Override
    public Class<ResultadoPreguntasSocraticas> tipo() {
        return ResultadoPreguntasSocraticas.class;
    }

    @Override
    public Content render(Optional<UUID> idEjecucion, String sufijo, ResultadoPreguntasSocraticas r, Modo modo) {
        return salida -> plantillas.render("tag/v/v09.jte", Map.of("v", vista(idEjecucion, sufijo, r, modo)), salida);
    }

    public static V09 vista(Optional<UUID> idEjecucion, String sufijo, ResultadoPreguntasSocraticas r, Modo modo) {
        List<V09.Burbuja> burbujas = new ArrayList<>();
        burbujas.add(new V09.Burbuja("postura", "persona", "tú · postura", r.postura(), List.of(), null));
        for (ResultadoPreguntasSocraticas.Turno t : r.turnos()) {
            burbujas.add(pregunta(t));
            burbujas.add(new V09.Burbuja("r" + t.numero(), "persona", "tú", t.respuesta(), List.of(), null));
        }
        V09.Burbuja siguiente;
        if (r.siguiente() != null) {
            siguiente = pregunta(r.siguiente());
            siguiente = new V09.Burbuja(siguiente.clave(), siguiente.rol(), siguiente.autor() + " · sigue", siguiente.texto(),
                    List.of("por responder"), siguiente.detalle());
        } else {
            siguiente = new V09.Burbuja("cierre", "consejero", "consejero · cierre", r.cierre(),
                    List.of(r.respuestaCierre() == null ? "por responder" : "respondida"), null);
        }
        if (r.respuestaCierre() != null) {
            burbujas.add(new V09.Burbuja("cierre", "consejero", "consejero · cierre", r.cierre(), List.of(), null));
            burbujas.add(new V09.Burbuja("r-cierre", "persona", "tú", r.respuestaCierre(), List.of(), null));
            siguiente = null;
        }
        List<V09.ItemPanel> elementos = r.elementos().stream().map(e -> new V09.ItemPanel(e.id(), e.nombre(), e.estado(),
                e.lleno() ? "chip chip-ok" : "pendiente".equals(e.estado()) ? "chip chip-pendiente" : "chip", e.texto(), null)).toList();
        List<V09.ItemPanel> estandares = r.estandares().stream().map(s -> new V09.ItemPanel(s.id(), s.nombre(), s.estado() + " · " + s.puntaje(),
                s.puntaje() == 10 ? "chip chip-ok" : s.puntaje() == 5 ? "chip chip-pendiente" : "chip chip-aviso", s.motivo(), s.pregunta())).toList();
        List<V09.SeccionPanel> panel = List.of(new V09.SeccionPanel("Elementos (" + r.llenos() + " de 8)", elementos),
                new V09.SeccionPanel("Estándares por reglas (0, 5 o 10)", estandares));
        List<String> avisos = r.cambio() == null ? List.of() : List.of(r.cambio());
        String tarjeta = "Las preguntas las elige el código y no dicen si tienes razón; los estándares miran qué elementos llenaste, no si lo que piensas es cierto.";
        return new V09(idEjecucion, sufijo, modo, "Preguntas socráticas · modo " + ("ensayo".equals(r.modoSesion()) ? "ensayo" : "decisión"), null,
                burbujas, siguiente, "Elementos del razonamiento (Paul-Elder)", panel, avisos, r.propuestas(), r.resumen(), tarjeta);
    }

    private static V09.Burbuja pregunta(ResultadoPreguntasSocraticas.Turno t) {
        List<String> chips = new ArrayList<>();
        chips.add("modelo".equals(t.origen()) ? "del modelo · adoptada" : "del banco");
        if (!"general".equals(t.rama())) {
            chips.add("rama: " + t.rama());
        }
        return new V09.Burbuja("p" + t.numero(), "consejero", "consejero · " + t.tipoNombre() + " · " + t.elementoNombre().toLowerCase(), t.pregunta(),
                chips, t.porque());
    }
}
