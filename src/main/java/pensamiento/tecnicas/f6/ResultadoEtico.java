package pensamiento.tecnicas.f6;

import java.util.List;

/**
 * Valor que pinta el patrón V03b (matriz con totales) para T39 · Razonamiento ético: una fila por marco activo, una columna
 * por parte afectada con su valoración (+1, 0 o −1), el balance de cada marco, los conflictos que señalan las reglas y las
 * objeciones por marco.
 */
public record ResultadoEtico(String decision, List<String> partes, List<FilaMarco> marcos, List<String> conflictos, List<Objecion> objeciones,
                             List<String> avisos, String resumen) {

    /** @param balance la suma de la fila, con signo */
    public record FilaMarco(String id, String nombre, List<Integer> valoraciones, int balance) {
        public FilaMarco {
            valoraciones = List.copyOf(valoraciones);
        }
    }

    /** @param texto la objeción; nula si falta */
    public record Objecion(String marco, String nombre, String texto) {
    }

    public ResultadoEtico {
        partes = List.copyOf(partes);
        marcos = List.copyOf(marcos);
        conflictos = List.copyOf(conflictos);
        objeciones = List.copyOf(objeciones);
        avisos = List.copyOf(avisos);
    }
}
