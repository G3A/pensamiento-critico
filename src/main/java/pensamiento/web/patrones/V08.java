package pensamiento.web.patrones;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Patrón V08, gráfico de probabilidad o frecuencias (T18 · Correlación, causalidad y tasas base, T24 · Razonamiento
 * bayesiano y T25 · Calibración y puntaje Brier, también en el tablero del Diario). Record tipado de tag/v/v08.jte: raíz
 * id="res-{idEjecucion}" y data-patron="V08". Los gráficos son SVG escritos por el servidor, sin librerías de JavaScript,
 * con los colores por clase CSS (sin fill ni style) y con los mismos números en una tabla para quien no ve el gráfico.
 *
 * @param barras     las barras del gráfico, ya medidas; vacío si no hay
 * @param filasTabla los números completos (incluidos los que no se dibujan)
 * @param items      los criterios u otros ítems con su estado
 * @param curva      los puntos de la curva de calibración, uno por tramo con resueltas; vacío si no es una curva
 * @param tablaCurva las filas de la curva con texto (tramo, n, declarada, se cumple, provisional)
 */
public record V08(Optional<UUID> idEjecucion, String sufijo, Modo modo, String titulo, String enunciado, String tituloGrafico,
                  List<Barra> barras, List<Fila> filasTabla, String frase, String tituloItems, List<Item> items, List<String> avisos,
                  String resumen, String tarjeta, List<Punto> curva, List<FilaCurva> tablaCurva) {

    public static final String PATRON = "V08";

    /** Ancho del área de barras dentro del SVG, en unidades del viewBox. */
    public static final int ANCHO_BARRAS = 200;
    public static final int ALTO_FILA = 28;
    public static final int X_BARRAS = 150;

    /** Lado del área de la curva y su margen, en unidades del viewBox; 100 puntos de confianza ocupan LADO_CURVA. */
    public static final int LADO_CURVA = 160;
    public static final int MARGEN_CURVA = 30;

    /** @param ancho el largo de la barra en unidades del viewBox, ya escalado */
    public record Barra(String clave, String etiqueta, int valor, int ancho, String clase) {
    }

    public record Fila(String etiqueta, int valor) {
    }

    public record Item(String clave, String nombre, String chip, String claseChip, String detalle) {
    }

    /** Un tramo: confianza declarada promedio (x) contra porcentaje que se cumplió (y), los dos de 0 a 100. */
    public record Punto(int declarada, int cumplida, int n, boolean provisional) {
        public int x() {
            return MARGEN_CURVA + declarada * LADO_CURVA / 100;
        }

        public int y() {
            return MARGEN_CURVA + LADO_CURVA - cumplida * LADO_CURVA / 100;
        }
    }

    public record FilaCurva(String tramo, int n, int declarada, int cumplida, boolean provisional) {
    }

    public V08 {
        barras = List.copyOf(barras);
        filasTabla = List.copyOf(filasTabla);
        items = List.copyOf(items);
        avisos = List.copyOf(avisos);
        curva = curva == null ? List.of() : List.copyOf(curva);
        tablaCurva = tablaCurva == null ? List.of() : List.copyOf(tablaCurva);
    }

    /** Sin curva: el gráfico de barras de T18 y T24. */
    public V08(Optional<UUID> idEjecucion, String sufijo, Modo modo, String titulo, String enunciado, String tituloGrafico, List<Barra> barras,
               List<Fila> filasTabla, String frase, String tituloItems, List<Item> items, List<String> avisos, String resumen, String tarjeta) {
        this(idEjecucion, sufijo, modo, titulo, enunciado, tituloGrafico, barras, filasTabla, frase, tituloItems, items, avisos, resumen, tarjeta,
                List.of(), List.of());
    }

    /** Barras escaladas al mayor valor: una barra con valor mayor que cero mide al menos 2 unidades para que se vea. */
    public static List<Barra> escalar(List<Fila> filas, List<String> claves, List<String> clases) {
        int maximo = filas.stream().mapToInt(Fila::valor).max().orElse(0);
        return escalar(filas, claves, clases, maximo);
    }

    /** Barras escaladas a un máximo fijo (100 para porcentajes). */
    public static List<Barra> escalar(List<Fila> filas, List<String> claves, List<String> clases, int maximo) {
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

    /** Los puntos de la curva como "x,y x,y" para la polilínea. */
    public String puntosCurva() {
        return String.join(" ", curva.stream().map(p -> p.x() + "," + p.y()).toList());
    }

    public int bordeCurva() {
        return MARGEN_CURVA + LADO_CURVA;
    }

    public boolean lectura() {
        return modo == Modo.LECTURA;
    }
}
