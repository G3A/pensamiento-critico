package pensamiento.web.patrones;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Patrón V03b, matriz ponderada con totales y sensibilidad (T31 · Matriz de decisión ponderada, T33 · Inferencia a la
 * mejor explicación, T39 · Razonamiento ético). Record tipado de tag/v/v03b.jte: raíz id="res-{idEjecucion}" y
 * data-patron="V03b". La tabla tiene caption y encabezados con scope, y a 360 px se desplaza dentro de su contenedor; el
 * puesto y el empate van con texto.
 *
 * @param probabilidades     encabezado y valores de la columna de probabilidad; vacío si la técnica no la usa
 * @param caption            el caption de la tabla
 * @param tituloSensibilidad el título de la lista de debajo de la tabla (sensibilidad en T31 y T33, conflictos en T39)
 */
public record V03b(Optional<UUID> idEjecucion, String sufijo, Modo modo, String titulo, String enunciado, String tituloOpcion,
                   List<Columna> columnas, List<Fila> filas, String tituloProbabilidad, String tituloTotal, String ganador, String nivelSensibilidad,
                   List<String> sensibilidad, String justificacion, List<String> avisos, String resumen, String tarjeta, String caption,
                   String tituloSensibilidad) {

    public static final String PATRON = "V03b";

    /** @param peso nulo si la técnica no pondera las columnas (T39) */
    public record Columna(String criterio, Integer peso) {
    }

    /**
     * @param puesto       "1º"; nulo si la técnica no ordena las filas (T39)
     * @param probabilidad "100%"; nula si no hay columna de probabilidad
     */
    public record Fila(String puesto, String opcion, List<Integer> puntajes, String probabilidad, String total, boolean empate) {
        public Fila {
            puntajes = List.copyOf(puntajes);
        }
    }

    public V03b {
        columnas = List.copyOf(columnas);
        filas = List.copyOf(filas);
        sensibilidad = List.copyOf(sensibilidad);
        avisos = List.copyOf(avisos);
    }

    /** La matriz ponderada de T31 y T33, con su caption y su título de sensibilidad. */
    public V03b(Optional<UUID> idEjecucion, String sufijo, Modo modo, String titulo, String enunciado, String tituloOpcion, List<Columna> columnas,
                List<Fila> filas, String tituloProbabilidad, String tituloTotal, String ganador, String nivelSensibilidad, List<String> sensibilidad,
                String justificacion, List<String> avisos, String resumen, String tarjeta) {
        this(idEjecucion, sufijo, modo, titulo, enunciado, tituloOpcion, columnas, filas, tituloProbabilidad, tituloTotal, ganador, nivelSensibilidad,
                sensibilidad, justificacion, avisos, resumen, tarjeta, "Puntaje por criterio; el peso va entre paréntesis", "Sensibilidad a los pesos");
    }

    public String idRaiz() {
        return "res-" + idEjecucion.map(UUID::toString).orElse(PATRON + "-" + sufijo);
    }

    public boolean conProbabilidad() {
        return tituloProbabilidad != null;
    }

    public boolean conPuesto() {
        return filas.stream().anyMatch(f -> f.puesto() != null);
    }

    public boolean lectura() {
        return modo == Modo.LECTURA;
    }
}
