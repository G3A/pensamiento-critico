package pensamiento.web.patrones;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Patrón V12, ranking con barras (T27 · Valor esperado). Record tipado de tag/v/v12.jte: raíz id="res-{idEjecucion}" y
 * data-patron="V12". Las barras son SVG del servidor (sin fill ni style), positivas a la derecha del cero y negativas a la
 * izquierda; la lista ordenada repite puesto, valor y estado con texto.
 */
public record V12(Optional<UUID> idEjecucion, String sufijo, Modo modo, String titulo, String enunciado, String nota, List<Fila> filas,
                  List<String> avisos, String resumen, String tarjeta) {

    public static final String PATRON = "V12";

    /** Ancho de media gráfica (del cero a un extremo), en unidades del viewBox. */
    public static final int MEDIO = 110;
    public static final int X_CERO = 160 + MEDIO;
    public static final int ALTO_FILA = 28;

    /**
     * @param x     dónde empieza la barra (a la izquierda del cero si es negativa)
     * @param ancho su largo, escalado al mayor valor absoluto
     * @param chip  el estado con texto ("empate"); nulo si no hay
     */
    public record Fila(int puesto, String opcion, String valor, String detalle, int x, int ancho, boolean negativa, String chip) {
    }

    public V12 {
        filas = List.copyOf(filas);
        avisos = List.copyOf(avisos);
    }

    /** Escala los valores (en cualquier unidad entera) al mayor valor absoluto. */
    public static Fila fila(int puesto, String opcion, String valor, String detalle, long bruto, long maximoAbsoluto, String chip) {
        int ancho = maximoAbsoluto == 0 ? 0 : (int) Math.max(bruto == 0 ? 0 : 2, Math.round((double) Math.abs(bruto) * MEDIO / maximoAbsoluto));
        boolean negativa = bruto < 0;
        return new Fila(puesto, opcion, valor, detalle, negativa ? X_CERO - ancho : X_CERO, ancho, negativa, chip);
    }

    public String idRaiz() {
        return "res-" + idEjecucion.map(UUID::toString).orElse(PATRON + "-" + sufijo);
    }

    public int altoSvg() {
        return Math.max(1, filas.size()) * ALTO_FILA + 8;
    }

    public int y(int indice) {
        return 4 + indice * ALTO_FILA;
    }

    public boolean lectura() {
        return modo == Modo.LECTURA;
    }
}
