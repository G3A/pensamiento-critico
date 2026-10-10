package pensamiento.tecnicas.f8;

import java.util.List;

/**
 * Valor que pinta el patrón V02 (lista de verificación con estado) para T47 · Reflexión estructurada: una fila por pregunta
 * activa, respondida o pendiente, la nota de si es obligatoria al cerrar y el resumen.
 */
public record ResultadoReflexion(String sobre, List<Item> items, int respondidas, String nota, String resumen) {

    /** @param respuesta nula si está pendiente */
    public record Item(EjecutorReflexion.Pregunta pregunta, String texto, String respuesta) {
        public boolean respondida() {
            return respuesta != null;
        }
    }

    public ResultadoReflexion {
        items = List.copyOf(items);
    }
}
