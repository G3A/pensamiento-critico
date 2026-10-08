package pensamiento.web.patrones;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import gg.jte.Content;
import gg.jte.TemplateEngine;
import org.springframework.stereotype.Component;

import pensamiento.tecnicas.f3.ResultadoTasasBase;

/**
 * V08 para T18 · Correlación, causalidad y tasas base: las barras comparan los positivos verdaderos con los falsos,
 * que es lo que la gente no ve al leer "acierta el 99%"; la tabla trae todos los números.
 */
@Component
public class RenderizadorTasasBase implements RenderizadorResultado<ResultadoTasasBase> {

    private final TemplateEngine plantillas;

    public RenderizadorTasasBase(TemplateEngine plantillas) {
        this.plantillas = plantillas;
    }

    @Override
    public String patron() {
        return V08.PATRON;
    }

    @Override
    public Class<ResultadoTasasBase> tipo() {
        return ResultadoTasasBase.class;
    }

    @Override
    public Content render(Optional<UUID> idEjecucion, String sufijo, ResultadoTasasBase r, Modo modo) {
        List<V08.Barra> barras = List.of();
        List<V08.Fila> tabla = List.of();
        String titulo = "";
        ResultadoTasasBase.Calculo c = r.calculo();
        if (c != null) {
            List<V08.Fila> positivos = List.of(new V08.Fila("Positivos verdaderos", c.detectados()), new V08.Fila("Positivos falsos", c.positivosFalsos()));
            barras = V08.escalar(positivos, List.of("detectados", "falsos"), List.of("barra-verdaderos", "barra-falsos"));
            tabla = List.of(new V08.Fila("Personas", c.deCada()), new V08.Fila("Con la condición", c.enfermos()),
                    new V08.Fila("Detectadas por el examen", c.detectados()), new V08.Fila("No detectadas", c.noDetectados()),
                    new V08.Fila("Sin la condición", c.sanos()), new V08.Fila("Positivos falsos", c.positivosFalsos()),
                    new V08.Fila("Negativos sin la condición", c.negativosSanos()), new V08.Fila("Positivos en total", c.positivos()));
            titulo = "De " + c.deCada() + " personas, los " + c.positivos() + " positivos";
        }
        List<V08.Item> items = r.criterios().stream().map(k -> new V08.Item(k.criterio(), k.nombre(), k.cumplido() ? "cumplido" : "sin cumplir",
                k.cumplido() ? "chip chip-ok" : "chip", k.llano())).toList();
        String tarjeta = r.cumplidos() == r.criterios().size() && !r.criterios().isEmpty()
                ? "Cumple los " + r.cumplidos() + " criterios activos: es un caso fuerte de causa, no una prueba."
                : "Que dos cosas pasen juntas no muestra que una cause la otra: los criterios dicen qué falta mirar.";
        V08 v = new V08(idEjecucion, sufijo, modo, "Correlación, causa y tasa base", "«" + r.afirmacion() + "»", titulo, barras, tabla,
                r.fraseCalculo(), "Criterios de Hill (" + r.cumplidos() + " de " + r.criterios().size() + ")", items, r.avisos(), r.resumen(), tarjeta);
        return salida -> plantillas.render("tag/v/v08.jte", Map.of("v", v), salida);
    }
}
