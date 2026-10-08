package pensamiento.unidad.ia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

import pensamiento.ia.CortacircuitosIa;
import pensamiento.nucleo.puertos.EstadoIa;
import pensamiento.nucleo.puertos.IaNoDisponible;
import pensamiento.nucleo.puertos.IaRespuestaInvalida;
import pensamiento.nucleo.puertos.IaTiempoAgotado;
import pensamiento.nucleo.puertos.PeticionChat;
import pensamiento.nucleo.puertos.PeticionClasificacion;
import pensamiento.testutil.fakes.FakeIa;

/**
 * El cortacircuitos decide por llamada (sección 4): sin respuesta o con el tiempo agotado se abre, las llamadas
 * siguientes fallan de inmediato sin tocar a Ollama, y tras el enfriamiento vuelve a intentar.
 */
class CortacircuitosIaTest {

    /** Reloj que la prueba adelanta a mano. */
    static final class RelojMovible extends Clock {
        private Instant ahora = Instant.parse("2026-10-07T15:00:00Z");

        void avanzar(Duration d) {
            ahora = ahora.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return ahora;
        }
    }

    private static final EstadoIa LISTO = new EstadoIa(true, List.of("qwen3:4b", "bge-m3"), "listo");
    private static final PeticionChat HOLA = PeticionChat.simple("hola", Duration.ofSeconds(5));

    private final RelojMovible reloj = new RelojMovible();

    @Test
    void con_ollama_listo_deja_pasar_la_llamada() {
        FakeIa fake = new FakeIa();
        fake.programarRespuesta("Hola.");
        CortacircuitosIa ia = new CortacircuitosIa(fake, () -> LISTO, reloj, Duration.ofSeconds(30));
        assertThat(ia.chat(HOLA, t -> { }).texto()).isEqualTo("Hola.");
        assertThat(ia.estado().disponible()).isTrue();
    }

    @Test
    void si_el_monitor_dice_que_no_esta_falla_sin_llamar() {
        FakeIa fake = new FakeIa();
        CortacircuitosIa ia = new CortacircuitosIa(fake, () -> EstadoIa.noDisponible("Ollama no responde"), reloj, Duration.ofSeconds(30));
        assertThatThrownBy(() -> ia.chat(HOLA, t -> { })).isInstanceOf(IaNoDisponible.class).hasMessage("Ollama no responde");
        assertThat(fake.chatsRecibidos()).isEmpty();
    }

    @Test
    void un_tiempo_agotado_abre_el_circuito_y_tras_el_enfriamiento_vuelve_a_intentar() {
        FakeIa fake = new FakeIa();
        fake.hacerLento();
        CortacircuitosIa ia = new CortacircuitosIa(fake, () -> LISTO, reloj, Duration.ofSeconds(30));

        assertThatThrownBy(() -> ia.chat(HOLA, t -> { })).isInstanceOf(IaTiempoAgotado.class);
        assertThat(ia.abierto()).isTrue();
        assertThat(ia.estado().disponible()).isFalse();
        assertThat(ia.estado().detalle()).contains("se agotó el tiempo").contains("30 s");

        assertThatThrownBy(() -> ia.chat(HOLA, t -> { })).as("abierto: falla de inmediato").isInstanceOf(IaNoDisponible.class);

        reloj.avanzar(Duration.ofSeconds(31));
        assertThat(ia.abierto()).isFalse();
        assertThat(ia.estado().disponible()).isTrue();
    }

    @Test
    void sin_respuesta_tambien_lo_abre() {
        FakeIa fake = new FakeIa();
        fake.apagar();
        CortacircuitosIa ia = new CortacircuitosIa(fake, () -> LISTO, reloj, Duration.ofSeconds(30));
        assertThatThrownBy(() -> ia.clasificar(new PeticionClasificacion("Clasifica.", "texto", List.of("a", "b"), Duration.ofSeconds(5))))
                .isInstanceOf(IaNoDisponible.class);
        assertThat(ia.abierto()).isTrue();
        assertThat(ia.estado().detalle()).contains("no respondió");
    }

    @Test
    void una_respuesta_invalida_no_lo_abre_porque_el_modelo_si_respondio() {
        FakeIa fake = new FakeIa();
        fake.programarClasificacionCruda("esto no es json {");
        CortacircuitosIa ia = new CortacircuitosIa(fake, () -> LISTO, reloj, Duration.ofSeconds(30));
        assertThatThrownBy(() -> ia.clasificar(new PeticionClasificacion("Clasifica.", "texto", List.of("a", "b"), Duration.ofSeconds(5))))
                .isInstanceOf(IaRespuestaInvalida.class);
        assertThat(ia.abierto()).isFalse();
    }
}
