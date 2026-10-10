package pensamiento.web.patrones;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Patrón V13c, calendario (T49 · Repetición espaciada y el calendario del Dojo). Record tipado de tag/v/v13c.jte: raíz
 * id="res-{idEjecucion}" y data-patron="V13c". Los 7 días desde hoy con cuántos repasos tocan (hoy, con los atrasados) y la
 * lista de conceptos con su próximo repaso en palabras.
 */
public record V13c(Optional<UUID> idEjecucion, String sufijo, Modo modo, String titulo, List<Dia> dias, List<Concepto> conceptos, List<String> avisos,
                   String resumen, String tarjeta) {

    public static final String PATRON = "V13c";

    /** @param fecha AAAA-MM-DD; @param nombre "mié 7" */
    public record Dia(String fecha, String nombre, int repasos, boolean hoy) {
    }

    /** @param detalle "4 repasos · facilidad 2,48 · intervalo 6 días"; @param cuando "en 5 días", "atrasado 3 días" */
    public record Concepto(String nombre, String tema, String detalle, String cuando, boolean toca) {
    }

    public V13c {
        dias = List.copyOf(dias);
        conceptos = List.copyOf(conceptos);
        avisos = List.copyOf(avisos);
    }

    public String idRaiz() {
        return "res-" + idEjecucion.map(UUID::toString).orElse(PATRON + "-" + sufijo);
    }

    public boolean lectura() {
        return modo == Modo.LECTURA;
    }
}
