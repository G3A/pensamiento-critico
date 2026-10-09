package pensamiento.tecnicas.f6;

import java.util.List;

import pensamiento.nucleo.Propuesta;

/**
 * Valor que pinta el patrón V09 (transcripción con panel lateral) para T36 · Equipo rojo / abogado del diablo: cada ataque
 * con su respuesta y, al lado, la debilidad que el código identificó en cada razón.
 */
public record ResultadoEquipoRojo(String postura, String modo, List<Debilidad> debilidades, List<Ataque> ataques, int respondidos,
                                  int sinResponder, List<Propuesta> propuestas, String resumen) {

    /**
     * La debilidad de una razón.
     *
     * @param esquema   identificador del esquema de Walton; nulo si la razón quedó sin esquema
     * @param deDonde   "apoyo: una causa" o "detectado por las reglas: «…»"
     * @param preguntas las preguntas críticas usadas, en el orden de los ataques
     */
    public record Debilidad(String razon, String texto, String esquema, String esquemaNombre, String deDonde, List<Pregunta> preguntas) {
        public Debilidad {
            preguntas = List.copyOf(preguntas);
        }
    }

    /** @param falacia la etiqueta de R06 si queda sin respuesta (la confirma la persona en T13) */
    public record Pregunta(int numero, String texto, String falacia) {
    }

    /**
     * @param razon   la razón que ataca ("R1")
     * @param origen  "banco", "modelo" o "persona" (escrito a mano)
     * @param estado  "respondido" o "sin responder"
     */
    public record Ataque(String codigo, String razon, String esquema, int pregunta, String texto, String origen, String respuesta, String estado) {
        public boolean respondido() {
            return "respondido".equals(estado);
        }
    }

    public ResultadoEquipoRojo {
        debilidades = List.copyOf(debilidades);
        ataques = List.copyOf(ataques);
        propuestas = propuestas == null ? List.of() : List.copyOf(propuestas);
    }
}
