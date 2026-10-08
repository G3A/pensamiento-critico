package pensamiento.tecnicas.f3;

import java.util.List;

import pensamiento.nucleo.Propuesta;

/**
 * Valor del resultado de T13 que pinta el patrón V05 (texto propio marcado). Cada marca dice esquema,
 * pregunta crítica sin responder y porqué; la etiqueta de falacia solo cuenta si está confirmada (R06).
 *
 * @param texto el texto evaluado, para pintar las marcas en su lugar
 */
public record ResultadoFalacias(String texto, boolean mostrarPregunta, List<Marca> marcas, int confirmadas, int propuestas, String resumen,
                                List<Propuesta> delModelo) {

    public enum Estado {
        PROPUESTA, CONFIRMADA;

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    /**
     * @param inicio posición del fragmento en el texto, en caracteres (incluida)
     * @param fin    posición del final del fragmento (excluida)
     * @param origen "reglas" o "modelo" (una propuesta adoptada); nulo en ejecuciones anteriores al hito 3, que son de reglas
     */
    public record Marca(String codigo, int inicio, int fin, String fragmento, String esquema, String esquemaNombre, int pregunta,
                        String preguntaTexto, String falacia, String comoResponder, String porque, Estado estado, String origen) {

        public boolean delModelo() {
            return "modelo".equals(origen);
        }

        public boolean confirmada() {
            return estado == Estado.CONFIRMADA;
        }
    }

    public ResultadoFalacias {
        marcas = List.copyOf(marcas);
        delModelo = delModelo == null ? List.of() : List.copyOf(delModelo);
    }
}
