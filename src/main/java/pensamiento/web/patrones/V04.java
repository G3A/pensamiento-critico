package pensamiento.web.patrones;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import pensamiento.nucleo.Propuesta;

/**
 * Patrón V04, dos columnas comparativas con diferencias marcadas (T07 · Razonamiento por analogía, T15 · Considera
 * lo opuesto, T34 · Steelmanning). Record tipado de tag/v/v04.jte: raíz id="res-{idEjecucion}" y data-patron="V04".
 * Cada técnica traduce su resultado a este record en su renderizador; todo estado lleva texto además del color y las
 * columnas se apilan a 360 px.
 *
 * @param enunciado  una línea sobre las columnas (por ejemplo, el caso de origen de la analogía); nula si no hay
 * @param estado     el veredicto o el estado, con su motivo; nulo si la técnica no tiene uno
 * @param preguntas  las preguntas que quedaron sin responder (corrección 13), con su título
 * @param propuestas las propuestas del modelo que trae la entrada, para decir cuáles no cuentan
 */
public record V04(Optional<UUID> idEjecucion, String sufijo, Modo modo, String titulo, String enunciado, Columna izquierda,
                  Columna derecha, Estado estado, String tituloPreguntas, List<String> preguntas, List<Propuesta> propuestas,
                  String resumen, String tarjeta) {

    public static final String PATRON = "V04";

    public record Columna(String titulo, List<Item> items, String vacia) {
        public Columna {
            items = List.copyOf(items);
        }
    }

    /**
     * @param chip      texto del estado del ítem ("clave", "del modelo"); nulo si no tiene
     * @param claseChip clase CSS del chip
     * @param detalle   una línea debajo, por ejemplo "Qué cambiaría: …"; nula si no hay
     */
    public record Item(String texto, String chip, String claseChip, String detalle) {
    }

    public record Estado(String etiqueta, String texto, String claseChip, String motivo) {
    }

    public V04 {
        preguntas = preguntas == null ? List.of() : List.copyOf(preguntas);
        propuestas = propuestas == null ? List.of() : List.copyOf(propuestas);
    }

    public String idRaiz() {
        return "res-" + idEjecucion.map(UUID::toString).orElse(PATRON + "-" + sufijo);
    }

    public boolean lectura() {
        return modo == Modo.LECTURA;
    }
}
