package pensamiento.web.patrones;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import gg.jte.Content;
import gg.jte.TemplateEngine;
import org.springframework.stereotype.Component;

import pensamiento.tecnicas.f5.ResultadoBayes;

/**
 * V08 para T24 · Razonamiento bayesiano: una barra por paso (el prior y el posterior después de cada evidencia), escalada
 * a 100, y la misma secuencia en la tabla; cada evidencia con su razón de verosimilitud.
 */
@Component
public class RenderizadorBayes implements RenderizadorResultado<ResultadoBayes> {

    private final TemplateEngine plantillas;

    public RenderizadorBayes(TemplateEngine plantillas) {
        this.plantillas = plantillas;
    }

    @Override
    public String patron() {
        return V08.PATRON;
    }

    @Override
    public Class<ResultadoBayes> tipo() {
        return ResultadoBayes.class;
    }

    @Override
    public Content render(Optional<UUID> idEjecucion, String sufijo, ResultadoBayes r, Modo modo) {
        List<V08.Fila> filas = new ArrayList<>();
        List<String> claves = new ArrayList<>();
        List<String> clases = new ArrayList<>();
        filas.add(new V08.Fila("Antes" + (r.oddsPrior() == null ? "" : " (" + r.oddsPrior() + ")"), r.prior()));
        claves.add("prior");
        clases.add("barra-prior");
        for (ResultadoBayes.Paso p : r.pasos()) {
            filas.add(new V08.Fila("Con " + p.codigo() + (p.odds() == null ? "" : " (" + p.odds() + ")"), p.posterior()));
            claves.add(p.codigo());
            clases.add("barra-posterior");
        }
        List<V08.Barra> barras = V08.escalar(filas, claves, clases, 100);
        List<V08.Item> items = r.pasos().stream().map(p -> new V08.Item(p.codigo(), p.codigo() + " · " + p.evidencia(), "razón " + p.razon(),
                "chip", "si es cierta " + p.siCierta() + "%, si es falsa " + p.siFalsa() + "% · después: " + p.posterior() + "%")).toList();
        String frase = "Antes " + r.prior() + "%; después de " + r.pasos().size() + (r.pasos().size() == 1 ? " evidencia, " : " evidencias, ")
                + r.pasos().getLast().posterior() + "%.";
        V08 v = new V08(idEjecucion, sufijo, modo, "Actualización bayesiana", "«" + r.afirmacion() + "»", "Confianza en cada paso, en porcentaje",
                barras, filas, frase, "Evidencias y su razón de verosimilitud", items, r.avisos(), r.resumen(),
                "El posterior es lo que deberías creer si las probabilidades que escribiste son razonables. Si cambias una, cambia el resultado.");
        return salida -> plantillas.render("tag/v/v08.jte", Map.of("v", v), salida);
    }
}
