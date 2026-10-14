package pensamiento.web.aula;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;
import java.util.function.Supplier;

import pensamiento.nucleo.puertos.Clasificacion;
import pensamiento.nucleo.puertos.EstadoIa;
import pensamiento.nucleo.puertos.Ia;
import pensamiento.nucleo.puertos.IaNoDisponible;
import pensamiento.nucleo.puertos.PeticionChat;
import pensamiento.nucleo.puertos.PeticionClasificacion;
import pensamiento.nucleo.puertos.PeticionEmbeddings;
import pensamiento.nucleo.puertos.RespuestaChat;

/**
 * Ollama en el aula (hito 10, entregable 9). Para un estudiante con su clase abierta, el modelo está apagado por defecto: el
 * estado dice "no disponible" y cada llamada falla con IaNoDisponible, así cada técnica sigue en modo plantillas como sin
 * Ollama. Si su docente lo encendió para el grupo, las llamadas pasan con un cupo de un turno por grupo: un grupo de 35 tiene a
 * lo sumo una llamada esperando en la cola de Ollama (que atiende una a la vez), y los demás grupos y personas no quedan detrás
 * de todo un curso. Fuera de una clase nada cambia.
 */
public class IaDelAula implements Ia {

    /** Qué rige para quien hace la llamada. */
    public sealed interface EnClase extends java.io.Serializable {
        /** Sin clase abierta (o no es estudiante): el modelo como siempre. */
        record Libre() implements EnClase {
        }

        /** En clase, con el modelo apagado para su grupo. */
        record Apagada() implements EnClase {
        }

        /** En clase, con el modelo encendido por su docente: el cupo de su grupo. */
        record ConCupo(UUID grupo) implements EnClase {
        }
    }

    static final String APAGADA = "En clase el asistente de IA está apagado; tu profe puede encenderlo para el grupo.";

    private final Ia real;
    private final Supplier<EnClase> enClase;
    private final Map<UUID, Semaphore> cupos = new ConcurrentHashMap<>();

    public IaDelAula(Ia real, Supplier<EnClase> enClase) {
        this.real = real;
        this.enClase = enClase;
    }

    @Override
    public EstadoIa estado() {
        return enClase.get() instanceof EnClase.Apagada ? EstadoIa.noDisponible(APAGADA) : real.estado();
    }

    @Override
    public RespuestaChat chat(PeticionChat peticion, Consumer<String> alRecibirToken) {
        return conCupo(() -> real.chat(peticion, alRecibirToken));
    }

    @Override
    public Clasificacion clasificar(PeticionClasificacion peticion) {
        return conCupo(() -> real.clasificar(peticion));
    }

    @Override
    public List<float[]> incrustar(PeticionEmbeddings peticion) {
        return conCupo(() -> real.incrustar(peticion));
    }

    /** Cuántas llamadas del grupo esperan su turno (para las pruebas y las métricas). */
    public int esperandoDe(UUID grupo) {
        return Optional.ofNullable(cupos.get(grupo)).map(Semaphore::getQueueLength).orElse(0);
    }

    private <T> T conCupo(Supplier<T> llamada) {
        EnClase regla = enClase.get();
        if (regla instanceof EnClase.Apagada) {
            throw new IaNoDisponible(APAGADA);
        }
        if (!(regla instanceof EnClase.ConCupo(UUID grupo))) {
            return llamada.get();
        }
        Semaphore cupo = cupos.computeIfAbsent(grupo, g -> new Semaphore(1, true));
        try {
            cupo.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IaNoDisponible("Interrumpido esperando el turno del grupo", e);
        }
        try {
            return llamada.get();
        } finally {
            cupo.release();
        }
    }
}
