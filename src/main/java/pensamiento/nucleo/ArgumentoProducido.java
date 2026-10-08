package pensamiento.nucleo;

import java.util.Optional;

/**
 * Un argumento que produce una ejecución, listo para la tabla argumento y sus premisas en premisa_argumento.
 * Conclusión y premisas son afirmaciones de la misma ejecución (AfirmacionConRol).
 *
 * @param esquemaId    el esquema de Walton que instancia, si se sabe
 * @param textoArgdown el texto del mapa en el subconjunto Argdown, si la técnica lo produce
 */
public record ArgumentoProducido(Argumento argumento, EstandarPrueba estandar, Optional<String> esquemaId, Optional<String> textoArgdown) {
}
