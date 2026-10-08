package pensamiento.tecnicas.f3;

import java.util.List;

/**
 * Valor que pinta el patrón V08 (gráfico de frecuencias) para T18 · Correlación, causalidad y tasas base: el cálculo
 * con frecuencias naturales, si lo hubo, los criterios de Hill cumplidos y los avisos. Nunca dice "causa probada".
 *
 * @param calculo      nulo si no hay tasa base con sensibilidad y especificidad
 * @param fraseCalculo "De cada 11 positivos, 1 tiene la condición: 9%, no 99%." o "Probabilidad real si da positivo: 9%."
 * @param faltaTasaBase la afirmación es de riesgo, la configuración exige tasa base y no la tiene
 */
public record ResultadoTasasBase(String afirmacion, Calculo calculo, String fraseCalculo, boolean faltaTasaBase, List<CriterioEvaluado> criterios,
                                 int cumplidos, List<String> avisos, String formato, String resumen) {

    /** Frecuencias naturales sobre "de cada" personas, redondeadas a entero la mitad hacia arriba en cada paso. */
    public record Calculo(int deCada, int enfermos, int sanos, int detectados, int noDetectados, int positivosFalsos, int negativosSanos,
                          int positivos, int probabilidad, int sensibilidad) {
    }

    public record CriterioEvaluado(String criterio, String nombre, String llano, boolean cumplido) {
    }

    public ResultadoTasasBase {
        criterios = List.copyOf(criterios);
        avisos = List.copyOf(avisos);
    }
}
