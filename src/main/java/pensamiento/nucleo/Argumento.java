package pensamiento.nucleo;

import java.util.List;
import java.util.UUID;

/**
 * Conjunto de premisas que apoyan o atacan una conclusión, con peso, sentido y estándar de prueba.
 * Es la unidad que R04 evalúa y que el mapa dibuja.
 */
public record Argumento(UUID id, UUID conclusionId, List<Premisa> premisas, int peso, Sentido sentido) {

    public record Premisa(UUID afirmacionId, int orden, boolean asumible) {
    }

    public enum Sentido { PRO, CONTRA }

    public Argumento {
        premisas = List.copyOf(premisas);
        if (peso < 0) {
            throw new IllegalArgumentException("El peso de un argumento no puede ser negativo");
        }
    }
}
