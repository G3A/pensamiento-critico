package pensamiento.ia;

import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

import pensamiento.nucleo.puertos.IaNoDisponible;

/** Una llamada a Ollama a la vez (OLLAMA_NUM_PARALLEL=1): el resto espera su turno. */
public final class SemaforoIa {

    private final Semaphore permisos;

    public SemaforoIa(int permisos) {
        this.permisos = new Semaphore(permisos, true);
    }

    public <T> T conPermiso(Supplier<T> accion) {
        try {
            permisos.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IaNoDisponible("Interrumpido esperando turno para Ollama", e);
        }
        try {
            return accion.get();
        } finally {
            permisos.release();
        }
    }
}
