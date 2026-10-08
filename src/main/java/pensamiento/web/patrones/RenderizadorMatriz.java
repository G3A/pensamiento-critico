package pensamiento.web.patrones;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import gg.jte.Content;
import gg.jte.TemplateEngine;
import org.springframework.stereotype.Component;

import pensamiento.tecnicas.f5.ResultadoMatriz;

/**
 * V03b para T31 · Matriz de decisión ponderada y T33 · Inferencia a la mejor explicación, que comparten el resultado: la
 * matriz en el orden del ranking, el ganador o el empate, la justificación (T33) y una frase de sensibilidad por criterio.
 */
@Component
public class RenderizadorMatriz implements RenderizadorResultado<ResultadoMatriz> {

    private final TemplateEngine plantillas;

    public RenderizadorMatriz(TemplateEngine plantillas) {
        this.plantillas = plantillas;
    }

    @Override
    public String patron() {
        return V03b.PATRON;
    }

    @Override
    public Class<ResultadoMatriz> tipo() {
        return ResultadoMatriz.class;
    }

    @Override
    public Content render(Optional<UUID> idEjecucion, String sufijo, ResultadoMatriz r, Modo modo) {
        boolean explicacion = r.justificacion() != null || r.titulo().startsWith("Inferencia");
        List<V03b.Columna> columnas = r.criterios().stream().map(c -> new V03b.Columna(c.criterio(), c.peso())).toList();
        List<V03b.Fila> filas = new ArrayList<>();
        for (ResultadoMatriz.FilaOpcion f : r.filas()) {
            filas.add(new V03b.Fila(f.puesto() + "º", f.opcion(), f.puntajes(), f.probabilidad() == null ? null : f.probabilidad() + "%", f.valor(),
                    f.empate()));
        }
        String tarjeta = explicacion ? "La mejor explicación no es una explicación probada: compruébala con lo que predice."
                : "La matriz ordena según los pesos que elegiste: si dudas de un peso, mira la sensibilidad antes de decidir.";
        V03b v = new V03b(idEjecucion, sufijo, modo, r.titulo(), r.pregunta(), explicacion ? "Explicación" : "Opción", columnas, filas,
                r.probabilidades() == null ? null : "Probabilidad", r.probabilidades() == null ? "Total" : "Total esperado", r.ganador(),
                r.nivelSensibilidad(), r.sensibilidad(), r.justificacion(), r.avisos(), r.resumen(), tarjeta);
        return salida -> plantillas.render("tag/v/v03b.jte", Map.of("v", v), salida);
    }
}
