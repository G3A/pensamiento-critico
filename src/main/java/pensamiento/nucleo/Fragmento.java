package pensamiento.nucleo;

import java.util.Optional;
import java.util.UUID;

/**
 * Un trozo citable de un documento de la biblioteca (tabla fragmento): el texto tal cual salió del documento, su orden y,
 * en un PDF, su página. En un CSV, cada fila con su encabezado.
 */
public record Fragmento(UUID id, UUID documentoId, int orden, String texto, Optional<Integer> pagina) {

    /** Lo que el troceado produce antes de guardarse. */
    public record Nuevo(int orden, String texto, Optional<Integer> pagina) {
        public Nuevo {
            if (texto == null || texto.isBlank()) {
                throw new IllegalArgumentException("Un fragmento no puede estar vacío");
            }
        }
    }
}
