package pensamiento.ia;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import pensamiento.nucleo.puertos.Clasificacion;
import pensamiento.nucleo.puertos.EstadoIa;
import pensamiento.nucleo.puertos.Ia;
import pensamiento.nucleo.puertos.IaNoDisponible;
import pensamiento.nucleo.puertos.IaTiempoAgotado;
import pensamiento.nucleo.puertos.PeticionChat;
import pensamiento.nucleo.puertos.PeticionClasificacion;
import pensamiento.nucleo.puertos.PeticionEmbeddings;
import pensamiento.nucleo.puertos.RespuestaChat;

/**
 * Cortacircuitos del puerto Ia (sección 4): decide por llamada si se intenta. Si el monitor dice que Ollama no
 * está, o si una llamada reciente no respondió o agotó su tiempo, la llamada falla de inmediato con
 * IaNoDisponible y la técnica sigue en modo plantillas. Tras el enfriamiento vuelve a intentar. Una respuesta
 * inválida no abre el circuito: el modelo respondió, solo que mal.
 */
public final class CortacircuitosIa implements Ia {

    private final Ia real;
    private final Supplier<EstadoIa> monitor;
    private final Clock reloj;
    private final Duration enfriamiento;
    private volatile Instant abiertoHasta = Instant.MIN;
    private volatile String motivo = "";

    public CortacircuitosIa(Ia real, Supplier<EstadoIa> monitor, Clock reloj, Duration enfriamiento) {
        this.real = real;
        this.monitor = monitor;
        this.reloj = reloj;
        this.enfriamiento = enfriamiento;
    }

    /** No consulta la red: el estado del monitor, o abierto si una llamada reciente falló. */
    @Override
    public EstadoIa estado() {
        if (abierto()) {
            long segundos = Math.max(1, Duration.between(reloj.instant(), abiertoHasta).toSeconds());
            return EstadoIa.noDisponible("El modelo no respondió hace poco (" + motivo + "); se vuelve a intentar en " + segundos + " s.");
        }
        return monitor.get();
    }

    public boolean abierto() {
        return reloj.instant().isBefore(abiertoHasta);
    }

    @Override
    public RespuestaChat chat(PeticionChat peticion, Consumer<String> alRecibirToken) {
        return intentar(() -> real.chat(peticion, alRecibirToken));
    }

    @Override
    public Clasificacion clasificar(PeticionClasificacion peticion) {
        return intentar(() -> real.clasificar(peticion));
    }

    @Override
    public List<float[]> incrustar(PeticionEmbeddings peticion) {
        return intentar(() -> real.incrustar(peticion));
    }

    private <T> T intentar(Supplier<T> llamada) {
        EstadoIa estado = estado();
        if (!estado.disponible()) {
            throw new IaNoDisponible(estado.detalle());
        }
        try {
            return llamada.get();
        } catch (IaNoDisponible e) {
            abrir("no respondió");
            throw e;
        } catch (IaTiempoAgotado e) {
            abrir("se agotó el tiempo");
            throw e;
        }
    }

    private void abrir(String porQue) {
        motivo = porQue;
        abiertoHasta = reloj.instant().plus(enfriamiento);
    }
}
