package pensamiento.web.patrones;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Patrón V13a, lista enlazada o priorizada (T14 · Sesgos cognitivos; luego T11, T29 y T40). Record tipado de
 * tag/v/v13a.jte: raíz id="res-{idEjecucion}" y data-patron="V13a". La lista va en orden de prioridad y cada ítem
 * dice su estado con texto; debajo, las acciones sugeridas.
 */
public record V13a(Optional<UUID> idEjecucion, String sufijo, Modo modo, String titulo, String enunciado, List<Item> items,
                   String tituloAcciones, List<String> acciones, String resumen, String tarjeta) {

    public static final String PATRON = "V13a";

    /** @param detalle la señal o el motivo, en una línea */
    public record Item(String clave, String nombre, String chip, String claseChip, String detalle) {
    }

    public V13a {
        items = List.copyOf(items);
        acciones = acciones == null ? List.of() : List.copyOf(acciones);
    }

    public String idRaiz() {
        return "res-" + idEjecucion.map(UUID::toString).orElse(PATRON + "-" + sufijo);
    }

    public boolean lectura() {
        return modo == Modo.LECTURA;
    }
}
