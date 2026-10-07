package pensamiento.ia;

import java.util.concurrent.atomic.AtomicReference;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import pensamiento.nucleo.puertos.EstadoIa;
import pensamiento.nucleo.puertos.Ia;

/** Consulta el estado de Ollama cada 30 segundos y lo deja en memoria para la cabecera y el health. */
@Component
public class MonitorIa {

    private final Ia ia;
    private final AtomicReference<EstadoIa> estado = new AtomicReference<>(EstadoIa.noDisponible("sin consultar todavía"));

    public MonitorIa(Ia ia) {
        this.ia = ia;
    }

    @Scheduled(initialDelayString = "2s", fixedDelayString = "30s")
    public void actualizar() {
        estado.set(ia.estado());
    }

    public EstadoIa estado() {
        return estado.get();
    }
}
