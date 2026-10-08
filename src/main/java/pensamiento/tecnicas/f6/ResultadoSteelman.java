package pensamiento.tecnicas.f6;

import java.util.List;
import java.util.UUID;

import pensamiento.nucleo.Propuesta;

/**
 * Valor que pinta el patrón V04 (dos columnas comparativas) para T34 · Steelmanning: a la izquierda la postura
 * contraria como la escribió la persona y su cita; a la derecha el steelman vigente y lo que agrega. Nunca dice
 * "correcto": dice qué pregunta quedó sin responder (corrección 13).
 *
 * @param steelman     el steelman vigente; nulo si todavía no hay
 * @param origen       "usuario" o "modelo" (una propuesta adoptada)
 * @param propuestas   las propuestas del modelo, adoptadas o no, para mostrarlas con su estado
 * @param afirmacionId la afirmación guardada para el steelman, si hay
 */
public record ResultadoSteelman(String posturaOriginal, String cita, String steelman, String origen, int palabras, int maximo,
                                List<String> razones, List<String> preguntas, Estado estado, List<Propuesta> propuestas,
                                String resumen, UUID afirmacionId) {

    public enum Estado {
        INCOMPLETO("incompleto"), POR_CONFIRMAR("por confirmar");

        private final String texto;

        Estado(String texto) {
            this.texto = texto;
        }

        public String texto() {
            return texto;
        }

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    public ResultadoSteelman {
        razones = razones == null ? List.of() : List.copyOf(razones);
        preguntas = preguntas == null ? List.of() : List.copyOf(preguntas);
        propuestas = propuestas == null ? List.of() : List.copyOf(propuestas);
    }

    public boolean tieneSteelman() {
        return steelman != null;
    }

    public boolean delModelo() {
        return "modelo".equals(origen);
    }
}
