package pensamiento.contrato;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import pensamiento.nucleo.Prediccion;
import pensamiento.nucleo.PrediccionDeclarada;
import pensamiento.nucleo.puertos.RepositorioPredicciones;

/**
 * Contrato de las predicciones de cada persona (R05): lo guardado se lee igual, con el texto de su afirmación; no
 * encontrado es vacío; otra persona no ve ni resuelve nada; insistir no duplica; el orden es por fecha de revisión; y una
 * predicción resuelta es inmutable.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class RepositorioPrediccionesContract {

    /** Dos usuarios distintos de la misma institución, ya existentes en el backend. */
    public record Personas(UUID institucion, UUID usuarioA, UUID usuarioB) {
    }

    /** Una ejecución guardada del usuario con una afirmación por texto, en ese orden. */
    public record EjecucionConAfirmaciones(UUID ejecucionId, List<UUID> afirmaciones) {
    }

    protected static final Instant RESUELTA_EN = Instant.parse("2027-04-15T14:30:00Z");

    protected abstract Personas personas();

    protected abstract RepositorioPredicciones comoUsuario(UUID usuarioId);

    protected abstract EjecucionConAfirmaciones dadaUnaEjecucionConAfirmaciones(UUID usuarioId, List<String> textos);

    private static PrediccionDeclarada declarada(UUID afirmacion, int confianza, LocalDate fecha) {
        return new PrediccionDeclarada(UUID.randomUUID(), afirmacion, confianza, fecha);
    }

    @Test
    void lo_guardado_se_lee_igual_con_el_texto_de_su_afirmacion_y_pendiente() {
        Personas p = personas();
        EjecucionConAfirmaciones e = dadaUnaEjecucionConAfirmaciones(p.usuarioA(), List.of("La sucursal cubre sus costos en seis meses, señora"));
        PrediccionDeclarada d = declarada(e.afirmaciones().getFirst(), 70, LocalDate.of(2027, 4, 15));

        comoUsuario(p.usuarioA()).guardar(p.usuarioA(), p.institucion(), e.ejecucionId(), List.of(d));

        Prediccion esperada = new Prediccion(d.id(), e.ejecucionId(), d.afirmacionId(), "La sucursal cubre sus costos en seis meses, señora", 70,
                LocalDate.of(2027, 4, 15), Prediccion.Estado.PENDIENTE, Optional.empty());
        assertThat(comoUsuario(p.usuarioA()).porId(p.usuarioA(), d.id())).contains(esperada);
        assertThat(comoUsuario(p.usuarioA()).deEjecucion(p.usuarioA(), e.ejecucionId())).containsExactly(esperada);
        assertThat(comoUsuario(p.usuarioA()).deUsuario(p.usuarioA())).contains(esperada);
    }

    @Test
    void un_identificador_que_no_existe_devuelve_vacio_y_no_se_resuelve() {
        Personas p = personas();
        RepositorioPredicciones repo = comoUsuario(p.usuarioA());

        assertThat(repo.porId(p.usuarioA(), UUID.randomUUID())).isEmpty();
        assertThat(repo.deEjecucion(p.usuarioA(), UUID.randomUUID())).isEmpty();
        assertThat(repo.resolver(p.usuarioA(), UUID.randomUUID(), true, RESUELTA_EN)).isEmpty();
    }

    @Test
    void otra_persona_no_ve_ni_resuelve_las_predicciones() {
        Personas p = personas();
        EjecucionConAfirmaciones e = dadaUnaEjecucionConAfirmaciones(p.usuarioA(), List.of("La asamblea aprueba las cámaras"));
        PrediccionDeclarada d = declarada(e.afirmaciones().getFirst(), 95, LocalDate.of(2026, 10, 17));
        comoUsuario(p.usuarioA()).guardar(p.usuarioA(), p.institucion(), e.ejecucionId(), List.of(d));
        RepositorioPredicciones comoB = comoUsuario(p.usuarioB());

        assertThat(comoB.porId(p.usuarioB(), d.id())).isEmpty();
        assertThat(comoB.deEjecucion(p.usuarioB(), e.ejecucionId())).isEmpty();
        assertThat(comoB.deUsuario(p.usuarioB())).extracting(Prediccion::id).doesNotContain(d.id());
        assertThat(comoB.resolver(p.usuarioB(), d.id(), false, RESUELTA_EN)).isEmpty();
        assertThat(comoUsuario(p.usuarioA()).porId(p.usuarioA(), d.id())).hasValueSatisfying(x -> assertThat(x.resuelta()).isFalse());
    }

    @Test
    void guardar_dos_veces_la_misma_prediccion_no_duplica() {
        Personas p = personas();
        EjecucionConAfirmaciones e = dadaUnaEjecucionConAfirmaciones(p.usuarioA(), List.of("Termina el primer año sin perder materias"));
        PrediccionDeclarada d = declarada(e.afirmaciones().getFirst(), 60, LocalDate.of(2027, 3, 1));
        RepositorioPredicciones repo = comoUsuario(p.usuarioA());

        repo.guardar(p.usuarioA(), p.institucion(), e.ejecucionId(), List.of(d));
        repo.guardar(p.usuarioA(), p.institucion(), e.ejecucionId(), List.of(d));

        assertThat(repo.deEjecucion(p.usuarioA(), e.ejecucionId())).hasSize(1);
    }

    @Test
    void las_de_la_persona_vienen_por_fecha_de_revision() {
        Personas p = personas();
        EjecucionConAfirmaciones e = dadaUnaEjecucionConAfirmaciones(p.usuarioA(), List.of("Tarde", "Temprano"));
        PrediccionDeclarada tarde = declarada(e.afirmaciones().get(0), 80, LocalDate.of(2031, 12, 1));
        PrediccionDeclarada temprano = declarada(e.afirmaciones().get(1), 80, LocalDate.of(2031, 1, 1));
        comoUsuario(p.usuarioA()).guardar(p.usuarioA(), p.institucion(), e.ejecucionId(), List.of(tarde, temprano));

        assertThat(comoUsuario(p.usuarioA()).deUsuario(p.usuarioA())).extracting(Prediccion::id)
                .containsSubsequence(temprano.id(), tarde.id());
    }

    @Test
    void resolver_registra_el_resultado_y_una_resuelta_ya_no_se_puede_modificar() {
        Personas p = personas();
        EjecucionConAfirmaciones e = dadaUnaEjecucionConAfirmaciones(p.usuarioA(), List.of("La sucursal de la terminal cubre sus costos"));
        PrediccionDeclarada d = declarada(e.afirmaciones().getFirst(), 70, LocalDate.of(2027, 4, 15));
        RepositorioPredicciones repo = comoUsuario(p.usuarioA());
        repo.guardar(p.usuarioA(), p.institucion(), e.ejecucionId(), List.of(d));

        assertThat(repo.resolver(p.usuarioA(), d.id(), true, RESUELTA_EN)).hasValueSatisfying(r -> {
            assertThat(r.estado()).isEqualTo(Prediccion.Estado.ACIERTO);
            assertThat(r.resueltaEn()).contains(RESUELTA_EN);
        });
        assertThatThrownBy(() -> repo.resolver(p.usuarioA(), d.id(), false, RESUELTA_EN.plusSeconds(60)))
                .isInstanceOf(Prediccion.YaResuelta.class)
                .hasMessage("Esta predicción ya está resuelta: no se puede modificar.");
        assertThat(repo.porId(p.usuarioA(), d.id())).hasValueSatisfying(r -> {
            assertThat(r.estado()).isEqualTo(Prediccion.Estado.ACIERTO);
            assertThat(r.resueltaEn()).contains(RESUELTA_EN);
        });
    }
}
