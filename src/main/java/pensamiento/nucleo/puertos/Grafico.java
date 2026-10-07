package pensamiento.nucleo.puertos;

/**
 * Puerto de render de grafos: recibe DOT generado por el servidor y devuelve SVG inerte (saneado por
 * lista blanca: sin script, sin manejadores de eventos, sin enlaces ejecutables).
 */
public interface Grafico {

    String svg(String dot);

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
