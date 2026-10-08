package pensamiento.web.taller;

import java.util.List;
import java.util.stream.Collectors;

import pensamiento.nucleo.Ejecucion;
import pensamiento.nucleo.Expediente;

/**
 * Lo que queda a la vista después de guardar en el Taller: las ejecuciones creadas (una por técnica) y el
 * formulario para asociarlas juntas a un expediente.
 *
 * @param citas      la cita de la técnica de cada ejecución, en el mismo orden
 * @param nuevaClave clave de idempotencia para el siguiente guardado; nula al solo asociar
 */
public record VistaGuardado(List<Ejecucion> ejecuciones, List<String> citas, List<Expediente> expedientes, String mensaje, String nuevaClave) {

    public String expedienteActual() {
        return ejecuciones.stream().map(e -> e.expedienteId().map(Object::toString).orElse("")).distinct().collect(Collectors.joining());
    }
}
