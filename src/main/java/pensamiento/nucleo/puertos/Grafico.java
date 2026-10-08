package pensamiento.nucleo.puertos;

/**
 * Puerto de render de grafos: recibe DOT generado por el servidor y devuelve SVG inerte (saneado por
 * lista blanca: sin script, sin manejadores de eventos, sin enlaces ejecutables) y sin colores: fill, stroke y
 * style no salen; los colores los pone app.css por la clase de cada nodo.
 */
public interface Grafico {

    String svg(String dot);

    /**
     * Un texto como cadena DOT entre comillas, con barras, comillas y saltos de línea escapados: lo que el usuario
     * escribe nunca cierra la cadena ni cambia el grafo. Un salto de línea pasa a ser el salto de etiqueta de DOT.
     */
    static String cadena(String texto) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> { }
                default -> sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

    /** El DOT no se pudo interpretar. */
    class GraficoInvalido extends RuntimeException {
        public GraficoInvalido(String mensaje) {
            super(mensaje);
        }
    }

    /** El proceso de render superó su tiempo máximo. */
    class GraficoTiempoAgotado extends RuntimeException {
        public GraficoTiempoAgotado(String mensaje) {
            super(mensaje);
        }
    }
}
