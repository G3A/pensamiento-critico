package pensamiento.nucleo;

import java.util.Optional;
import java.util.UUID;

/**
 * Una evidencia guardada (tabla evidencia) con la ficha de su fuente: el pasaje copiado tal cual, su postura frente a la
 * afirmación, la fuerza que le dio R01 al registrarla y quién la etiquetó. Lo etiquetado por el modelo sin adoptar no cuenta.
 *
 * @param fragmentoId el fragmento de la biblioteca de donde salió el pasaje, si salió de ahí
 */
public record EvidenciaGuardada(UUID id, UUID afirmacionId, FichaFuente fuente, Optional<UUID> fragmentoId, String pasaje, Evidencia.Postura postura,
                                int fuerza, Evidencia.EtiquetadaPor etiquetadaPor, boolean adoptada) {

    public EvidenciaGuardada {
        if (pasaje == null || pasaje.isBlank()) {
            throw new IllegalArgumentException("Una evidencia necesita el pasaje copiado tal cual");
        }
        if (fuerza < 0 || fuerza > 8) {
            throw new IllegalArgumentException("La fuerza de una evidencia va de 0 a 8 (R01)");
        }
        if (etiquetadaPor == Evidencia.EtiquetadaPor.USUARIO && !adoptada) {
            throw new IllegalArgumentException("Lo que etiqueta la persona cuenta desde el principio");
        }
    }

    /** La evidencia que R02 y R03 evalúan. */
    public Evidencia comoEvidencia() {
        return new Evidencia(id, afirmacionId, fuente.comoFuente(), pasaje, postura, fuerza, etiquetadaPor, adoptada);
    }
}
