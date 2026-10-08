package pensamiento.web.patrones;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import gg.jte.Content;
import gg.jte.TemplateEngine;
import org.springframework.stereotype.Component;

import pensamiento.tecnicas.f5.ResultadoValorEsperado;

/** V12 para T27 · Valor esperado: las opciones de mayor a menor, con barras desde el cero y su peor caso. */
@Component
public class RenderizadorValorEsperado implements RenderizadorResultado<ResultadoValorEsperado> {

    private final TemplateEngine plantillas;

    public RenderizadorValorEsperado(TemplateEngine plantillas) {
        this.plantillas = plantillas;
    }

    @Override
    public String patron() {
        return V12.PATRON;
    }

    @Override
    public Class<ResultadoValorEsperado> tipo() {
        return ResultadoValorEsperado.class;
    }

    @Override
    public Content render(Optional<UUID> idEjecucion, String sufijo, ResultadoValorEsperado r, Modo modo) {
        long maximo = r.ranking().stream().mapToLong(f -> Math.abs(f.centesimas())).max().orElse(0);
        List<V12.Fila> filas = r.ranking().stream().map(f -> V12.fila(f.puesto(), f.opcion(), f.valor(),
                "peor caso: " + (f.peorCaso() == null ? "sin escenarios" : f.peorCaso() + " " + r.unidad()), f.centesimas(), maximo,
                f.empate() ? "empate" : null)).toList();
        String nota = "Valor esperado = Σ probabilidad × impacto por escenario, en " + r.unidad() + (r.aversion() ? "; las pérdidas cuentan el doble." : ".");
        V12 v = new V12(idEjecucion, sufijo, modo, "Valor esperado", r.pregunta(), nota, filas, r.avisos(), r.resumen(),
                "El valor esperado es lo que rendiría en promedio si pudieras repetir la decisión muchas veces. Una sola vez puede tocarte el peor caso.");
        return salida -> plantillas.render("tag/v/v12.jte", Map.of("v", v), salida);
    }
}
