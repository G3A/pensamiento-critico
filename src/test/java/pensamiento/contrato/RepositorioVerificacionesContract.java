package pensamiento.contrato;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import pensamiento.nucleo.Afirmacion;
import pensamiento.nucleo.EstadoAfirmacion;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.TipoAfirmacion;
import pensamiento.nucleo.Verificacion;
import pensamiento.nucleo.puertos.RepositorioVerificaciones;

/**
 * Contrato de lo que la ficha de verificación lee y escribe de una afirmación: nace sin verificar; el tipo, las preguntas
 * marcadas y el veredicto se leen igual; la confianza puede volver a quedar vacía; el origen es la ejecución que la
 * produjo con su expediente; otra persona no ve ni cambia nada.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class RepositorioVerificacionesContract {

    public record Personas(UUID institucion, UUID usuarioA, UUID usuarioB) {
    }

    /** La afirmación producida por una ejecución de T01, dentro de un expediente si se pide. */
    public record AfirmacionDeEjecucion(UUID afirmacionId, UUID ejecucionId, Optional<UUID> expedienteId) {
    }

    protected static final Instant MARCADA_EN = Instant.parse("2026-10-09T18:00:00Z");

    protected abstract Personas personas();

    protected abstract RepositorioVerificaciones comoUsuario(UUID usuarioId);

    protected abstract AfirmacionDeEjecucion dadaUnaAfirmacion(UUID usuarioId, String texto, TipoAfirmacion tipo, boolean enExpediente);

    @Test
    void una_afirmacion_nace_sin_verificar_con_fuerza_0_y_sin_confianza() {
        Personas p = personas();
        AfirmacionDeEjecucion a = dadaUnaAfirmacion(p.usuarioA(), "El centro tiene más tráfico peatonal que el barrio, señora.", TipoAfirmacion.HECHO,
                false);

        Optional<Afirmacion> leida = comoUsuario(p.usuarioA()).afirmacion(p.usuarioA(), a.afirmacionId());

        assertThat(leida).hasValueSatisfying(x -> {
            assertThat(x.texto()).isEqualTo("El centro tiene más tráfico peatonal que el barrio, señora.");
            assertThat(x.tipo()).isEqualTo(TipoAfirmacion.HECHO);
            assertThat(x.estado()).isEqualTo(EstadoAfirmacion.SIN_VERIFICAR);
            assertThat(x.fuerzaNeta()).isZero();
            assertThat(x.confianza()).isEmpty();
            assertThat(x.usuarioId()).isEqualTo(p.usuarioA());
        });
        assertThat(comoUsuario(p.usuarioA()).verificacion(p.usuarioA(), a.afirmacionId())).isEqualTo(Verificacion.nueva(a.afirmacionId()));
    }

    @Test
    void una_afirmacion_que_no_existe_no_se_encuentra() {
        Personas p = personas();
        assertThat(comoUsuario(p.usuarioA()).afirmacion(p.usuarioA(), UUID.randomUUID())).isEmpty();
        assertThat(comoUsuario(p.usuarioA()).origen(p.usuarioA(), UUID.randomUUID())).isEmpty();
    }

    @Test
    void cambiar_el_tipo_se_lee_en_la_afirmacion() {
        Personas p = personas();
        AfirmacionDeEjecucion a = dadaUnaAfirmacion(p.usuarioA(), "Pasan al menos 1.000 personas por la esquina cada mañana.", TipoAfirmacion.HECHO, false);

        assertThat(comoUsuario(p.usuarioA()).cambiarTipo(p.usuarioA(), a.afirmacionId(), TipoAfirmacion.DATO_ESTADISTICO)).isTrue();

        assertThat(comoUsuario(p.usuarioA()).afirmacion(p.usuarioA(), a.afirmacionId()))
                .hasValueSatisfying(x -> assertThat(x.tipo()).isEqualTo(TipoAfirmacion.DATO_ESTADISTICO));
    }

    @Test
    void las_preguntas_marcadas_se_leen_igual_y_marcar_de_nuevo_las_reemplaza() {
        Personas p = personas();
        AfirmacionDeEjecucion a = dadaUnaAfirmacion(p.usuarioA(), "Los robos en la cuadra subieron este año.", TipoAfirmacion.HECHO, false);
        RepositorioVerificaciones repo = comoUsuario(p.usuarioA());

        repo.marcarPreguntas(p.usuarioA(), p.institucion(), a.afirmacionId(), List.of("¿Quién lo registró y cómo?", "¿De cuándo es el dato?"), MARCADA_EN);
        repo.marcarPreguntas(p.usuarioA(), p.institucion(), a.afirmacionId(), List.of("¿De cuándo es el dato?"), MARCADA_EN.plusSeconds(60));

        Verificacion esperada = new Verificacion(a.afirmacionId(), List.of("¿De cuándo es el dato?"), Optional.of(MARCADA_EN.plusSeconds(60)));
        assertThat(repo.verificacion(p.usuarioA(), a.afirmacionId())).isEqualTo(esperada);
        assertThat(repo.deUsuario(p.usuarioA())).contains(esperada);
    }

    @Test
    void el_veredicto_se_escribe_en_la_afirmacion_y_la_confianza_puede_volver_a_quedar_vacia() {
        Personas p = personas();
        AfirmacionDeEjecucion a = dadaUnaAfirmacion(p.usuarioA(), "El colegio nuevo tiene mejor nivel.", TipoAfirmacion.HECHO, false);
        RepositorioVerificaciones repo = comoUsuario(p.usuarioA());

        assertThat(repo.guardarVeredicto(p.usuarioA(), a.afirmacionId(),
                new Verificacion.Veredicto(TipoAfirmacion.DATO_ESTADISTICO, EstadoAfirmacion.DISPUTADA, 0, Optional.of(45)))).isTrue();
        assertThat(repo.afirmacion(p.usuarioA(), a.afirmacionId())).hasValueSatisfying(x -> {
            assertThat(x.tipo()).isEqualTo(TipoAfirmacion.DATO_ESTADISTICO);
            assertThat(x.estado()).isEqualTo(EstadoAfirmacion.DISPUTADA);
            assertThat(x.fuerzaNeta()).isZero();
            assertThat(x.confianza()).contains(45);
        });

        repo.guardarVeredicto(p.usuarioA(), a.afirmacionId(),
                new Verificacion.Veredicto(TipoAfirmacion.DATO_ESTADISTICO, EstadoAfirmacion.REFUTADA, -9, Optional.empty()));
        assertThat(repo.afirmacion(p.usuarioA(), a.afirmacionId())).hasValueSatisfying(x -> {
            assertThat(x.estado()).isEqualTo(EstadoAfirmacion.REFUTADA);
            assertThat(x.fuerzaNeta()).isEqualTo(-9);
            assertThat(x.confianza()).isEmpty();
        });
    }

    @Test
    void el_origen_es_la_ejecucion_que_la_produjo_con_su_expediente() {
        Personas p = personas();
        AfirmacionDeEjecucion conExpediente = dadaUnaAfirmacion(p.usuarioA(), "Conviene abrir la sucursal.", TipoAfirmacion.JUICIO_DE_VALOR, true);
        AfirmacionDeEjecucion sinExpediente = dadaUnaAfirmacion(p.usuarioA(), "Falta personal.", TipoAfirmacion.HECHO, false);
        RepositorioVerificaciones repo = comoUsuario(p.usuarioA());

        assertThat(repo.origen(p.usuarioA(), conExpediente.afirmacionId()))
                .contains(new Verificacion.Origen(conExpediente.ejecucionId(), IdTecnica.de("T01"), conExpediente.expedienteId()));
        assertThat(repo.origen(p.usuarioA(), sinExpediente.afirmacionId()))
                .contains(new Verificacion.Origen(sinExpediente.ejecucionId(), IdTecnica.de("T01"), Optional.empty()));
    }

    @Test
    void otra_persona_no_ve_ni_cambia_la_afirmacion() {
        Personas p = personas();
        AfirmacionDeEjecucion a = dadaUnaAfirmacion(p.usuarioA(), "Las cámaras bajan los robos.", TipoAfirmacion.CAUSAL, true);
        comoUsuario(p.usuarioA()).marcarPreguntas(p.usuarioA(), p.institucion(), a.afirmacionId(), List.of("¿Quién lo dice?"), MARCADA_EN);
        RepositorioVerificaciones comoB = comoUsuario(p.usuarioB());

        assertThat(comoB.afirmacion(p.usuarioB(), a.afirmacionId())).isEmpty();
        assertThat(comoB.cambiarTipo(p.usuarioB(), a.afirmacionId(), TipoAfirmacion.HECHO)).isFalse();
        assertThat(comoB.guardarVeredicto(p.usuarioB(), a.afirmacionId(),
                new Verificacion.Veredicto(TipoAfirmacion.HECHO, EstadoAfirmacion.VERIFICADA, 12, Optional.of(99)))).isFalse();
        assertThat(comoB.verificacion(p.usuarioB(), a.afirmacionId())).isEqualTo(Verificacion.nueva(a.afirmacionId()));
        assertThat(comoB.origen(p.usuarioB(), a.afirmacionId())).isEmpty();
        assertThat(comoB.deUsuario(p.usuarioB())).extracting(Verificacion::afirmacionId).doesNotContain(a.afirmacionId());
        assertThatThrownBy(() -> comoB.marcarPreguntas(p.usuarioB(), p.institucion(), a.afirmacionId(), List.of("¿Y esto?"), MARCADA_EN))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(comoUsuario(p.usuarioA()).afirmacion(p.usuarioA(), a.afirmacionId()))
                .hasValueSatisfying(x -> {
                    assertThat(x.tipo()).isEqualTo(TipoAfirmacion.CAUSAL);
                    assertThat(x.estado()).isEqualTo(EstadoAfirmacion.SIN_VERIFICAR);
                });
        assertThat(comoUsuario(p.usuarioA()).verificacion(p.usuarioA(), a.afirmacionId()).preguntasRespondidas()).containsExactly("¿Quién lo dice?");
    }
}
