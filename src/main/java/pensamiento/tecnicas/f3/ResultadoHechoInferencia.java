package pensamiento.tecnicas.f3;

import java.util.List;

import pensamiento.nucleo.Propuesta;

/**
 * Valor que pinta el patrón V05 (texto propio marcado) para T17 · Hecho, inferencia, juicio: cada oración con su tipo,
 * su grupo de Hayakawa y qué se puede hacer con ella (verificarla, buscarle evidencia o reconocer que no es verificable).
 *
 * @param texto las oraciones unidas por un espacio, para pintar cada una marcada en su lugar
 */
public record ResultadoHechoInferencia(String texto, List<OracionEtiquetada> oraciones, List<Propuesta> propuestas, String resumen) {

    /**
     * @param tipo   el tipo de afirmación en minúscula ("dato_estadistico"); nulo si está sin etiquetar
     * @param grupo  "hecho", "inferencia" o "juicio"; nulo si está sin etiquetar
     * @param linea  "Oración 1 · dato estadístico (hecho): puede verificarse."
     * @param inicio posición en el texto unido
     */
    public record OracionEtiquetada(int numero, String texto, String tipo, String grupo, String linea, boolean delModelo, int inicio, int fin) {
    }

    public ResultadoHechoInferencia {
        oraciones = List.copyOf(oraciones);
        propuestas = propuestas == null ? List.of() : List.copyOf(propuestas);
    }
}
