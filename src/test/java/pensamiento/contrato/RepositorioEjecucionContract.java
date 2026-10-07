package pensamiento.contrato;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import pensamiento.nucleo.Ejecucion;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Json;
import pensamiento.nucleo.puertos.RepositorioEjecucion;

/**
 * Contrato del historial de ejecuciones: no encontrado, idempotencia por clave, orden de la más reciente a la
 * más antigua, JSONB con tildes y ñ ida y vuelta, registro del modelo (RNF-07) y aislamiento por usuario.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class RepositorioEjecucionContract {

    protected static final Instant BASE = Instant.parse("2026-10-07T15:00:00Z");

    /** Dos usuarios distintos de la misma institución, ya existentes en el backend. */
    public record Personas(UUID institucion, UUID usuarioA, UUID usuarioB) {
    }

    protected abstract Personas personas();

    /** Repositorio operando como el usuario dado (el real fija el contexto RLS). */
    protected abstract RepositorioEjecucion comoUsuario(UUID usuarioId);

    protected Ejecucion ejecucion(UUID usuario, String clave, Instant creada) {
        return new Ejecucion(UUID.randomUUID(), usuario, personas().institucion(), IdTecnica.de("T28"), 1, Optional.empty(),
                new Json("{\"escala\":\"CIN\"}"), new Json("{\"pregunta\":\"¿Por qué bajaron las ventas del sábado?\"}"),
                new Json("{\"menosRefutada\":\"H1\",\"nota\":\"la feria, el niño y el ñandú\"}"),
                "Menos refutada: H1, la feria.", Optional.empty(), clave, creada);
    }

    @Test
    void una_ejecucion_que_no_existe_devuelve_vacio() {
        Personas p = personas();
        assertThat(comoUsuario(p.usuarioA()).porId(p.usuarioA(), UUID.randomUUID())).isEmpty();
    }

    @Test
    void lo_guardado_se_lee_igual_incluido_el_jsonb_con_tildes_y_enie() {
        Personas p = personas();
        Ejecucion guardada = comoUsuario(p.usuarioA()).guardar(ejecucion(p.usuarioA(), "clave-" + UUID.randomUUID(), BASE));

        Optional<Ejecucion> leida = comoUsuario(p.usuarioA()).porId(p.usuarioA(), guardada.id());

        assertThat(leida).isPresent();
        assertThat(leida.get().datos().texto()).contains("¿Por qué bajaron las ventas del sábado?");
        assertThat(leida.get().resultado().texto()).contains("ñandú");
        assertThat(leida.get().resumen()).isEqualTo("Menos refutada: H1, la feria.");
        assertThat(leida.get().tecnica()).isEqualTo(IdTecnica.de("T28"));
        assertThat(leida.get().creadaEn()).isEqualTo(BASE);
    }

    @Test
    void guardar_dos_veces_con_la_misma_clave_de_idempotencia_devuelve_la_primera_y_no_duplica() {
        Personas p = personas();
        String clave = "doble-clic-" + UUID.randomUUID();
        RepositorioEjecucion repo = comoUsuario(p.usuarioA());
        Ejecucion primera = repo.guardar(ejecucion(p.usuarioA(), clave, BASE));
        Ejecucion segunda = repo.guardar(ejecucion(p.usuarioA(), clave, BASE.plusSeconds(5)));

        assertThat(segunda.id()).isEqualTo(primera.id());
        assertThat(repo.porTecnica(p.usuarioA(), IdTecnica.de("T28")).stream().filter(e -> e.claveIdempotencia().equals(clave))).hasSize(1);
    }

    @Test
    void el_historial_de_una_tecnica_viene_de_la_mas_reciente_a_la_mas_antigua() {
        Personas p = personas();
        RepositorioEjecucion repo = comoUsuario(p.usuarioA());
        Ejecucion vieja = repo.guardar(ejecucion(p.usuarioA(), "orden-1-" + UUID.randomUUID(), BASE.minus(Duration.ofDays(3))));
        Ejecucion nueva = repo.guardar(ejecucion(p.usuarioA(), "orden-2-" + UUID.randomUUID(), BASE.plus(Duration.ofDays(1))));

        List<Ejecucion> historial = repo.porTecnica(p.usuarioA(), IdTecnica.de("T28"));

        assertThat(historial.stream().map(Ejecucion::id).toList()).containsSubsequence(nueva.id(), vieja.id());
        assertThat(historial.stream().map(Ejecucion::creadaEn).toList()).isSortedAccordingTo(java.util.Comparator.reverseOrder());
    }

    @Test
    void el_registro_del_modelo_se_conserva_para_reproducir_la_ejecucion() {
        Personas p = personas();
        Ejecucion conModelo = new Ejecucion(UUID.randomUUID(), p.usuarioA(), p.institucion(), IdTecnica.de("T34"), 1, Optional.empty(),
                Json.VACIO, Json.VACIO, Json.VACIO, "steelman", Optional.of(new Ejecucion.RegistroModelo("qwen3:4b", "sha256:abc", "steelman-v1", 0.0, 42L)),
                "modelo-" + UUID.randomUUID(), BASE);
        comoUsuario(p.usuarioA()).guardar(conModelo);

        Optional<Ejecucion> leida = comoUsuario(p.usuarioA()).porId(p.usuarioA(), conModelo.id());

        assertThat(leida).isPresent();
        assertThat(leida.get().modelo()).contains(new Ejecucion.RegistroModelo("qwen3:4b", "sha256:abc", "steelman-v1", 0.0, 42L));
    }

    @Test
    void el_usuario_b_no_ve_las_ejecuciones_del_usuario_a() {
        Personas p = personas();
        Ejecucion deA = comoUsuario(p.usuarioA()).guardar(ejecucion(p.usuarioA(), "privada-" + UUID.randomUUID(), BASE));

        assertThat(comoUsuario(p.usuarioB()).porId(p.usuarioB(), deA.id())).isEmpty();
        assertThat(comoUsuario(p.usuarioB()).porTecnica(p.usuarioB(), IdTecnica.de("T28")).stream().map(Ejecucion::id)).doesNotContain(deA.id());
    }
}
