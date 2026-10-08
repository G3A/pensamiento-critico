package pensamiento.web.patrones;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Patrón V08, gráfico de probabilidad o frecuencias (T18 · Correlación, causalidad y tasas base; luego T24 y T25).
 * Record tipado de tag/v/v08.jte: raíz id="res-{idEjecucion}" y data-patron="V08". El gráfico es SVG escrito por el
 * servidor, sin librerías de JavaScript, con los colores por clase CSS (sin fill ni style) y con los mismos números en
 * una tabla para quien no ve el gráfico.
 *
 * @param barras     las barras del gráfico, ya medidas; vacío si no hay cálculo
 * @param filasTabla los números completos (incluidos los que no se dibujan)
 * @param items      los criterios u otros ítems con su estado
 */
public record V08(Optional<UUID> idEjecucion, String sufijo, Modo modo, String titulo, String enunciado, String tituloGrafico,
                  List<Barra> barras, List<Fila> filasTabla, String frase, String tituloItems, List<Item> items, List<String> avisos,
                  String resumen, String tarjeta) {

    public static final String PATRON = "V08";

    /** Ancho del área de barras dentro del SVG, en unidades del viewBox. */
    public static final int ANCHO_BARRAS = 200;
    public static final int ALTO_FILA = 28;
    public static final int X_BARRAS = 150;

    /** @param ancho el largo de la barra en unidades del viewBox, ya escalado */
    public record Barra(String clave, String etiqueta, int valor, int ancho, String clase) {
    }

    public record Fila(String etiqueta, int valor) {
    }

    public record Item(String clave, String nombre, String chip, String claseChip, String detalle) {
    }

    public V08 {
        barras = List.copyOf(barras);
        filasTabla = List.copyOf(filasTabla);
        items = List.copyOf(items);
        avisos = List.copyOf(avisos);
    }

    /** Barras escaladas al mayor valor: una barra con valor mayor que cero mide al menos 2 unidades para que se vea. */
    public static List<Barra> escalar(List<Fila> filas, List<String> claves, List<String> clases) {
        int maximo = filas.stream().mapToInt(Fila::valor).max().orElse(0);
        java.util.ArrayList<Barra> barras = new java.util.ArrayList<>();
        for (int i = 0; i < filas.size(); i++) {
            Fila f = filas.get(i);
            int ancho = maximo == 0 ? 0 : Math.max(f.valor() > 0 ? 2 : 0, Math.round((float) f.valor() * ANCHO_BARRAS / maximo));
            barras.add(new Barra(claves.get(i), f.etiqueta(), f.valor(), ancho, clases.get(i)));
        }
        return barras;
    }

    public String idRaiz() {
        return "res-" + idEjecucion.map(UUID::toString).orElse(PATRON + "-" + sufijo);
    }

    public int altoSvg() {
        return Math.max(1, barras.size()) * ALTO_FILA + 8;
    }

    public int y(int indice) {
        return 4 + indice * ALTO_FILA;
    }

    public boolean lectura() {
        return modo == Modo.LECTURA;
    }
}
