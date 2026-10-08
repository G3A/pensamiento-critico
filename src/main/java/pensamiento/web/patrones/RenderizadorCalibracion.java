package pensamiento.web.patrones;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import gg.jte.Content;
import gg.jte.TemplateEngine;
import org.springframework.stereotype.Component;

import pensamiento.tecnicas.f5.ResultadoCalibracion;

/**
 * V08 para T25 · Calibración y puntaje Brier y para el tablero del Diario: la curva de calibración como SVG de servidor
 * (declarada contra cumplida, con la diagonal de la calibración perfecta) y la tabla con los mismos números. Un tramo con
 * menos resueltas que el umbral se dibuja hueco y dice "provisional" con texto (corrección 13).
 */
@Component
public class RenderizadorCalibracion implements RenderizadorResultado<ResultadoCalibracion> {

    private final TemplateEngine plantillas;

    public RenderizadorCalibracion(TemplateEngine plantillas) {
        this.plantillas = plantillas;
    }

    @Override
    public String patron() {
        return V08.PATRON;
    }

    @Override
    public Class<ResultadoCalibracion> tipo() {
        return ResultadoCalibracion.class;
    }

    @Override
    public Content render(Optional<UUID> idEjecucion, String sufijo, ResultadoCalibracion r, Modo modo) {
        V08 v = vista(idEjecucion, sufijo, r, modo, "Calibración");
        return salida -> plantillas.render("tag/v/v08.jte", Map.of("v", v), salida);
    }

    /** También la usa el tablero del Diario, con su propio título. */
    public static V08 vista(Optional<UUID> idEjecucion, String sufijo, ResultadoCalibracion r, Modo modo, String titulo) {
        List<V08.Punto> puntos = r.tramos().stream().map(t -> new V08.Punto(t.confianzaMedia(), t.porcentajeCumplido(), t.n(), t.provisional())).toList();
        List<V08.FilaCurva> tabla = r.tramos().stream().map(t -> new V08.FilaCurva(t.desde() + " a " + t.hasta() + "%", t.n(), t.confianzaMedia(),
                t.porcentajeCumplido(), t.provisional())).toList();
        String nombre = "brier".equals(r.tipoPuntaje()) ? "Brier" : "Puntaje logarítmico";
        String frase = r.puntaje() == null ? "Todavía no hay predicciones resueltas." : nombre + " " + r.puntaje() + " con " + r.resueltas()
                + (r.resueltas() == 1 ? " resuelta" : " resueltas") + (r.fueraDeHorizonte() > 0 ? " (" + r.fueraDeHorizonte() + " fuera del horizonte)" : "")
                + ". Sobre la diagonal estarías bien calibrado.";
        String cero = "brier".equals(r.tipoPuntaje()) ? "0,25" : "0,69";
        String tarjeta = "0 es perfecto; decir siempre 50% da " + cero + ". Con menos de " + r.umbral()
                + " resueltas en un tramo, la curva es provisional.";
        List<String> avisos = r.avisos();
        if (puntos.isEmpty()) {
            // Sin tramos no hay curva que rotular: la frase pasa a ser un aviso.
            avisos = new java.util.ArrayList<>(avisos);
            avisos.addFirst(frase);
            frase = null;
        }
        return new V08(idEjecucion, sufijo, modo, titulo, null, "Curva de calibración: confianza declarada contra lo que se cumplió", List.of(),
                List.of(), frase, null, List.of(), avisos, r.resumen(), tarjeta, puntos, tabla);
    }
}
