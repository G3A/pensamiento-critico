package pensamiento.nucleo;

import java.util.Optional;
import java.util.UUID;

/**
 * Un resultado de buscar en la biblioteca: el texto literal de un fragmento con su documento y su página, nunca un resumen.
 *
 * @param puntaje similitud coseno de 0 a 1 en la búsqueda semántica; coincidencia de texto completo (ts_rank_cd) en la otra
 * @param modo    cómo se encontró
 */
public record Pasaje(UUID fragmentoId, UUID documentoId, String documento, Optional<Integer> pagina, String texto, double puntaje, Modo modo) {

    public enum Modo { SEMANTICA, TEXTO_COMPLETO }
}
