package pensamiento.web.tecnicas;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.util.HtmlUtils;

/**
 * Los turnos con el modelo local (sección 4, streaming): una conexión SSE por turno, no chunked. El trabajo corre en
 * un hilo virtual desde que se pide; cada conexión recibe "tiempo" cada segundo, "token" con el texto provisional
 * (escapado) y un único "fin" con la versión validada, que cierra la conexión. Si el navegador reconecta con el turno
 * terminado, recibe el "fin" otra vez. Un turno es de una persona y vive cinco minutos.
 */
@Component
public class TurnosIa {

    /** Lo que vuelve al terminar: el HTML que reemplaza la burbuja (con el formulario fuera de banda, si cambió). */
    public record Fin(String html) {
    }

    /** Un turno: de quién es, cuándo empezó, el texto provisional acumulado y el fin, cuando llega. */
    public static final class Turno {
        private final UUID id;
        private final UUID usuarioId;
        private final String tecnica;
        private final Instant inicio;
        private final StringBuilder provisional = new StringBuilder();
        private final List<SseEmitter> conexiones = new CopyOnWriteArrayList<>();
        private volatile Fin fin;
        private volatile boolean cancelado;

        Turno(UUID id, UUID usuarioId, String tecnica, Instant inicio) {
            this.id = id;
            this.usuarioId = usuarioId;
            this.tecnica = tecnica;
            this.inicio = inicio;
        }

        public UUID id() {
            return id;
        }

        public String tecnica() {
            return tecnica;
        }

        public boolean terminado() {
            return fin != null;
        }

        public boolean cancelado() {
            return cancelado;
        }
    }

    static final Duration VIDA = Duration.ofMinutes(5);

    private final Map<UUID, Turno> turnos = new ConcurrentHashMap<>();
    private final ExecutorService trabajo = Executors.newVirtualThreadPerTaskExecutor();
    private final ScheduledExecutorService reloj = Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().factory());
    private final Clock tiempo;

    public TurnosIa() {
        this(Clock.systemUTC());
    }

    TurnosIa(Clock tiempo) {
        this.tiempo = tiempo;
        reloj.scheduleAtFixedRate(this::latir, 1, 1, TimeUnit.SECONDS);
    }

    /**
     * Abre un turno y empieza el trabajo. Si la misma persona ya tiene uno sin terminar en la misma técnica, devuelve
     * ese (doble clic): nunca dos llamadas al modelo por lo mismo.
     *
     * @param trabajo recibe con qué publicar el texto provisional y devuelve el fin
     */
    public Turno abrir(UUID usuarioId, String tecnica, Function<Consumer<String>, Fin> trabajo) {
        limpiar();
        Optional<Turno> enCurso = turnos.values().stream()
                .filter(t -> t.usuarioId.equals(usuarioId) && t.tecnica.equals(tecnica) && !t.terminado() && !t.cancelado)
                .findFirst();
        if (enCurso.isPresent()) {
            return enCurso.get();
        }
        Turno turno = new Turno(UUID.randomUUID(), usuarioId, tecnica, tiempo.instant());
        turnos.put(turno.id, turno);
        this.trabajo.submit(() -> correr(turno, trabajo));
        return turno;
    }

    /** El turno, solo si es de esta persona. */
    public Optional<Turno> de(UUID usuarioId, UUID id) {
        return Optional.ofNullable(turnos.get(id)).filter(t -> t.usuarioId.equals(usuarioId));
    }

    /** Cancelar: se descarta lo que llegue y las conexiones se cierran. El modelo puede seguir hasta su tiempo máximo. */
    public boolean cancelar(UUID usuarioId, UUID id) {
        Optional<Turno> turno = de(usuarioId, id);
        turno.ifPresent(t -> {
            t.cancelado = true;
            t.conexiones.forEach(SseEmitter::complete);
            t.conexiones.clear();
        });
        return turno.isPresent();
    }

    /** Una conexión SSE nueva: recibe lo provisional acumulado y, si ya terminó, el fin. */
    public SseEmitter conectar(Turno turno) {
        SseEmitter emisor = new SseEmitter(VIDA.toMillis());
        if (turno.cancelado) {
            emisor.complete();
            return emisor;
        }
        turno.conexiones.add(emisor);
        emisor.onCompletion(() -> turno.conexiones.remove(emisor));
        emisor.onTimeout(() -> turno.conexiones.remove(emisor));
        emisor.onError(e -> turno.conexiones.remove(emisor));
        synchronized (turno.provisional) {
            if (!turno.provisional.isEmpty()) {
                enviar(turno, emisor, "token", HtmlUtils.htmlEscape(turno.provisional.toString()));
            }
        }
        if (turno.terminado()) {
            terminar(turno, emisor);
        }
        return emisor;
    }

    private void correr(Turno turno, Function<Consumer<String>, Fin> trabajo) {
        Fin fin;
        try {
            fin = trabajo.apply(texto -> {
                if (turno.cancelado || texto == null || texto.isEmpty()) {
                    return;
                }
                synchronized (turno.provisional) {
                    turno.provisional.append(texto);
                }
                String escapado = HtmlUtils.htmlEscape(texto);
                turno.conexiones.forEach(e -> enviar(turno, e, "token", escapado));
            });
        } catch (RuntimeException e) {
            fin = new Fin("<p class=\"aviso-ia\" role=\"status\"><span class=\"chip chip-aviso\">sin modelo</span> "
                    + "Algo falló al pedir propuestas: sigues en modo plantillas.</p>");
        }
        turno.fin = fin;
        if (!turno.cancelado) {
            turno.conexiones.forEach(e -> terminar(turno, e));
        }
    }

    private void terminar(Turno turno, SseEmitter emisor) {
        enviar(turno, emisor, "fin", turno.fin.html());
        emisor.complete();
    }

    /** "tiempo" cada segundo a las conexiones de los turnos en curso, con los segundos desde que se pidió. */
    private void latir() {
        Instant ahora = tiempo.instant();
        for (Turno t : turnos.values()) {
            if (!t.terminado() && !t.cancelado) {
                String segundos = Duration.between(t.inicio, ahora).toSeconds() + " s";
                t.conexiones.forEach(e -> enviar(t, e, "tiempo", segundos));
            }
        }
    }

    /** Un evento SSE de una sola línea por campo data: el HTML viaja sin saltos de línea. */
    private static void enviar(Turno turno, SseEmitter emisor, String evento, String datos) {
        try {
            emisor.send(SseEmitter.event().name(evento).data(datos.replace("\r", "").replace("\n", " ")));
        } catch (IOException | IllegalStateException e) {
            turno.conexiones.remove(emisor);
        }
    }

    private void limpiar() {
        Instant limite = tiempo.instant().minus(VIDA);
        turnos.values().removeIf(t -> t.inicio.isBefore(limite));
    }

    @PreDestroy
    void apagar() {
        reloj.shutdownNow();
        trabajo.shutdownNow();
    }
}
