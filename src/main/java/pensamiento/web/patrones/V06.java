package pensamiento.web.patrones;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Patrón V06, árbol o cadena (T42 · Árbol de hipótesis MECE, T26 · Estimación de Fermi; luego T09). Record tipado de
 * tag/v/v06.jte: raíz id="res-{idEjecucion}" y data-patron="V06". El diagrama es SVG de Graphviz ya saneado y sin
 * colores; debajo va la misma estructura como lista con texto, que también sirve si Graphviz no respondió.
 *
 * @param svg      el diagrama; vacío si no se pudo dibujar
 * @param items    los nodos en orden, con su nivel de sangría (0 la raíz) y su estado con texto
 * @param secciones listas con título debajo del diagrama (hojas, huecos, solapes, factores…)
 */
public record V06(Optional<UUID> idEjecucion, String sufijo, Modo modo, String titulo, String enunciado, Optional<String> svg,
                  String descripcionSvg, String tituloLista, List<Item> items, List<Seccion> secciones, List<String> avisos, String resumen,
                  String tarjeta) {

    public static final String PATRON = "V06";

    /**
     * @param id    el id del nodo en el SVG (el identificador de su afirmación)
     * @param chip  el estado del nodo con texto ("rama vacía", "hoja"); nulo si no tiene
     */
    public record Item(String id, String codigo, String texto, String chip, String claseChip, int nivel) {
    }

    public record Seccion(String titulo, List<String> lineas) {
        public Seccion {
            lineas = List.copyOf(lineas);
        }
    }

    public V06 {
        items = List.copyOf(items);
        secciones = List.copyOf(secciones);
        avisos = List.copyOf(avisos);
    }

    public String idRaiz() {
        return idRaiz(idEjecucion, sufijo);
    }

    public static String idRaiz(Optional<UUID> idEjecucion, String sufijo) {
        return "res-" + idEjecucion.map(UUID::toString).orElse(PATRON + "-" + sufijo);
    }

    public boolean lectura() {
        return modo == Modo.LECTURA;
    }
}
