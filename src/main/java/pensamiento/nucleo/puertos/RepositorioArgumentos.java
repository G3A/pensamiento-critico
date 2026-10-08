package pensamiento.nucleo.puertos;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import pensamiento.nucleo.ArgumentoProducido;

/**
 * Argumentos de cada persona (tablas argumento y premisa_argumento), ligados a la ejecución que los produjo.
 * Todo método recibe el usuario de la sesión y solo devuelve lo suyo; la implementación real además corre
 * bajo RLS. Guardar va en la misma transacción que la ejecución, que ya debe existir.
 */
public interface RepositorioArgumentos {

    /** Un argumento guardado: su ejecución, su posición en ella y lo que la técnica produjo. */
    record ArgumentoGuardado(UUID ejecucionId, int orden, ArgumentoProducido argumento) {
    }

    /**
     * Guarda los argumentos de la ejecución en el orden de la lista. Volver a guardar los mismos identificadores
     * no duplica nada. Las afirmaciones de conclusión y premisas ya deben estar guardadas.
     */
    void guardar(UUID usuarioId, UUID institucionId, UUID ejecucionId, List<ArgumentoProducido> argumentos);

    /** Los argumentos de una ejecución en su orden, con sus premisas en orden; vacía si la ejecución no es del usuario. */
    List<ArgumentoGuardado> deEjecucion(UUID usuarioId, UUID ejecucionId);

    /** Vacío si no existe o si es de otra persona: para quien pregunta, es lo mismo. */
    Optional<ArgumentoGuardado> porId(UUID usuarioId, UUID argumentoId);
}
