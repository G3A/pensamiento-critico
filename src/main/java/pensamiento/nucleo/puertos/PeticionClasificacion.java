package pensamiento.nucleo.puertos;

import java.time.Duration;
import java.util.List;

/** Clasificar un texto contra una lista cerrada de etiquetas, con salida estructurada. */
public record PeticionClasificacion(String instruccion, String texto, List<String> etiquetas, Duration tiempoMaximo) {

    public PeticionClasificacion {
        etiquetas = List.copyOf(etiquetas);
        if (etiquetas.isEmpty()) {
            throw new IllegalArgumentException("Una clasificación necesita al menos una etiqueta");
        }
    }
}
