package pensamiento.web.patrones;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * El estado vigente de una predicción del Diario, para que el registro de T32 · Diario de decisiones se pinte al día
 * aunque el resultado guardado sea el del momento del registro: si ya se revisó, el renderizador suma el hito "resuelta".
 * La implementación lee la predicción de la persona de la sesión; fuera de una sesión (los ejemplos) no hay revisión.
 */
@FunctionalInterface
public interface EstadoDePrediccion {

    /** La predicción ya revisada: si se cumplió y el día en que se registró el resultado. */
    record Revision(boolean seCumplio, LocalDate fecha) {
    }

    Optional<Revision> de(UUID prediccionId);

    EstadoDePrediccion NINGUNO = id -> Optional.empty();
}
