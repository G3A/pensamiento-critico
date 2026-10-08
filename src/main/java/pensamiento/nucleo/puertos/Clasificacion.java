package pensamiento.nucleo.puertos;

/**
 * Etiqueta elegida (siempre una de las pedidas), el "por qué" que el usuario juzga y el registro de
 * reproducibilidad de quien clasificó: modelo y digest (RNF-07).
 */
public record Clasificacion(String etiqueta, String porQue, String modelo, String digest) {
}
