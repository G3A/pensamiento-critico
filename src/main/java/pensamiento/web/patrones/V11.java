package pensamiento.web.patrones;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Patrón V11, registro con línea de tiempo (T32 · Diario de decisiones, T45 · Diario de razonamiento y T46 · Registro de
 * cambios de opinión). Record tipado de tag/v/v11.jte: raíz id="res-{idEjecucion}" y data-patron="V11". Arriba los campos
 * del registro como lista de definiciones; abajo los hitos con su fecha, en una sola línea o en grupos con su título y su
 * resumen (las semanas de T45). Opcionales: barras con su número visible (el año de T46) y una lista aparte (las posturas
 * sin revisar). El estado va con texto ("pendiente de revisión", "se cumplió").
 *
 * @param estado      nulo si el registro no tiene estado
 * @param grupos      si no está vacía, la línea de tiempo se pinta por grupos y {@code linea} se ignora
 * @param tituloBarras nulo si no hay barras
 * @param tituloAparte nulo si no hay lista aparte
 */
public record V11(Optional<UUID> idEjecucion, String sufijo, Modo modo, String titulo, List<Campo> campos, String estado, String claseEstado,
                  List<Hito> linea, List<Grupo> grupos, String tituloBarras, List<Barra> barras, String tituloAparte, List<String> aparte,
                  String bloqueo, List<String> avisos, String resumen, String tarjeta) {

    public static final String PATRON = "V11";

    public record Campo(String nombre, String valor) {
    }

    /** @param fecha AAAA-MM-DD, para el atributo datetime; @param texto la fecha en palabras */
    public record Hito(String fecha, String texto, String que) {
    }

    /** @param resumen nulo si el grupo no lleva resumen */
    public record Grupo(String titulo, String resumen, List<Hito> hitos) {
        public Grupo {
            hitos = List.copyOf(hitos);
        }
    }

    /** Una barra con su etiqueta y su número, siempre visibles; total es el máximo de la escala. */
    public record Barra(String clave, String etiqueta, int valor, int total) {
    }

    public V11 {
        campos = List.copyOf(campos);
        linea = List.copyOf(linea);
        grupos = List.copyOf(grupos);
        barras = List.copyOf(barras);
        aparte = List.copyOf(aparte);
        avisos = List.copyOf(avisos);
    }

    /** El registro de una línea, sin grupos, barras ni lista aparte (T32). */
    public V11(Optional<UUID> idEjecucion, String sufijo, Modo modo, String titulo, List<Campo> campos, String estado, String claseEstado,
               List<Hito> linea, String bloqueo, List<String> avisos, String resumen, String tarjeta) {
        this(idEjecucion, sufijo, modo, titulo, campos, estado, claseEstado, linea, List.of(), null, List.of(), null, List.of(), bloqueo, avisos,
                resumen, tarjeta);
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
