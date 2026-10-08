package pensamiento.web.patrones;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import gg.jte.Content;
import gg.jte.TemplateEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import pensamiento.tecnicas.comun.Textos;
import pensamiento.tecnicas.f5.ResultadoDiario;

/**
 * V11 para T32 · Diario de decisiones: el registro de la decisión con su predicción y la línea de tiempo de su revisión.
 * Si la predicción ya se revisó, suma el hito "resuelta" y el resultado con texto.
 */
@Component
public class RenderizadorDiario implements RenderizadorResultado<ResultadoDiario> {

    private final TemplateEngine plantillas;
    private final EstadoDePrediccion estado;

    @Autowired
    public RenderizadorDiario(TemplateEngine plantillas, EstadoDePrediccion estado) {
        this.plantillas = plantillas;
        this.estado = estado;
    }

    /** Sin estado vigente: pinta el registro como se guardó (pruebas de plantilla y ejemplos). */
    public RenderizadorDiario(TemplateEngine plantillas) {
        this(plantillas, EstadoDePrediccion.NINGUNO);
    }

    @Override
    public String patron() {
        return V11.PATRON;
    }

    @Override
    public Class<ResultadoDiario> tipo() {
        return ResultadoDiario.class;
    }

    @Override
    public Content render(Optional<UUID> idEjecucion, String sufijo, ResultadoDiario r, Modo modo) {
        return salida -> {
            Optional<EstadoDePrediccion.Revision> revision = idEjecucion.isPresent() && r.prediccionId() != null ? estado.de(r.prediccionId()) : Optional.empty();
            V11 v = vista(idEjecucion, sufijo, r, modo, revision);
            plantillas.render("tag/v/v11.jte", Map.of("v", v), salida);
        };
    }

    public static V11 vista(Optional<UUID> idEjecucion, String sufijo, ResultadoDiario r, Modo modo, Optional<EstadoDePrediccion.Revision> revision) {
        List<V11.Campo> campos = List.of(new V11.Campo("Decisión", r.decision()), new V11.Campo("Contexto", r.contexto()),
                new V11.Campo("Alternativas", r.alternativas()), new V11.Campo("Predicción", r.prediccion() + " · " + r.confianza() + "%"),
                new V11.Campo("Me haría cambiar de opinión", r.cambiarOpinion()),
                new V11.Campo("Revisar", r.fechaRevisionTexto() + " · aparece en Inicio y en el Diario desde ese día"));
        List<V11.Hito> linea = new ArrayList<>(r.linea().stream().map(h -> new V11.Hito(h.fecha(), h.texto(), h.que())).toList());
        String estado = r.estado();
        String clase = "chip chip-pendiente";
        if (revision.isPresent()) {
            String resultado = revision.get().seCumplio() ? "se cumplió" : "no se cumplió";
            linea.add(new V11.Hito(revision.get().fecha().toString(), Textos.fecha(revision.get().fecha()), "resuelta: " + resultado));
            estado = "resuelta: " + resultado;
            clase = revision.get().seCumplio() ? "chip chip-ok" : "chip chip-aviso";
        }
        return new V11(idEjecucion, sufijo, modo, "Diario de decisiones", campos, estado, clase, linea, r.bloqueo(), r.avisos(), r.resumen(),
                "La confianza la declaras tú: al revisar, el resultado queda fijo y alimenta tu calibración.");
    }
}
