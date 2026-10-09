package pensamiento.tecnicas.f6;

import java.util.List;

/**
 * Valor que pinta el patrón V10 (tarjeta de veredicto con barras de puntaje) para T37 · Test de Turing ideológico: el
 * puntaje de la rúbrica en código, el de cada criterio, las señales y el veredicto contra el umbral. El modelo no puntúa.
 *
 * @param omision      el puntaje de omisión; nulo si no se mide (sin argumentos de referencia)
 * @param argumentos   cada argumento de referencia, cubierto u omitido
 */
public record ResultadoTuring(String postura, String texto, String steelman, int puntaje, int umbral, boolean aprueba, int caricatura, int pesoCaricatura,
                              Integer omision, int pesoOmision, int tono, int pesoTono, List<String> caricaturas, List<String> burlas,
                              List<Argumento> argumentos, String motivo, String resumen) {

    /** @param clave la clave que se encontró; nula si está omitido */
    public record Argumento(String texto, boolean cubierto, String clave) {
    }

    public ResultadoTuring {
        caricaturas = List.copyOf(caricaturas);
        burlas = List.copyOf(burlas);
        argumentos = List.copyOf(argumentos);
    }
}
