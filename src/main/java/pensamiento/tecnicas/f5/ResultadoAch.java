package pensamiento.tecnicas.f5;

import java.util.List;
import java.util.UUID;

/**
 * Valor del resultado de T28 que pinta el patrón V03a. Guarda el identificador de la afirmación de cada
 * hipótesis; el texto se conserva para poder pintar la matriz sin otra consulta.
 *
 * @param menosRefutadas códigos de las hipótesis con menos inconsistencias ponderadas, en orden de entrada
 * @param empate         verdadero si hay más de una menos refutada
 * @param masRefutadas   códigos de las hipótesis con más inconsistencias; vacía si todas empatan
 * @param verificar      un pendiente por cada menos refutada
 */
public record ResultadoAch(
        String pregunta,
        ConfigAch.Escala escala,
        boolean pesosActivos,
        List<HipotesisEvaluada> hipotesis,
        List<EvidenciaEvaluada> evidencias,
        List<String> menosRefutadas,
        boolean empate,
        List<String> masRefutadas,
        List<Verificacion> verificar,
        String resumen) {

    public record HipotesisEvaluada(String codigo, UUID afirmacionId, String texto, int inconsistencias, List<String> evidenciasEnContra) {
        public HipotesisEvaluada {
            evidenciasEnContra = List.copyOf(evidenciasEnContra);
        }
    }

    /** pesoValor es 1 cuando los pesos están inactivos; peso queda nulo en ese caso. */
    public record EvidenciaEvaluada(String codigo, String texto, EntradaAch.Peso peso, int pesoValor, List<String> celdas) {
        public EvidenciaEvaluada {
            celdas = List.copyOf(celdas);
        }
    }

    /** evidencia es nula si la hipótesis no tiene ninguna evidencia a favor: hay que buscar una que la distinga. */
    public record Verificacion(String hipotesis, String evidencia) {
    }

    public ResultadoAch {
        hipotesis = List.copyOf(hipotesis);
        evidencias = List.copyOf(evidencias);
        menosRefutadas = List.copyOf(menosRefutadas);
        masRefutadas = List.copyOf(masRefutadas);
        verificar = List.copyOf(verificar);
    }

    public HipotesisEvaluada hipotesis(String codigo) {
        return hipotesis.stream().filter(h -> h.codigo().equals(codigo)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No hay hipótesis " + codigo));
    }

    public EvidenciaEvaluada evidencia(String codigo) {
        return evidencias.stream().filter(e -> e.codigo().equals(codigo)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No hay evidencia " + codigo));
    }
}
