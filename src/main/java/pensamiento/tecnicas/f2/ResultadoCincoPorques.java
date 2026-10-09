package pensamiento.tecnicas.f2;

import java.util.List;
import java.util.UUID;

/**
 * Valor que pinta el patrón V06 (árbol o cadena) para T09 · 5 porqués: el problema, cada porqué con su nivel, a qué
 * responde, su evidencia y su estado (causa raíz, sin terminar o intermedio), los avisos y los conteos.
 *
 * @param problemaId la afirmación del problema (id del nodo raíz en el SVG)
 */
public record ResultadoCincoPorques(String problema, UUID problemaId, List<Porque> porques, int niveles, int conEvidencia, int causasRaiz,
                                   int sinTerminar, List<String> avisos, String resumen) {

    public enum Estado {
        INTERMEDIO("intermedio"), CAUSA_RAIZ("causa raíz"), SIN_TERMINAR("sin terminar");

        private final String texto;

        Estado(String texto) {
            this.texto = texto;
        }

        public String texto() {
            return texto;
        }

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    /**
     * @param padre el código del porqué al que responde; nulo si responde al problema
     */
    public record Porque(String codigo, String texto, String evidencia, String padre, int nivel, Estado estado, UUID afirmacionId) {
    }

    public ResultadoCincoPorques {
        porques = List.copyOf(porques);
        avisos = List.copyOf(avisos);
    }
}
