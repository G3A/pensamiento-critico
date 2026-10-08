package pensamiento.web.patrones;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Patrón V11, registro con línea de tiempo (T32 · Diario de decisiones; luego T45 y T46). Record tipado de tag/v/v11.jte:
 * raíz id="res-{idEjecucion}" y data-patron="V11". Arriba los campos del registro como lista de definiciones; abajo los
 * hitos con su fecha. El estado va con texto ("pendiente de revisión", "se cumplió").
 */
public record V11(Optional<UUID> idEjecucion, String sufijo, Modo modo, String titulo, List<Campo> campos, String estado, String claseEstado,
                  List<Hito> linea, String bloqueo, List<String> avisos, String resumen, String tarjeta) {

    public static final String PATRON = "V11";

    public record Campo(String nombre, String valor) {
    }

    /** @param fecha AAAA-MM-DD, para el atributo datetime; @param texto la fecha en palabras */
    public record Hito(String fecha, String texto, String que) {
    }

    public V11 {
        campos = List.copyOf(campos);
        linea = List.copyOf(linea);
        avisos = List.copyOf(avisos);
    }

    public String idRaiz() {
        return "res-" + idEjecucion.map(UUID::toString).orElse(PATRON + "-" + sufijo);
    }

    public boolean lectura() {
        return modo == Modo.LECTURA;
    }
}
