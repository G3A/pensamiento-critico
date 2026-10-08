package pensamiento.tecnicas.f5;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongFunction;

/**
 * Valor que pinta el patrón V03b (matriz ponderada con totales y sensibilidad) para T31 · Matriz de decisión ponderada
 * y T33 · Inferencia a la mejor explicación: criterios con su peso, opciones con sus puntajes en el orden del ranking,
 * totales, puestos con empate y una frase de sensibilidad por criterio.
 *
 * @param probabilidades por opción, en el orden en que se escribieron; nulas si la técnica no las usa
 * @param ganador        el texto de la opción ganadora; nulo si hay empate en el primer lugar
 */
public record ResultadoMatriz(String titulo, String pregunta, List<CriterioPeso> criterios, List<FilaOpcion> filas, List<Integer> probabilidades, String ganador,
                              String nivelSensibilidad, List<String> sensibilidad, String justificacion, List<String> avisos, String resumen) {

    public record CriterioPeso(String criterio, int peso) {
    }

    /**
     * @param valor     el total que ordena, como texto ("29", "27,0")
     * @param puntajes     uno por criterio, en el orden de los criterios
     * @param probabilidad la de la opción con probabilidad × impacto (T31); nula si no hay
     */
    public record FilaOpcion(int puesto, String opcion, List<Integer> puntajes, long total, String valor, boolean empate, Integer probabilidad) {
        public FilaOpcion {
            puntajes = List.copyOf(puntajes);
        }
    }

    public ResultadoMatriz {
        criterios = List.copyOf(criterios);
        filas = List.copyOf(filas);
        probabilidades = probabilidades == null ? null : List.copyOf(probabilidades);
        sensibilidad = List.copyOf(sensibilidad);
        avisos = avisos == null ? List.of() : List.copyOf(avisos);
    }

    ResultadoMatriz conResumen(String nuevo) {
        return new ResultadoMatriz(titulo, pregunta, criterios, filas, probabilidades, ganador, nivelSensibilidad, sensibilidad, justificacion, avisos, nuevo);
    }

    ResultadoMatriz conJustificacionYAvisos(String nuevaJustificacion, List<String> nuevosAvisos) {
        return new ResultadoMatriz(titulo, pregunta, criterios, filas, probabilidades, ganador, nivelSensibilidad, sensibilidad, nuevaJustificacion,
                nuevosAvisos, resumen);
    }

    /**
     * Arma el valor desde el cálculo: filas en el orden del ranking y la frase de sensibilidad de cada criterio
     * (docs/ejemplos/T31.md, regla 5).
     *
     * @param verbo     "gana"
     * @param sinCambio "ningún peso entre 0 y 10 cambia el ganador" o "… cambia la mejor"
     */
    static ResultadoMatriz armar(String titulo, String pregunta, List<String> criterios, List<Integer> pesos, List<String> opciones, List<List<Integer>> puntajes,
                                 MatrizPonderada.Calculo calculo, List<Integer> probabilidades, LongFunction<String> formato, String verbo,
                                 String sinCambio) {
        List<CriterioPeso> cps = new ArrayList<>();
        for (int c = 0; c < criterios.size(); c++) {
            cps.add(new CriterioPeso(criterios.get(c), pesos.get(c)));
        }
        List<FilaOpcion> filas = new ArrayList<>();
        for (int i : calculo.orden()) {
            int puesto = calculo.puestos().get(i);
            boolean empate = calculo.puestos().stream().filter(p -> p == puesto).count() > 1;
            filas.add(new FilaOpcion(puesto, opciones.get(i), puntajes.get(i), calculo.totales().get(i), formato.apply(calculo.valores().get(i)), empate,
                    probabilidades == null ? null : probabilidades.get(i)));
        }
        List<String> sensibilidad = new ArrayList<>();
        if (calculo.ganador().isEmpty()) {
            sensibilidad.add("Empate en el primer lugar: la sensibilidad no aplica.");
        } else {
            for (int c = 0; c < criterios.size(); c++) {
                var cambio = calculo.cambios().get(c);
                sensibilidad.add(cambio.isPresent()
                        ? criterios.get(c) + ": si pesa " + cambio.get().pesoNuevo() + " en vez de " + pesos.get(c) + ", " + verbo + " "
                        + opciones.get(cambio.get().nuevoGanador()) + "."
                        : criterios.get(c) + ": " + sinCambio + ".");
            }
        }
        String ganador = calculo.ganador().map(opciones::get).orElse(null);
        return new ResultadoMatriz(titulo, pregunta, cps, filas, probabilidades, ganador, calculo.ganador().isPresent() ? calculo.nivel() : null, sensibilidad,
                null, List.of(), "");
    }
}
