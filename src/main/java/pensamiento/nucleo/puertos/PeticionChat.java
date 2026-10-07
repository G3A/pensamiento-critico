package pensamiento.nucleo.puertos;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Petición de chat. El validador decide si la respuesta completa sirve (por ejemplo, "termina en
 * pregunta"); si no, la implementación reintenta hasta reintentosMaximos y luego lanza IaRespuestaInvalida.
 */
public record PeticionChat(
        List<Mensaje> mensajes,
        Duration tiempoMaximo,
        Optional<Predicate<String>> validador,
        int reintentosMaximos) {

    public PeticionChat {
        mensajes = List.copyOf(mensajes);
        if (mensajes.isEmpty()) {
            throw new IllegalArgumentException("Una petición de chat necesita al menos un mensaje");
        }
        if (reintentosMaximos < 0) {
            throw new IllegalArgumentException("Los reintentos no pueden ser negativos");
        }
    }

    public static PeticionChat simple(String pregunta, Duration tiempoMaximo) {
        return new PeticionChat(List.of(Mensaje.usuario(pregunta)), tiempoMaximo, Optional.empty(), 0);
    }
}
