package pensamiento.ia;

import java.util.concurrent.atomic.AtomicReference;

import org.springframework.scheduling.annotation.Scheduled;

import pensamiento.nucleo.puertos.EstadoIa;
import pensamiento.nucleo.puertos.Ia;

/** Consulta el estado de Ollama cada 30 segundos y lo deja en memoria para la cabecera, el health y el cortacircuitos. */
public class MonitorIa {

    private final Ia ollama;
    private final AtomicReference<EstadoIa> estado = new AtomicReference<>(EstadoIa.noDisponible("sin consultar todavía"));

    public MonitorIa(Ia ollama) {
        this.ollama = ollama;
    }

    @Scheduled(initialDelayString = "2s", fixedDelayString = "30s")
    public void actualizar() {
        estado.set(ollama.estado());
    }

    public EstadoIa estado() {
        return estado.get();
    }

    /** El adaptador real, sin cortacircuitos: solo para armar el cortacircuitos. */
    Ia ollama() {
        return ollama;
    }
}
