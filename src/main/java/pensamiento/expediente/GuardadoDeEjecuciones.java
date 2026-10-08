package pensamiento.expediente;

import org.springframework.stereotype.Service;

import pensamiento.nucleo.Ejecucion;
import pensamiento.nucleo.Resultado;
import pensamiento.nucleo.puertos.RepositorioArgumentos;
import pensamiento.nucleo.puertos.RepositorioEjecucion;

/**
 * Guarda una ejecución con todo lo que su resultado declara: afirmaciones con rol, pendientes y argumentos con
 * sus premisas. La transacción corta la abre quien llama, así que todo queda o nada queda. Idempotente: si la
 * clave ya existía (doble clic), devuelve la ejecución de antes y no escribe argumentos nuevos.
 */
@Service
public class GuardadoDeEjecuciones {

    private final RepositorioEjecucion ejecuciones;
    private final RepositorioArgumentos argumentos;

    public GuardadoDeEjecuciones(RepositorioEjecucion ejecuciones, RepositorioArgumentos argumentos) {
        this.ejecuciones = ejecuciones;
        this.argumentos = argumentos;
    }

    public Ejecucion guardar(Ejecucion nueva, Resultado<?> resultado) {
        Ejecucion guardada = ejecuciones.guardar(nueva, resultado.afirmaciones(), resultado.pendientes());
        if (guardada.id().equals(nueva.id()) && !resultado.argumentos().isEmpty()) {
            argumentos.guardar(nueva.usuarioId(), nueva.institucionId(), guardada.id(), resultado.argumentos());
        }
        return guardada;
    }
}
