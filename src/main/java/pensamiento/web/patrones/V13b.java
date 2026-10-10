package pensamiento.web.patrones;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Patrón V13b, barras de progreso (T48 · Taxonomía de Bloom y el progreso del Dojo). Record tipado de tag/v/v13b.jte: raíz
 * id="res-{idEjecucion}" y data-patron="V13b". Una barra por nivel con su estado en texto ("dominado", "en curso",
 * "bloqueado", "apagado") y sus aciertos de la meta, siempre visibles.
 */
public record V13b(Optional<UUID> idEjecucion, String sufijo, Modo modo, String titulo, String enunciado, List<Nivel> niveles, String mensaje,
                   List<String> avisos, String resumen, String tarjeta) {

    public static final String PATRON = "V13b";

    /** @param quePide lo que pide un reto de ese nivel, en una línea */
    public record Nivel(String clave, String nombre, String quePide, String estado, String claseChip, int aciertos, int meta, int intentos) {
        public int barra() {
            return Math.min(aciertos, meta);
        }
    }

    public V13b {
        niveles = List.copyOf(niveles);
        avisos = List.copyOf(avisos);
    }

    public String idRaiz() {
        return "res-" + idEjecucion.map(UUID::toString).orElse(PATRON + "-" + sufijo);
    }

    public String idNivel(Nivel n) {
        return idRaiz() + "-" + n.clave();
    }

    public boolean lectura() {
        return modo == Modo.LECTURA;
    }
}
