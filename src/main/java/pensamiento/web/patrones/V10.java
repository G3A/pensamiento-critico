package pensamiento.web.patrones;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import pensamiento.nucleo.Propuesta;

/**
 * Patrón V10, tarjeta de veredicto con barras de puntaje (T03 · Afirmación, evidencia, razonamiento (CER), T05 ·
 * Validez y solidez, T22 · Triangulación). Record tipado de tag/v/v10.jte: raíz id="res-{idEjecucion}" y
 * data-patron="V10". Cada técnica traduce su resultado a este record; las barras son meter con su etiqueta, los
 * ítems llevan su estado con texto y el veredicto dice su motivo (corrección 13: nunca "válido" ni "ok").
 *
 * @param enunciado lo que se evalúa (la afirmación, el argumento); nulo si no hay
 * @param barras    medidores con etiqueta visible, por ejemplo "Completitud 4 de 5"
 * @param items     una línea por pieza o por evidencia, con su estado
 * @param veredicto el estado final con su motivo; nulo si la técnica no tiene uno
 */
public record V10(Optional<UUID> idEjecucion, String sufijo, Modo modo, String titulo, String enunciado, List<Barra> barras,
                  String tituloItems, List<Item> items, Veredicto veredicto, List<String> lineas, List<Propuesta> propuestas,
                  String resumen, String tarjeta) {

    public static final String PATRON = "V10";

    /** Un medidor: valor sobre máximo, con la etiqueta que se lee. El mínimo puede ser negativo (fuerza neta). */
    public record Barra(String clave, String etiqueta, int minimo, int maximo, int valor) {
    }

    /**
     * @param chip    el estado del ítem con texto ("completa", "apoya · cuenta")
     * @param detalle una línea debajo: la pregunta que falta, el pasaje, la fuerza
     */
    public record Item(String clave, String nombre, String chip, String claseChip, String texto, String detalle) {
    }

    public record Veredicto(String etiqueta, String texto, String claseChip, String motivo) {
    }

    public V10 {
        barras = barras == null ? List.of() : List.copyOf(barras);
        items = items == null ? List.of() : List.copyOf(items);
        lineas = lineas == null ? List.of() : List.copyOf(lineas);
        propuestas = propuestas == null ? List.of() : List.copyOf(propuestas);
    }

    public String idRaiz() {
        return "res-" + idEjecucion.map(UUID::toString).orElse(PATRON + "-" + sufijo);
    }

    public String idBarra(Barra b) {
        return idRaiz() + "-" + b.clave();
    }

    public boolean lectura() {
        return modo == Modo.LECTURA;
    }
}
