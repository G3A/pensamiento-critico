package pensamiento.tecnicas.f2;

import java.util.List;
import java.util.UUID;

import pensamiento.nucleo.Propuesta;

/**
 * Valor que pinta el patrón V09 (transcripción con panel lateral) para T08 · Preguntas socráticas: los turnos con lo que
 * eligió el motor y la respuesta, lo que sigue (otro turno o el cierre) y, al lado, los ocho elementos de Paul-Elder y los
 * estándares puntuados por reglas. Nunca un puntaje global ni un veredicto.
 *
 * @param siguiente      el próximo turno; nulo si toca el cierre
 * @param cierre         la pregunta de cierre, con la postura
 * @param tocaCierre     si ya toca el cierre
 * @param respuestaCierre la respuesta al cierre; nula si no hay
 * @param cambio         la línea del cambio de confianza; nula si no hubo
 * @param posturaId      la afirmación de la postura
 */
public record ResultadoPreguntasSocraticas(String postura, String modoSesion, List<Turno> turnos, Turno siguiente, String cierre,
                                           boolean tocaCierre, String respuestaCierre, List<ElementoPanel> elementos, int llenos,
                                           List<EstandarPanel> estandares, String cambio, List<Propuesta> propuestas, String resumen,
                                           UUID posturaId) {

    /**
     * Un turno, respondido o por responder.
     *
     * @param tipo      identificador del tipo socrático ("evidencia")
     * @param tipoNombre cómo se lee ("pregunta sobre la pregunta")
     * @param elemento  identificador del elemento ("informacion")
     * @param marca     lo que el motor encontró para la rama; nula en la general
     * @param origen    "banco" o "modelo"
     * @param respuesta lo que respondió la persona; nula en el turno que sigue
     */
    public record Turno(int numero, String tipo, String tipoNombre, String elemento, String elementoNombre, String rama, String marca,
                        String porque, String pregunta, String origen, String respuesta) {
    }

    /** @param estado "lleno", "pendiente" o "sin preguntar" */
    public record ElementoPanel(String id, String nombre, String estado, String texto, String origen) {
        public boolean lleno() {
            return "lleno".equals(estado);
        }
    }

    /** @param estado "cumple", "a medias" o "falta"; pregunta: la de T04 si no cumple */
    public record EstandarPanel(String id, String nombre, int puntaje, String estado, String motivo, String pregunta) {
    }

    public ResultadoPreguntasSocraticas {
        turnos = List.copyOf(turnos);
        elementos = List.copyOf(elementos);
        estandares = List.copyOf(estandares);
        propuestas = propuestas == null ? List.of() : List.copyOf(propuestas);
    }
}
