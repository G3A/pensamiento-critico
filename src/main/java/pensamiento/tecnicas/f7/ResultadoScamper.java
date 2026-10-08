package pensamiento.tecnicas.f7;

import java.util.List;

/**
 * Valor que pinta el patrón V03c (rejilla de celdas de texto) para T44 · SCAMPER y pensamiento lateral: una celda por
 * operador activo con sus ideas y las seleccionadas, que pasan a la matriz ponderada del Diario.
 *
 * @param seleccionadas "texto (letra del operador)", en el orden en que se escribieron
 */
public record ResultadoScamper(String problema, List<Celda> celdas, List<String> seleccionadas, int minutosPorOperador, List<String> avisos,
                               String resumen) {

    /** @param corta tiene menos ideas que el mínimo de la configuración */
    public record Celda(String operador, String letra, String nombre, List<IdeaEn> ideas, boolean corta) {
        public Celda {
            ideas = List.copyOf(ideas);
        }
    }

    public record IdeaEn(String texto, boolean seleccionada) {
    }

    public ResultadoScamper {
        celdas = List.copyOf(celdas);
        seleccionadas = List.copyOf(seleccionadas);
        avisos = List.copyOf(avisos);
    }
}
