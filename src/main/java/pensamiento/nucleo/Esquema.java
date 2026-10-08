package pensamiento.nucleo;

import java.util.List;
import java.util.Optional;

/**
 * Esquema de argumentación de Walton con sus preguntas críticas (Walton, Reed y Macagno 2008). Catálogo único:
 * cada pregunta lleva la etiqueta de falacia que recibe su fallo (R06) y qué haría falta para responderla.
 */
public record Esquema(String id, String nombre, String descripcion, List<PreguntaCritica> preguntas, String origen) {

    /**
     * @param numero        desde 1, en el orden del catálogo
     * @param falacia       la etiqueta que cuenta solo cuando la persona confirma el fallo
     * @param comoResponder qué haría falta para responder la pregunta (corrección 13)
     */
    public record PreguntaCritica(int numero, String texto, String falacia, String comoResponder) {
    }

    public Esquema {
        preguntas = List.copyOf(preguntas);
    }

    public Optional<PreguntaCritica> pregunta(int numero) {
        return preguntas.stream().filter(p -> p.numero() == numero).findFirst();
    }
}
