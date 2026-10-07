package pensamiento.web.patrones;

import java.util.Optional;
import java.util.UUID;

import gg.jte.Content;

/**
 * Presentación por patrón, fuera del ejecutor (sección 4): cada patrón pinta su valor en modo completo o lectura.
 *
 * @param <R> el valor del resultado de las técnicas que usan este patrón
 */
public interface RenderizadorResultado<R> {

    String patron();

    Class<R> tipo();

    /** El fragmento con raíz id="res-{idEjecucion}" y data-patron; sufijo distingue los fragmentos sin guardar. */
    Content render(Optional<UUID> idEjecucion, String sufijo, R valor, Modo modo);
}
