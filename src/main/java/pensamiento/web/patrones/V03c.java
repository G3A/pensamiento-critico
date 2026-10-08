package pensamiento.web.patrones;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Patrón V03c, rejilla de celdas de texto (T44 · SCAMPER y pensamiento lateral; luego T35). Record tipado de
 * tag/v/v03c.jte: raíz id="res-{idEjecucion}" y data-patron="V03c". Cada celda es una sección con su encabezado, su
 * cuenta y sus textos; a 360 px las celdas se apilan. Lo seleccionado lleva texto ("seleccionada"), no solo color.
 */
public record V03c(Optional<UUID> idEjecucion, String sufijo, Modo modo, String titulo, String enunciado, List<Celda> celdas, String nota,
                   String tituloSeleccion, List<String> seleccion, List<String> avisos, String resumen, String tarjeta) {

    public static final String PATRON = "V03c";

    /**
     * @param letra  la letra o código corto de la celda ("S")
     * @param estado el estado de la celda con texto ("faltan ideas"); nulo si está bien
     */
    public record Celda(String clave, String letra, String nombre, List<Texto> textos, String estado) {
        public Celda {
            textos = List.copyOf(textos);
        }
    }

    public record Texto(String texto, boolean seleccionado) {
    }

    public V03c {
        celdas = List.copyOf(celdas);
        seleccion = List.copyOf(seleccion);
        avisos = List.copyOf(avisos);
    }

    public String idRaiz() {
        return "res-" + idEjecucion.map(UUID::toString).orElse(PATRON + "-" + sufijo);
    }

    public boolean lectura() {
        return modo == Modo.LECTURA;
    }
}
