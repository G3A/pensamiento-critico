package pensamiento.trabajos;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import pensamiento.nucleo.Trabajo;
import pensamiento.nucleo.puertos.ColaTrabajos;
import pensamiento.nucleo.puertos.ProcesadorTrabajo;
import pensamiento.nucleo.puertos.Reloj;

/**
 * Consume la tabla de trabajos largos (sección 4): al arrancar devuelve a la cola lo que quedó en proceso y abre un hilo
 * virtual por tipo de trabajo, que toma uno a la vez. Indexar un documento nuevo no espera a que termine el vectorizado de
 * otro. El semáforo de Ollama (una llamada a la vez) vive en el adaptador de la IA. Solo toma los tipos que conoce.
 */
@Component
public class EjecutorTrabajos {

    private static final Logger LOG = LoggerFactory.getLogger(EjecutorTrabajos.class);
    static final Duration PAUSA = Duration.ofSeconds(1);
    static final Duration ESPERA_TRAS_ERROR = Duration.ofSeconds(30);

    private final ColaTrabajos cola;
    private final List<ProcesadorTrabajo> procesadores;
    private final Reloj reloj;
    private final AtomicBoolean activo = new AtomicBoolean(false);

    public EjecutorTrabajos(ColaTrabajos cola, List<ProcesadorTrabajo> procesadores, Reloj reloj) {
        this.cola = cola;
        this.procesadores = List.copyOf(procesadores);
        this.reloj = reloj;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void arrancar() {
        Set<String> tipos = Set.copyOf(procesadores.stream().map(ProcesadorTrabajo::tipo).toList());
        int devueltos = cola.reencolarEnProceso(tipos);
        if (devueltos > 0) {
            LOG.info("{} trabajos que quedaron en proceso volvieron a la cola", devueltos);
        }
        activo.set(true);
        for (ProcesadorTrabajo p : procesadores) {
            Thread.ofVirtual().name("trabajos-" + p.tipo()).start(() -> bucle(p));
        }
    }

    @PreDestroy
    public void detener() {
        activo.set(false);
    }

    private void bucle(ProcesadorTrabajo procesador) {
        while (activo.get()) {
            boolean hizoAlgo;
            try {
                hizoAlgo = procesarUno(procesador);
            } catch (RuntimeException e) {
                LOG.warn("La cola de {} falló: {}", procesador.tipo(), e.getMessage());
                hizoAlgo = false;
            }
            if (!hizoAlgo) {
                try {
                    Thread.sleep(PAUSA);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    /** Toma un trabajo de ese tipo, si hay uno disponible, lo procesa y aplica su desenlace. Falso si no había. */
    public boolean procesarUno(ProcesadorTrabajo procesador) {
        Optional<Trabajo> tomado = cola.tomar(reloj.ahora(), Set.of(procesador.tipo()));
        if (tomado.isEmpty()) {
            return false;
        }
        Trabajo t = tomado.get();
        ProcesadorTrabajo.Desenlace desenlace;
        try {
            desenlace = procesador.procesar(t);
        } catch (RuntimeException e) {
            LOG.warn("El trabajo {} ({}) falló: {}", t.id(), t.tipo(), e.getMessage());
            desenlace = new ProcesadorTrabajo.Reintentar(reloj.ahora().plus(ESPERA_TRAS_ERROR), "Falló: se reintenta en 30 s.");
        }
        switch (desenlace) {
            case ProcesadorTrabajo.Hecho h -> cola.terminar(t.id());
            case ProcesadorTrabajo.Reintentar r -> cola.reintentar(t.id(), r.desde(), r.motivo());
            case ProcesadorTrabajo.Falla f -> cola.fallar(t.id(), f.motivo());
        }
        return true;
    }
}
