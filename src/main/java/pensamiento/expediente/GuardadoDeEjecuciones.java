package pensamiento.expediente;

import org.springframework.stereotype.Service;

import pensamiento.nucleo.Ejecucion;
import pensamiento.nucleo.Resultado;
import pensamiento.nucleo.puertos.RepositorioArgumentos;
import pensamiento.nucleo.puertos.RepositorioCambiosOpinion;
import pensamiento.nucleo.puertos.RepositorioEjecucion;
import pensamiento.nucleo.puertos.RepositorioEvidencias;
import pensamiento.nucleo.puertos.RepositorioPredicciones;

/**
 * Guarda una ejecución con todo lo que su resultado declara: afirmaciones con rol, pendientes, argumentos con sus
 * premisas, predicciones con su confianza (T32), cambios de opinión (R05) y evidencias con su fuente (T22). La transacción corta la abre quien llama,
 * así que todo queda o nada queda. Idempotente: si la clave ya existía (doble clic), devuelve la ejecución de antes y no
 * escribe nada más.
 */
@Service
public class GuardadoDeEjecuciones {

    private final RepositorioEjecucion ejecuciones;
    private final RepositorioArgumentos argumentos;
    private final RepositorioPredicciones predicciones;
    private final RepositorioCambiosOpinion cambios;
    private final RepositorioEvidencias evidencias;

    public GuardadoDeEjecuciones(RepositorioEjecucion ejecuciones, RepositorioArgumentos argumentos, RepositorioPredicciones predicciones,
                                 RepositorioCambiosOpinion cambios, RepositorioEvidencias evidencias) {
        this.ejecuciones = ejecuciones;
        this.argumentos = argumentos;
        this.predicciones = predicciones;
        this.cambios = cambios;
        this.evidencias = evidencias;
    }

    public Ejecucion guardar(Ejecucion nueva, Resultado<?> resultado) {
        Ejecucion guardada = ejecuciones.guardar(nueva, resultado.afirmaciones(), resultado.pendientes());
        if (!guardada.id().equals(nueva.id())) {
            return guardada;
        }
        if (!resultado.argumentos().isEmpty()) {
            argumentos.guardar(nueva.usuarioId(), nueva.institucionId(), guardada.id(), resultado.argumentos());
        }
        if (!resultado.predicciones().isEmpty()) {
            predicciones.guardar(nueva.usuarioId(), nueva.institucionId(), guardada.id(), resultado.predicciones());
        }
        if (!resultado.cambios().isEmpty()) {
            cambios.guardar(nueva.usuarioId(), nueva.institucionId(), guardada.id(), resultado.cambios(), nueva.creadaEn());
        }
        for (pensamiento.nucleo.EvidenciaGuardada e : resultado.evidencias()) {
            evidencias.guardar(nueva.usuarioId(), nueva.institucionId(), e);
        }
        return guardada;
    }
}
