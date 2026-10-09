package pensamiento.nucleo;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * El estado de la ficha de verificación de una afirmación (tabla verificacion, P10): qué preguntas críticas marcó la persona
 * como respondidas. La afirmación lleva su tipo, estado, fuerza neta y confianza; las evidencias, en su tabla.
 *
 * @param actualizadaEn vacío si la ficha nunca se guardó
 */
public record Verificacion(UUID afirmacionId, List<String> preguntasRespondidas, Optional<Instant> actualizadaEn) {

    public Verificacion {
        preguntasRespondidas = List.copyOf(preguntasRespondidas);
    }

    public static Verificacion nueva(UUID afirmacionId) {
        return new Verificacion(afirmacionId, List.of(), Optional.empty());
    }

    /** El veredicto que se guarda en la afirmación: estado de R03, fuerza neta de R02 y la confianza declarada. */
    public record Veredicto(TipoAfirmacion tipo, EstadoAfirmacion estado, int fuerzaNeta, Optional<Integer> confianza) {
        public Veredicto {
            if (confianza.isPresent() && (confianza.get() < 0 || confianza.get() > 100)) {
                throw new IllegalArgumentException("La confianza va de 0 a 100");
            }
        }
    }

    /**
     * La ejecución que produjo la afirmación: la ficha guarda su veredicto en el mismo expediente.
     *
     * @param expedienteId vacío si esa ejecución no está en un expediente
     */
    public record Origen(UUID ejecucionId, IdTecnica tecnica, Optional<UUID> expedienteId) {
    }
}
