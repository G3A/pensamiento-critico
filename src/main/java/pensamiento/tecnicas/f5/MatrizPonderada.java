package pensamiento.tecnicas.f5;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * El cálculo que comparten T31 · Matriz de decisión ponderada y T33 · Inferencia a la mejor explicación (patrón V03b):
 * totales Σ peso × puntaje, multiplicados por un factor por opción (la probabilidad de T31, o 1), ranking con empates y
 * sensibilidad de cada criterio. Todo en enteros, sin redondeos. Las reglas están en docs/ejemplos/T31.md (regla 5).
 */
public final class MatrizPonderada {

    /** Peso mínimo y máximo que prueba la sensibilidad. */
    public static final int PESO_MINIMO = 0;
    public static final int PESO_MAXIMO = 10;

    /**
     * @param totales  Σ peso × puntaje de cada opción, en su orden
     * @param valores  total × factor de cada opción: lo que ordena
     * @param puestos  1 + cuántas tienen más valor
     * @param ganador  el índice de la primera si nadie la iguala; vacío si hay empate en el primer lugar
     * @param cambios  por criterio, el primer peso que deja otra opción estrictamente por encima del ganador; vacío si no hay
     */
    public record Calculo(List<Long> totales, List<Long> valores, List<Integer> orden, List<Integer> puestos, Optional<Integer> ganador,
                          List<Optional<Cambio>> cambios) {

        /** La menor distancia entre todos los criterios; vacía si ninguno cambia el ganador o si hay empate. */
        public Optional<Integer> menorDistancia() {
            return cambios.stream().flatMap(Optional::stream).map(Cambio::distancia).min(Integer::compare);
        }

        /** Alta si la menor distancia es 1, media si es 2 o 3, baja si es 4 o más o si ninguno cambia el ganador. */
        public String nivel() {
            int d = menorDistancia().orElse(Integer.MAX_VALUE);
            return d == 1 ? "alta" : d <= 3 ? "media" : "baja";
        }
    }

    /** @param nuevoGanador el índice de la opción que pasa a ganar con ese peso */
    public record Cambio(int pesoNuevo, int distancia, int nuevoGanador) {
    }

    private MatrizPonderada() {
    }

    /**
     * @param pesos    uno por criterio
     * @param puntajes por opción, uno por criterio
     * @param factores por opción (la probabilidad entera de T31); todos 1 si no hay
     */
    public static Calculo calcular(List<Integer> pesos, List<List<Integer>> puntajes, List<Integer> factores) {
        List<Long> totales = puntajes.stream().map(p -> total(pesos, p)).toList();
        List<Long> valores = IntStream.range(0, puntajes.size()).mapToObj(i -> totales.get(i) * factores.get(i)).toList();
        List<Integer> orden = IntStream.range(0, puntajes.size()).boxed()
                .sorted(Comparator.comparingLong((Integer i) -> valores.get(i)).reversed().thenComparingInt(i -> i)).toList();
        List<Integer> puestos = IntStream.range(0, puntajes.size())
                .mapToObj(i -> 1 + (int) valores.stream().filter(v -> v > valores.get(i)).count()).toList();
        long primeros = puestos.stream().filter(p -> p == 1).count();
        Optional<Integer> ganador = primeros == 1 ? Optional.of(orden.getFirst()) : Optional.empty();
        List<Optional<Cambio>> cambios = new ArrayList<>();
        for (int c = 0; c < pesos.size(); c++) {
            cambios.add(ganador.isEmpty() ? Optional.empty() : cambio(pesos, puntajes, factores, c, ganador.get()));
        }
        return new Calculo(totales, valores, orden, puestos, ganador, cambios);
    }

    /** Distancia 1, 2… subiendo primero y luego bajando, hasta salir de 0 a 10; el primer peso con otro ganador estricto. */
    static Optional<Cambio> cambio(List<Integer> pesos, List<List<Integer>> puntajes, List<Integer> factores, int criterio, int ganador) {
        int actual = pesos.get(criterio);
        for (int d = 1; d <= PESO_MAXIMO - PESO_MINIMO; d++) {
            for (int nuevo : new int[] {actual + d, actual - d}) {
                if (nuevo < PESO_MINIMO || nuevo > PESO_MAXIMO) {
                    continue;
                }
                List<Integer> probados = new ArrayList<>(pesos);
                probados.set(criterio, nuevo);
                long delGanador = total(probados, puntajes.get(ganador)) * factores.get(ganador);
                int mejor = -1;
                long mejorValor = delGanador;
                for (int o = 0; o < puntajes.size(); o++) {
                    long v = total(probados, puntajes.get(o)) * factores.get(o);
                    if (o != ganador && v > mejorValor) {
                        mejor = o;
                        mejorValor = v;
                    }
                }
                if (mejor >= 0) {
                    return Optional.of(new Cambio(nuevo, d, mejor));
                }
            }
        }
        return Optional.empty();
    }

    private static long total(List<Integer> pesos, List<Integer> puntajes) {
        long t = 0;
        for (int c = 0; c < pesos.size(); c++) {
            t += (long) pesos.get(c) * puntajes.get(c);
        }
        return t;
    }
}
