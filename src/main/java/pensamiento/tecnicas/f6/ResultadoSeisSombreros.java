package pensamiento.tecnicas.f6;

import java.util.List;

/**
 * Valor que pinta el patrón V03c (rejilla de celdas de texto) para T35 · Seis Sombreros: una celda por sombrero activo en
 * el orden de la configuración, la síntesis y las notas de tiempo y modalidad.
 *
 * @param sintesis la síntesis; nula si falta
 * @param notas    "Tiempo sugerido: …" y la de la modalidad
 */
public record ResultadoSeisSombreros(String tema, List<Celda> celdas, int conNotas, String sintesis, List<String> notas, List<String> avisos,
                                     String resumen) {

    /** @param estado nulo si está bien, "sin notas" o "¿hecho u opinión?" */
    public record Celda(String id, String nombre, String mira, List<String> lineas, String estado) {
        public Celda {
            lineas = List.copyOf(lineas);
        }
    }

    public ResultadoSeisSombreros {
        celdas = List.copyOf(celdas);
        notas = List.copyOf(notas);
        avisos = List.copyOf(avisos);
    }
}
