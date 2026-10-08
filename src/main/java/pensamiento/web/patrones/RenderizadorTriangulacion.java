package pensamiento.web.patrones;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import gg.jte.Content;
import gg.jte.TemplateEngine;
import org.springframework.stereotype.Component;

import pensamiento.tecnicas.f4.ResultadoTriangulacion;

/**
 * V10 para T22 · Triangulación: la barra de fuerza neta (R02), cada pasaje con su postura y si cuenta, y el estado de
 * R03 con su motivo. "Verificada" siempre dice "por ti, bajo R03".
 */
@Component
public class RenderizadorTriangulacion implements RenderizadorResultado<ResultadoTriangulacion> {

    private final TemplateEngine plantillas;

    public RenderizadorTriangulacion(TemplateEngine plantillas) {
        this.plantillas = plantillas;
    }

    @Override
    public String patron() {
        return V10.PATRON;
    }

    @Override
    public Class<ResultadoTriangulacion> tipo() {
        return ResultadoTriangulacion.class;
    }

    @Override
    public Content render(Optional<UUID> idEjecucion, String sufijo, ResultadoTriangulacion r, Modo modo) {
        List<V10.Item> items = r.evidencias().stream().map(e -> new V10.Item(e.codigo(), e.codigo() + " · " + e.titulo(),
                e.cuenta() ? e.postura().replace('_', ' ') + " · cuenta" + ("modelo".equals(e.etiquetadaPor()) ? " · etiquetada por el modelo, adoptada" : "")
                        : e.detalle(),
                e.cuenta() ? "chip chip-ok" : "chip chip-pendiente", "«" + e.pasaje() + "»",
                "Fuerza " + e.fuerza() + " (R01)" + (e.grupo() == null ? "" : " · grupo de origen: " + e.grupo()))).toList();
        String estado = r.estado().replace('_', ' ').replace("en verificacion", "en verificación").replace("no verificable", "no verificable");
        String clase = switch (r.estado()) {
            case "verificada" -> "chip chip-ok";
            case "refutada", "disputada" -> "chip chip-aviso";
            default -> "chip chip-pendiente";
        };
        String tarjeta = "verificada".equals(r.estado())
                ? "Verificada por ti, con " + r.grupos() + " grupos de fuentes, bajo R03. Si aparece evidencia en contra, vuelve a evaluar."
                : "La app no decide si es cierta: dice qué tanto la sostienen tus fuentes bajo R03.";
        int maximo = Math.max(8, Math.abs(r.neta()));
        V10 v = new V10(idEjecucion, sufijo, modo, "Triangulación de fuentes", "Afirmación: «" + r.afirmacion() + "»",
                List.of(new V10.Barra("neta", "Fuerza neta " + (r.neta() > 0 ? "+" : "") + r.neta() + " (" + r.magnitud() + ")", -maximo, maximo, r.neta())),
                "Pasajes y fuentes", items, new V10.Veredicto("Estado", estado, clase, r.motivo()), List.of(),
                r.propuestas().stream().filter(p -> !p.adoptada()).toList(), r.resumen(), tarjeta);
        return salida -> plantillas.render("tag/v/v10.jte", Map.of("v", v), salida);
    }
}
