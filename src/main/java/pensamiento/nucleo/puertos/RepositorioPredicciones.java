package pensamiento.nucleo.puertos;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import pensamiento.nucleo.Prediccion;
import pensamiento.nucleo.PrediccionDeclarada;

/**
 * Las predicciones de cada persona con su confianza declarada (tabla prediccion, R05). Todo método recibe el usuario de
 * la sesión y solo devuelve lo suyo; la implementación real además corre bajo RLS. Guardar va en la misma transacción que
 * la ejecución, que ya debe existir con la afirmación de cada predicción. Una predicción resuelta es inmutable.
 */
public interface RepositorioPredicciones {

    /** Guarda las predicciones de la ejecución; volver a guardar los mismos identificadores no duplica ni cambia nada. */
    void guardar(UUID usuarioId, UUID institucionId, UUID ejecucionId, List<PrediccionDeclarada> predicciones);

    /** Vacío si no existe o si es de otra persona: para quien pregunta, es lo mismo. */
    Optional<Prediccion> porId(UUID usuarioId, UUID prediccionId);

    /** Todas las de la persona, de ejecuciones no borradas, por fecha de revisión y luego por identificador. */
    List<Prediccion> deUsuario(UUID usuarioId);

    /** Las de una ejecución, por identificador (uuidv7: el orden en que se declararon); vacía si la ejecución no es del usuario. */
    List<Prediccion> deEjecucion(UUID usuarioId, UUID ejecucionId);

    /**
     * Registra si se cumplió, con la fecha dada, y devuelve la predicción resuelta. Vacío si no existe o es de otra persona.
     *
     * @throws Prediccion.YaResuelta si ya estaba resuelta: no se modifica nada
     */
    Optional<Prediccion> resolver(UUID usuarioId, UUID prediccionId, boolean seCumplio, Instant cuando);
}
