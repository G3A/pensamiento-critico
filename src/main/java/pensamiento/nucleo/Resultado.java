package pensamiento.nucleo;

import java.util.List;
import java.util.Optional;

/**
 * Lo que devuelve un ejecutor (sección 4 del documento).
 *
 * @param versionEsquema  versión del esquema con que se produjo
 * @param valor           lo que el renderizador del patrón pinta
 * @param afirmaciones    consumidas y producidas, con origen
 * @param pendientes      tipos cerrados: revisión, verificación, objeción, repaso
 * @param resumen         una línea para Expediente e Inicio
 * @param argumentos      argumentos producidos sobre esas afirmaciones (tabla argumento); vacía si la técnica no arma argumentos
 * @param modelo          modelo, digest, prompt, temperatura y semilla si la entrada trae propuestas del modelo (RNF-07)
 * @param bloqueoGuardado si está presente, guardar responde con este motivo y no guarda nada (T16)
 */
public record Resultado<R>(
        int versionEsquema,
        R valor,
        List<AfirmacionConRol> afirmaciones,
        List<Pendiente> pendientes,
        String resumen,
        List<ArgumentoProducido> argumentos,
        Optional<Ejecucion.RegistroModelo> modelo,
        Optional<String> bloqueoGuardado) {

    public Resultado {
        afirmaciones = List.copyOf(afirmaciones);
        pendientes = List.copyOf(pendientes);
        argumentos = List.copyOf(argumentos);
    }

    public Resultado(int versionEsquema, R valor, List<AfirmacionConRol> afirmaciones, List<Pendiente> pendientes, String resumen,
                     List<ArgumentoProducido> argumentos) {
        this(versionEsquema, valor, afirmaciones, pendientes, resumen, argumentos, Optional.empty(), Optional.empty());
    }

    public Resultado(int versionEsquema, R valor, List<AfirmacionConRol> afirmaciones, List<Pendiente> pendientes, String resumen) {
        this(versionEsquema, valor, afirmaciones, pendientes, resumen, List.of());
    }

    /** El registro del modelo de la primera propuesta, si la entrada trae alguna. */
    public static Optional<Ejecucion.RegistroModelo> registroDe(List<Propuesta> propuestas) {
        return propuestas.stream().filter(p -> !p.modelo().isBlank()).findFirst().map(Propuesta::registro);
    }
}
