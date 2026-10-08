package pensamiento.nucleo;

import java.util.List;

/**
 * Lo que devuelve un ejecutor (sección 4 del documento).
 *
 * @param versionEsquema versión del esquema con que se produjo
 * @param valor          lo que el renderizador del patrón pinta
 * @param afirmaciones   consumidas y producidas, con origen
 * @param pendientes     tipos cerrados: revisión, verificación, objeción, repaso
 * @param resumen        una línea para Expediente e Inicio
 * @param argumentos     argumentos producidos sobre esas afirmaciones (tabla argumento); vacía si la técnica no arma argumentos
 */
public record Resultado<R>(
        int versionEsquema,
        R valor,
        List<AfirmacionConRol> afirmaciones,
        List<Pendiente> pendientes,
        String resumen,
        List<ArgumentoProducido> argumentos) {

    public Resultado {
        afirmaciones = List.copyOf(afirmaciones);
        pendientes = List.copyOf(pendientes);
        argumentos = List.copyOf(argumentos);
    }

    public Resultado(int versionEsquema, R valor, List<AfirmacionConRol> afirmaciones, List<Pendiente> pendientes, String resumen) {
        this(versionEsquema, valor, afirmaciones, pendientes, resumen, List.of());
    }
}
