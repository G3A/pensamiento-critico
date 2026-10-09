package pensamiento.web.patrones;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import pensamiento.nucleo.Propuesta;

/**
 * Patrón V09, transcripción con panel lateral (T08 · Preguntas socráticas, T36 · Equipo rojo / abogado del diablo). Record
 * tipado de tag/v/v09.jte: raíz id="res-{idEjecucion}" y data-patron="V09". La transcripción es una lista con rol ARIA
 * "log" y cada burbuja dice quién habla con texto; el panel lateral (elementos, estándares, debilidades) lleva el estado
 * de cada ítem con texto además del color. A 360 px el panel baja debajo de la transcripción.
 *
 * @param siguiente lo que viene (la pregunta que sigue o la de cierre); nula si no hay
 */
public record V09(Optional<UUID> idEjecucion, String sufijo, Modo modo, String titulo, String enunciado, List<Burbuja> transcripcion,
                  Burbuja siguiente, String tituloPanel, List<SeccionPanel> panel, List<String> avisos, List<Propuesta> propuestas, String resumen,
                  String tarjeta) {

    public static final String PATRON = "V09";

    /**
     * Una intervención.
     *
     * @param rol    "persona", "consejero" o "equipo-rojo": la clase CSS de la burbuja
     * @param autor  quién habla, como se lee ("tú", "consejero · evidencia", "equipo rojo · ataque 1 de 3 · a R1")
     * @param chips  marcas cortas con texto ("del banco", "del modelo", "rama: cifra", "sin responder")
     * @param detalle una línea debajo (por qué el motor eligió esa pregunta); nula si no hay
     */
    public record Burbuja(String clave, String rol, String autor, String texto, List<String> chips, String detalle) {
        public Burbuja {
            chips = chips == null ? List.of() : List.copyOf(chips);
        }
    }

    public record SeccionPanel(String titulo, List<ItemPanel> items) {
        public SeccionPanel {
            items = List.copyOf(items);
        }
    }

    /** @param chip el estado con texto ("lleno", "pendiente", "cumple · 10") */
    public record ItemPanel(String clave, String nombre, String chip, String claseChip, String texto, String detalle) {
    }

    public V09 {
        transcripcion = List.copyOf(transcripcion);
        panel = List.copyOf(panel);
        avisos = avisos == null ? List.of() : List.copyOf(avisos);
        propuestas = propuestas == null ? List.of() : List.copyOf(propuestas);
    }

    public String idRaiz() {
        return "res-" + idEjecucion.map(UUID::toString).orElse(PATRON + "-" + sufijo);
    }

    public boolean lectura() {
        return modo == Modo.LECTURA;
    }
}
