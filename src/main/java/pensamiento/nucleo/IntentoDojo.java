package pensamiento.nucleo;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Un reto respondido en el Dojo de razonamiento (tabla intento_dojo, solo inserción). Es la única fuente del Dojo: el próximo
 * repaso (T49 · Repetición espaciada) y el nivel de Bloom (T48 · Taxonomía de Bloom) se calculan con reglas sobre los intentos.
 *
 * @param clave    la del formulario: responder dos veces el mismo formulario guarda un solo intento
 * @param concepto el del banco, por ejemplo "T13:generalizacion"
 * @param nivel    el nivel del reto respondido
 * @param dia      el día según el reloj de la app, en su zona
 */
public record IntentoDojo(UUID id, String clave, String retoId, IdTecnica tecnica, String concepto, NivelBloom nivel, String respuesta,
                          boolean acierto, LocalDate dia, Instant creadoEn) {

    public IntentoDojo {
        if (clave == null || clave.isBlank() || retoId == null || retoId.isBlank() || concepto == null || concepto.isBlank()) {
            throw new IllegalArgumentException("Un intento necesita su clave, su reto y su concepto");
        }
        respuesta = respuesta == null ? "" : respuesta;
    }
}
