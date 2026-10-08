package pensamiento.web.patrones;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Patrón V07, espina de pescado (T43 · Diagrama de Ishikawa). Record tipado de tag/v/v07.jte: raíz id="res-{idEjecucion}"
 * y data-patron="V07". El diagrama es SVG de Graphviz ya saneado y sin colores; debajo, cada categoría con sus causas y
 * su estado con texto ("vacía", "le falta 1 causa"), que también sirve si Graphviz no respondió.
 */
public record V07(Optional<UUID> idEjecucion, String sufijo, Modo modo, String efecto, Optional<String> svg, List<Categoria> categorias,
                  String resumen, String tarjeta) {

    public static final String PATRON = "V07";

    /** @param estado "vacía", "le falta 1 causa" o nulo si llega al mínimo */
    public record Categoria(String nombre, List<String> causas, String estado) {
        public Categoria {
            causas = List.copyOf(causas);
        }

        public String claseChip() {
            return estado == null ? "chip chip-ok" : "chip chip-aviso";
        }
    }

    public V07 {
        categorias = List.copyOf(categorias);
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
