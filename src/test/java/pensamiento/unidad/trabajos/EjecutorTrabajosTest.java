package pensamiento.unidad.trabajos;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import pensamiento.nucleo.Json;
import pensamiento.nucleo.Trabajo;
import pensamiento.nucleo.puertos.ProcesadorTrabajo;
import pensamiento.testutil.fakes.FakeColaTrabajos;
import pensamiento.testutil.fakes.FakeReloj;
import pensamiento.trabajos.EjecutorTrabajos;

/** El ejecutor de trabajos aplica a la cola el desenlace del procesador; una excepción vuelve el trabajo a la cola con espera. */
class EjecutorTrabajosTest {

    /** Un procesador de prueba que devuelve los desenlaces en orden y recuerda qué trabajos recibió. */
    static final class Programado implements ProcesadorTrabajo {
        final Deque<Object> respuestas = new ArrayDeque<>();
        final List<UUID> recibidos = new java.util.ArrayList<>();

        @Override
        public String tipo() {
            return "prueba";
        }

        @Override
        public Desenlace procesar(Trabajo trabajo) {
            recibidos.add(trabajo.id());
            Object r = respuestas.pop();
            if (r instanceof RuntimeException e) {
                throw e;
            }
            return (Desenlace) r;
        }
    }

    private final FakeColaTrabajos cola = new FakeColaTrabajos();
    private final FakeReloj reloj = new FakeReloj();
    private final Programado procesador = new Programado();
    private final EjecutorTrabajos ejecutor = new EjecutorTrabajos(cola, List.of(procesador), reloj);

    @Test
    void sin_trabajos_disponibles_no_hace_nada() {
        cola.encolar("prueba", Json.VACIO, reloj.ahora().plusSeconds(60));
        cola.encolar("otro", Json.VACIO, reloj.ahora());

        assertThat(ejecutor.procesarUno(procesador)).isFalse();
        assertThat(procesador.recibidos).isEmpty();
    }

    @Test
    void hecho_reintentar_y_falla_quedan_en_la_cola() {
        UUID hecho = cola.encolar("prueba", Json.VACIO, reloj.ahora());
        UUID reintento = cola.encolar("prueba", Json.VACIO, reloj.ahora());
        UUID falla = cola.encolar("prueba", Json.VACIO, reloj.ahora());
        procesador.respuestas.add(new ProcesadorTrabajo.Hecho());
        procesador.respuestas.add(new ProcesadorTrabajo.Reintentar(reloj.ahora().plusSeconds(60), "Ollama no responde: se reintenta en 1 minuto."));
        procesador.respuestas.add(new ProcesadorTrabajo.Falla("No se pudo indexar el documento."));

        assertThat(ejecutor.procesarUno(procesador)).isTrue();
        assertThat(ejecutor.procesarUno(procesador)).isTrue();
        assertThat(ejecutor.procesarUno(procesador)).isTrue();

        assertThat(cola.porId(hecho)).hasValueSatisfying(t -> assertThat(t.estado()).isEqualTo(Trabajo.Estado.HECHO));
        assertThat(cola.porId(reintento)).hasValueSatisfying(t -> {
            assertThat(t.estado()).isEqualTo(Trabajo.Estado.PENDIENTE);
            assertThat(t.disponibleEn()).isEqualTo(reloj.ahora().plusSeconds(60));
            assertThat(t.error()).contains("Ollama no responde: se reintenta en 1 minuto.");
        });
        assertThat(cola.porId(falla)).hasValueSatisfying(t -> {
            assertThat(t.estado()).isEqualTo(Trabajo.Estado.ERROR);
            assertThat(t.error()).contains("No se pudo indexar el documento.");
        });
    }

    @Test
    void una_excepcion_del_procesador_vuelve_el_trabajo_a_la_cola_en_30_segundos() {
        UUID id = cola.encolar("prueba", Json.VACIO, reloj.ahora());
        procesador.respuestas.add(new IllegalStateException("la base no responde"));

        assertThat(ejecutor.procesarUno(procesador)).isTrue();

        assertThat(cola.porId(id)).hasValueSatisfying(t -> {
            assertThat(t.estado()).isEqualTo(Trabajo.Estado.PENDIENTE);
            assertThat(t.disponibleEn()).isEqualTo(reloj.ahora().plus(Duration.ofSeconds(30)));
        });
    }
}
