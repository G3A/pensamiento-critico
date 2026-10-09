package pensamiento.nucleo.puertos;

import java.time.Instant;

import pensamiento.nucleo.Trabajo;

/**
 * Lo que hace un tipo de trabajo largo (indexar o vectorizar un documento). El ejecutor de trabajos toma uno de la cola y le
 * aplica el desenlace que devuelve el procesador: hecho, reintentar más tarde o falla.
 */
public interface ProcesadorTrabajo {

    /** Cómo terminó un trabajo. */
    sealed interface Desenlace permits Hecho, Reintentar, Falla {
    }

    record Hecho() implements Desenlace {
    }

    /** Vuelve a la cola desde {@code desde}, con el motivo a la vista. */
    record Reintentar(Instant desde, String motivo) implements Desenlace {
    }

    /** No se vuelve a intentar. */
    record Falla(String motivo) implements Desenlace {
    }

    /** El tipo de trabajo que procesa ("indexar", "vectorizar"). */
    String tipo();

    /** Procesa el trabajo. Nunca lanza por un fallo esperable: lo devuelve como desenlace. */
    Desenlace procesar(Trabajo trabajo);
}
