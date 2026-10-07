package pensamiento.nucleo;

import java.util.UUID;

/** Un pasaje o dato que apoya, contradice o matiza una afirmación, con fuerza calculada por R01. */
public record Evidencia(
        UUID id,
        UUID afirmacionId,
        Fuente fuente,
        String pasaje,
        Postura postura,
        int fuerza,
        EtiquetadaPor etiquetadaPor,
        boolean adoptada) {

    public enum Postura { APOYA, CONTRADICE, MATIZA }

    public enum EtiquetadaPor { USUARIO, MODELO }

    /** Solo cuenta para R02, R03 y R04 lo que el usuario adoptó; lo del modelo sin adoptar no cuenta. */
    public boolean cuenta() {
        return adoptada;
    }
}
