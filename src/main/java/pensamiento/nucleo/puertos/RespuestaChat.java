package pensamiento.nucleo.puertos;

/** Respuesta completa de un chat con el registro de reproducibilidad (modelo y digest) y los intentos usados. */
public record RespuestaChat(String texto, String modelo, String digest, int intentos) {
}
