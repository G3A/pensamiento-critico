package pensamiento.tecnicas.f5;

import java.util.List;

/**
 * Valor que pinta el patrón V12 (ranking con barras) para T27 · Valor esperado: las opciones de mayor a menor valor, con
 * su peor caso y la marca de empate.
 *
 * @param aversion si el valor está ajustado por aversión a pérdidas
 */
public record ResultadoValorEsperado(String pregunta, String unidad, boolean aversion, List<Fila> ranking, List<String> avisos, String resumen) {

    /**
     * @param valor      con un decimal y signo: "+7,2"
     * @param centesimas probabilidad × impacto sumados, sin dividir: el largo de la barra
     * @param peorCaso   el menor impacto entre los escenarios con probabilidad
     */
    public record Fila(int puesto, String opcion, String valor, long centesimas, Integer peorCaso, boolean empate) {
    }

    public ResultadoValorEsperado {
        ranking = List.copyOf(ranking);
        avisos = List.copyOf(avisos);
    }
}
