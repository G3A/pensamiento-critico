package pensamiento.unidad.web.aula;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import pensamiento.nucleo.puertos.Clasificacion;
import pensamiento.nucleo.puertos.EstadoIa;
import pensamiento.nucleo.puertos.Ia;
import pensamiento.nucleo.puertos.IaNoDisponible;
import pensamiento.nucleo.puertos.PeticionChat;
import pensamiento.nucleo.puertos.PeticionClasificacion;
import pensamiento.nucleo.puertos.PeticionEmbeddings;
import pensamiento.nucleo.puertos.RespuestaChat;
import pensamiento.testutil.fakes.FakeIa;
import pensamiento.web.aula.IaDelAula;
import pensamiento.web.aula.IaDelAula.EnClase;

/**
 * Collaboration test de IaDelAula (hito 10, entregable 9): en clase con el modelo apagado, el estado dice no disponible y la
 * llamada no llega a Ollama; fuera de clase, nada cambia; encendido por el docente, cada grupo tiene un solo turno a la vez y
 * otro grupo no espera detrás.
 */
class IaDelAulaTest {

    private final FakeIa ollama = new FakeIa();
    private final AtomicReference<EnClase> regla = new AtomicReference<>(new EnClase.Libre());
    private final IaDelAula ia = new IaDelAula(ollama, regla::get);
    private final PeticionChat pregunta = PeticionChat.simple("¿Qué falacia es?", Duration.ofSeconds(5));

    @Test
    void fuera_de_clase_el_modelo_responde_como_siempre() {
        ollama.programarRespuesta("Generalización apresurada.");

        assertThat(ia.estado().disponible()).isTrue();
        assertThat(ia.chat(pregunta, t -> { }).texto()).isEqualTo("Generalización apresurada.");
    }

    @Test
    void en_clase_con_el_modelo_apagado_el_estado_lo_dice_y_la_llamada_no_llega_a_ollama() {
        regla.set(new EnClase.Apagada());

        assertThat(ia.estado().disponible()).isFalse();
        assertThat(ia.estado().detalle()).contains("tu profe puede encenderlo");
        assertThatThrownBy(() -> ia.chat(pregunta, t -> { })).isInstanceOf(IaNoDisponible.class);
        assertThat(ollama.chatsRecibidos()).isEmpty();
    }

    @Test
    void encendido_por_el_docente_cada_grupo_tiene_un_solo_turno_a_la_vez_y_otro_grupo_no_espera() throws Exception {
        UUID grupoA = UUID.randomUUID();
        UUID grupoB = UUID.randomUUID();
        ThreadLocal<EnClase> porHilo = new ThreadLocal<>();
        CountDownLatch dentro = new CountDownLatch(1);
        CountDownLatch soltar = new CountDownLatch(1);
        AtomicInteger simultaneasDeA = new AtomicInteger();
        AtomicInteger maximoDeA = new AtomicInteger();
        Ia lento = new OllamaQueSeDemoraConElGrupoA(porHilo, grupoA, dentro, soltar, simultaneasDeA, maximoDeA);
        IaDelAula conCupo = new IaDelAula(lento, porHilo::get);

        try (ExecutorService hilos = Executors.newFixedThreadPool(4)) {
            Future<?> a1 = hilos.submit(() -> llamar(conCupo, porHilo, grupoA));
            assertThat(dentro.await(5, TimeUnit.SECONDS)).isTrue();
            Future<?> a2 = hilos.submit(() -> llamar(conCupo, porHilo, grupoA));
            hilos.submit(() -> llamar(conCupo, porHilo, grupoB)).get(5, TimeUnit.SECONDS);   // el grupo B no espera detrás del A
            for (int i = 0; i < 100 && conCupo.esperandoDe(grupoA) == 0; i++) {
                Thread.sleep(20);
            }
            assertThat(conCupo.esperandoDe(grupoA)).as("el segundo del grupo A espera su turno").isEqualTo(1);
            soltar.countDown();
            a1.get(5, TimeUnit.SECONDS);
            a2.get(5, TimeUnit.SECONDS);
        }
        assertThat(maximoDeA.get()).as("nunca dos llamadas del grupo A a la vez").isEqualTo(1);
    }

    private void llamar(IaDelAula conCupo, ThreadLocal<EnClase> porHilo, UUID grupo) {
        porHilo.set(new EnClase.ConCupo(grupo));
        conCupo.chat(pregunta, t -> { });
    }

    /** Un Ollama de prueba que retiene las llamadas del grupo A hasta que se le suelta, y cuenta cuántas tiene a la vez. */
    private record OllamaQueSeDemoraConElGrupoA(ThreadLocal<EnClase> porHilo, UUID grupoA, CountDownLatch dentro, CountDownLatch soltar,
                                               AtomicInteger simultaneas, AtomicInteger maximo) implements Ia {
        @Override
        public EstadoIa estado() {
            return new EstadoIa(true, List.of("qwen3:4b"), "listo");
        }

        @Override
        public RespuestaChat chat(PeticionChat peticion, Consumer<String> alRecibirToken) {
            if (porHilo.get() instanceof EnClase.ConCupo(UUID g) && g.equals(grupoA)) {
                maximo.accumulateAndGet(simultaneas.incrementAndGet(), Math::max);
                dentro.countDown();
                try {
                    soltar.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                simultaneas.decrementAndGet();
            }
            return new RespuestaChat("Generalización apresurada.", "qwen3:4b", "sha256:prueba", 1);
        }

        @Override
        public Clasificacion clasificar(PeticionClasificacion peticion) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<float[]> incrustar(PeticionEmbeddings peticion) {
            throw new UnsupportedOperationException();
        }
    }
}
