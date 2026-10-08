package pensamiento.tecnicas.f7;

import java.util.List;

/**
 * Valor que pinta el patrón V13a para T40 · Definición del problema: la reformulación elegida, la original y las
 * descartadas, en ese orden, con cuántas opciones abre cada una y la comparación entre la elegida y la original.
 *
 * @param comparacion nula si la original o la elegida no dicen cuántas opciones abren
 */
public record ResultadoDefinicionProblema(String original, String elegida, List<Item> items, String comparacion, String plantillas,
                                          List<String> avisos, String resumen) {

    public enum Estado {
        ELEGIDA, ORIGINAL, DESCARTADA;

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    /**
     * @param plantilla el nombre de la plantilla usada; nula si no usó una
     * @param opciones  cuántas opciones abre; nula si no lo dice
     */
    public record Item(Estado estado, String texto, String plantilla, Integer opciones) {
    }

    public ResultadoDefinicionProblema {
        items = List.copyOf(items);
        avisos = List.copyOf(avisos);
    }
}
