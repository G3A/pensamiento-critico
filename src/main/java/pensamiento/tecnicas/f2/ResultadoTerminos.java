package pensamiento.tecnicas.f2;

import java.util.List;

/**
 * Valor que pinta el patrón V05 (texto propio marcado) para T12 · Definición de términos y detección de ambigüedad: el
 * texto con cada término marcado en su primera aparición y una nota por término con su estado y su definición.
 *
 * @param terminos los términos marcados, en orden de aparición
 * @param sueltos  los términos que la persona escribió y no aparecen en el texto (quedan en el glosario)
 */
public record ResultadoTerminos(String texto, List<Termino> terminos, List<Termino> sueltos, int ambiguos, int definidos, List<String> avisos,
                                String resumen) {

    /**
     * @param codigo T1, T2… en el orden del texto; vacío en los sueltos
     * @param inicio posición de la primera aparición en el texto; -1 en los sueltos
     * @param origen "lista" si lo detectó la lista de términos difusos, "persona" si solo lo escribió la persona
     * @param estado "definido", "falta ejemplo o contraejemplo" o "sin definir"
     */
    public record Termino(String codigo, String termino, int inicio, int fin, String origen, String estado, String definicion, String ejemplo,
                          String contraejemplo) {
    }

    public ResultadoTerminos {
        terminos = List.copyOf(terminos);
        sueltos = List.copyOf(sueltos);
        avisos = List.copyOf(avisos);
    }
}
