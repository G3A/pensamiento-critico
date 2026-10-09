package pensamiento.biblioteca;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import pensamiento.nucleo.Fragmento;
import pensamiento.nucleo.Trabajo;
import pensamiento.nucleo.puertos.Biblioteca;
import pensamiento.nucleo.puertos.ExcepcionIa;
import pensamiento.nucleo.puertos.Ia;
import pensamiento.nucleo.puertos.PeticionEmbeddings;
import pensamiento.nucleo.puertos.ProcesadorTrabajo;
import pensamiento.nucleo.puertos.Reloj;
import pensamiento.nucleo.puertos.TransaccionComoUsuario;

/**
 * El trabajo "vectorizar": pide a bge-m3 los vectores de los fragmentos que no los tienen, de a poco (lotes de 8, cada uno con
 * su turno en el semáforo de Ollama, así una pregunta al modelo puede pasar entre dos lotes) y los guarda en pgvector. Sin
 * Ollama, el trabajo vuelve a la cola en un minuto; mientras tanto el documento se encuentra por texto completo.
 */
@Component
public class VectorizadorDocumentos implements ProcesadorTrabajo {

    static final int LOTE = 8;
    static final Duration ESPERA_SIN_MODELO = Duration.ofMinutes(1);

    private final Biblioteca biblioteca;
    private final Ia ia;
    private final TransaccionComoUsuario transaccion;
    private final Reloj reloj;
    private final Duration tiempoMaximo;

    public VectorizadorDocumentos(Biblioteca biblioteca, Ia ia, TransaccionComoUsuario transaccion, Reloj reloj,
                                  @Value("${app.ia.timeout-embeddings:10s}") Duration tiempoMaximo) {
        this.biblioteca = biblioteca;
        this.ia = ia;
        this.transaccion = transaccion;
        this.reloj = reloj;
        this.tiempoMaximo = tiempoMaximo;
    }

    @Override
    public String tipo() {
        return IndexadorDocumentos.VECTORIZAR;
    }

    @Override
    public Desenlace procesar(Trabajo trabajo) {
        PayloadDocumento p = PayloadDocumento.de(trabajo.payload());
        while (true) {
            List<Fragmento> lote = transaccion.ejecutar(p.usuarioId(), p.institucionId(),
                    () -> biblioteca.sinVector(p.usuarioId(), p.documentoId(), LOTE));
            if (lote.isEmpty()) {
                return new Hecho();
            }
            List<float[]> vectores;
            try {
                vectores = ia.incrustar(new PeticionEmbeddings(lote.stream().map(Fragmento::texto).toList(), tiempoMaximo));
            } catch (ExcepcionIa e) {
                return new Reintentar(reloj.ahora().plus(ESPERA_SIN_MODELO), "Ollama no responde: se reintenta en 1 minuto.");
            }
            if (vectores.size() != lote.size()) {
                return new Reintentar(reloj.ahora().plus(ESPERA_SIN_MODELO), "El modelo devolvió otra cantidad de vectores: se reintenta en 1 minuto.");
            }
            Map<UUID, float[]> porFragmento = new LinkedHashMap<>();
            for (int i = 0; i < lote.size(); i++) {
                porFragmento.put(lote.get(i).id(), vectores.get(i));
            }
            transaccion.ejecutar(p.usuarioId(), p.institucionId(), () -> {
                biblioteca.guardarVectores(p.usuarioId(), porFragmento);
                return null;
            });
        }
    }
}
