package pensamiento.tecnicas.f3;

import java.util.List;

import pensamiento.nucleo.Propuesta;

/**
 * Valor que pinta el patrón V04 (dos columnas) para T15 · Considera lo opuesto: a la izquierda la postura propia, a la
 * derecha las opuestas con qué cambiaría si fueran ciertas, y cómo se movió la confianza.
 *
 * @param faltantes "Falta 1 postura opuesta: …" si hay menos de las que pide la configuración; nulo si no
 */
public record ResultadoOpuesto(String postura, List<OpuestaEvaluada> opuestas, int pedidas, String faltantes, String confianza,
                               List<Propuesta> propuestas, String resumen) {

    /** @param falta la pregunta de "qué cambiaría" si no está; nula si la postura opuesta está completa */
    public record OpuestaEvaluada(String texto, String queCambiaria, String falta, boolean delModelo) {
    }

    public ResultadoOpuesto {
        opuestas = List.copyOf(opuestas);
        propuestas = propuestas == null ? List.of() : List.copyOf(propuestas);
    }
}
