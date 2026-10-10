package pensamiento.nucleo;

import java.time.Instant;

/**
 * Dominio de una persona en un tema del Dojo (tabla competencia de V1, sección 5b): el nivel de Bloom actual, los intentos y
 * aciertos de todos los niveles y la última práctica. Es una proyección que el Dojo reescribe con cada intento.
 */
public record Competencia(IdTecnica tecnica, NivelBloom nivel, int intentos, int aciertos, Instant ultimaPractica) {

    public Competencia {
        if (intentos < 0 || aciertos < 0 || aciertos > intentos) {
            throw new IllegalArgumentException("Aciertos e intentos inválidos: " + aciertos + " de " + intentos);
        }
    }
}
