package pensamiento.web.patrones;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import gg.jte.Content;
import gg.jte.TemplateEngine;
import org.springframework.stereotype.Component;

import pensamiento.tecnicas.f3.ResultadoFalacias;

/**
 * V05 para T13 · Falacias como esquemas fallidos: cada marca dice esquema, pregunta crítica sin responder y porqué; la
 * etiqueta de falacia solo se afirma si la persona la confirmó (R06). Las marcas que vienen de una propuesta adoptada
 * lo dicen; las propuestas sin adoptar se listan aparte y no cuentan.
 */
@Component
public class RenderizadorV05 implements RenderizadorResultado<ResultadoFalacias> {

    private final TemplateEngine plantillas;

    public RenderizadorV05(TemplateEngine plantillas) {
        this.plantillas = plantillas;
    }

    @Override
    public String patron() {
        return V05.PATRON;
    }

    @Override
    public Class<ResultadoFalacias> tipo() {
        return ResultadoFalacias.class;
    }

    @Override
    public Content render(Optional<UUID> idEjecucion, String sufijo, ResultadoFalacias valor, Modo modo) {
        List<V05.Marca> marcas = valor.marcas().stream()
                .map(m -> new V05.Marca(m.codigo(), m.inicio(), m.fin(), m.confirmada() ? "marca-texto confirmada" : "marca-texto propuesta")).toList();
        List<V05.Nota> notas = valor.marcas().stream().map(m -> nota(m, valor.mostrarPregunta())).toList();
        V05 v = new V05(idEjecucion, sufijo, modo, "Tu texto, marcado", valor.texto(), marcas, "", notas,
                valor.delModelo().stream().filter(p -> !p.adoptada()).toList(), valor.resumen(), tarjeta(valor));
        return salida -> plantillas.render("tag/v/v05.jte", Map.of("v", v), salida);
    }

    private static V05.Nota nota(ResultadoFalacias.Marca m, boolean mostrarPregunta) {
        List<V05.Linea> lineas = new ArrayList<>();
        if (mostrarPregunta) {
            lineas.add(new V05.Linea("pregunta-critica", "Pregunta crítica sin responder:", m.preguntaTexto()));
        }
        lineas.add(new V05.Linea("porque", null, m.porque()));
        lineas.add(new V05.Linea("como-responder", "Para responderla:", m.comoResponder()));
        lineas.add(new V05.Linea("etiqueta-falacia", null, m.confirmada() ? "Falacia: " + m.falacia() + "."
                : "Si confirmas que la pregunta falla, la etiqueta sería: " + m.falacia() + "."));
        String estado = m.confirmada() ? "falacia confirmada por ti" : "esquema derrotable · sin confirmar";
        if (m.delModelo()) {
            estado += " · propuesta del modelo adoptada";
        }
        return new V05.Nota(m.codigo(), "marca-falacia marca-" + m.estado().toString(), estado,
                m.confirmada() ? "chip chip-aviso" : "chip chip-pendiente", m.esquemaNombre(), lineas);
    }

    private static String tarjeta(ResultadoFalacias valor) {
        if (valor.marcas().isEmpty()) {
            return "Las reglas no reconocieron ningún esquema con preguntas sin responder. Eso no prueba que el razonamiento sea bueno: "
                    + "las reglas solo ven patrones de palabras.";
        }
        return "Las marcas son propuestas: cada una dice qué pregunta crítica falta responder. La etiqueta de falacia "
                + "solo cuenta si tú la confirmas.";
    }
}
