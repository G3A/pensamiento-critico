package pensamiento.tecnicas.f2;

import java.util.List;
import java.util.UUID;

/**
 * Valor que pinta el patrón V13a (lista enlazada) para T11 · Falsación y "qué tendría que ser cierto": las condiciones
 * con su estado, lo que haría cambiar de opinión y cuántas quedan como pendientes de verificación.
 */
public record ResultadoFalsacion(String postura, List<Condicion> condiciones, String cambiaria, List<String> avisos, List<String> acciones,
                                 int verificables, String resumen) {

    /** @param estado "verificable", "verificable · falta cómo" o "no verificable" */
    public record Condicion(String codigo, String texto, String estado, String como, UUID afirmacionId) {
    }

    public ResultadoFalsacion {
        condiciones = List.copyOf(condiciones);
        avisos = List.copyOf(avisos);
        acciones = List.copyOf(acciones);
    }
}
