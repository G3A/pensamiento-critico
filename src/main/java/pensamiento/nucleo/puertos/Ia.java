package pensamiento.nucleo.puertos;

import java.util.List;
import java.util.function.Consumer;

/**
 * Puerto de la IA local (Ollama). Tres capacidades: chat con streaming, clasificación con salida
 * estructurada y embeddings. En el hito 0 ninguna técnica lo usa; existe el puerto, su Fake y el contrato.
 * Toda implementación lanza las mismas excepciones de dominio ante los mismos fallos (ver IaContract).
 */
public interface Ia {

    /** Disponibilidad y modelos listados. Nunca lanza: si no responde, devuelve no disponible. */
    EstadoIa estado();

    /**
     * Chat con streaming: cada token llega a {@code alRecibirToken} como texto provisional y la
     * respuesta completa se devuelve al final, validada. Si la petición trae validador y la respuesta
     * no lo pasa, se reintenta hasta {@code reintentosMaximos}; si sigue fallando, IaRespuestaInvalida.
     */
    RespuestaChat chat(PeticionChat peticion, Consumer<String> alRecibirToken);

    /** Clasificación contra un enum cerrado de etiquetas, con el "por qué" para que el usuario juzgue. */
    Clasificacion clasificar(PeticionClasificacion peticion);

    /** Embeddings de cada texto, en orden. */
    List<float[]> incrustar(PeticionEmbeddings peticion);
}
