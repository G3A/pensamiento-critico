package pensamiento.contrato;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import pensamiento.nucleo.AfirmacionConRol;
import pensamiento.nucleo.Ejecucion;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Json;
import pensamiento.nucleo.OrigenAfirmacion;
import pensamiento.nucleo.Pendiente;
import pensamiento.nucleo.RolAfirmacion;
import pensamiento.nucleo.SentidoAfirmacion;
import pensamiento.nucleo.TipoAfirmacion;
import pensamiento.nucleo.TipoPendiente;
import pensamiento.nucleo.Uuid7;
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

    /** Un expediente existente del usuario dado (el real lo inserta; el Fake no necesita nada). */
    protected abstract UUID expedienteDe(UUID usuarioId);

    private static AfirmacionConRol hipotesis(String texto) {
        return new AfirmacionConRol(Uuid7.en(BASE), texto, TipoAfirmacion.CAUSAL, RolAfirmacion.HIPOTESIS,
                SentidoAfirmacion.PRODUCIDA, OrigenAfirmacion.USUARIO);
    }

    @Test
    void las_afirmaciones_y_los_pendientes_se_guardan_con_la_ejecucion_y_se_leen_igual() {
        Personas p = personas();
        RepositorioEjecucion repo = comoUsuario(p.usuarioA());
        AfirmacionConRol h1 = hipotesis("Abrió una feria a dos cuadras los sábados");
        AfirmacionConRol h2 = hipotesis("Subió el precio del pan, señor");
        Pendiente verificar = new Pendiente(TipoPendiente.VERIFICACION, Optional.of(h1.afirmacionId()), Optional.of(LocalDate.of(2026, 10, 10)),
                "Verificar E1: La baja es solo los sábados");
        Ejecucion guardada = repo.guardar(ejecucion(p.usuarioA(), "con-proyeccion-" + UUID.randomUUID(), BASE), List.of(h1, h2), List.of(verificar));

        assertThat(repo.afirmacionesDe(p.usuarioA(), guardada.id())).containsExactlyInAnyOrder(h1, h2);
        assertThat(repo.pendientes(p.usuarioA())).filteredOn(pg -> pg.ejecucionId().equals(guardada.id()))
                .singleElement().satisfies(pg -> {
                    assertThat(pg.pendiente()).isEqualTo(verificar);
                    assertThat(pg.resuelto()).isFalse();
                });
    }

    @Test
    void el_doble_clic_no_duplica_afirmaciones_ni_pendientes() {
        Personas p = personas();
        RepositorioEjecucion repo = comoUsuario(p.usuarioA());
        String clave = "doble-clic-proyeccion-" + UUID.randomUUID();
        AfirmacionConRol h1 = hipotesis("La nevera vieja está fallando");
        Pendiente pendiente = new Pendiente(TipoPendiente.VERIFICACION, Optional.of(h1.afirmacionId()), Optional.empty(), "Verificar E3");
        Ejecucion primera = repo.guardar(ejecucion(p.usuarioA(), clave, BASE), List.of(h1), List.of(pendiente));
        repo.guardar(ejecucion(p.usuarioA(), clave, BASE.plusSeconds(1)), List.of(hipotesis("otra")), List.of(pendiente));

        assertThat(repo.afirmacionesDe(p.usuarioA(), primera.id())).containsExactly(h1);
        assertThat(repo.pendientes(p.usuarioA())).filteredOn(pg -> pg.ejecucionId().equals(primera.id())).hasSize(1);
    }

    @Test
    void asociar_a_un_expediente_y_desasociar() {
        Personas p = personas();
        RepositorioEjecucion repo = comoUsuario(p.usuarioA());
        UUID expediente = expedienteDe(p.usuarioA());
        Ejecucion vieja = repo.guardar(ejecucion(p.usuarioA(), "exp-1-" + UUID.randomUUID(), BASE.minusSeconds(60)));
        Ejecucion nueva = repo.guardar(ejecucion(p.usuarioA(), "exp-2-" + UUID.randomUUID(), BASE));

        assertThat(repo.asociar(p.usuarioA(), vieja.id(), Optional.of(expediente))).isTrue();
        assertThat(repo.asociar(p.usuarioA(), nueva.id(), Optional.of(expediente))).isTrue();
        assertThat(repo.porExpediente(p.usuarioA(), expediente)).extracting(Ejecucion::id).containsExactly(nueva.id(), vieja.id());
        assertThat(repo.porId(p.usuarioA(), vieja.id()).orElseThrow().expedienteId()).contains(expediente);

        assertThat(repo.asociar(p.usuarioA(), vieja.id(), Optional.empty())).isTrue();
        assertThat(repo.porExpediente(p.usuarioA(), expediente)).extracting(Ejecucion::id).containsExactly(nueva.id());
        assertThat(repo.asociar(p.usuarioA(), UUID.randomUUID(), Optional.of(expediente))).isFalse();
    }

    @Test
    void recientes_trae_las_ultimas_de_cualquier_tecnica_hasta_el_limite() {
        Personas p = personas();
        RepositorioEjecucion repo = comoUsuario(p.usuarioA());
        Ejecucion a = repo.guardar(ejecucion(p.usuarioA(), "rec-1-" + UUID.randomUUID(), BASE.plus(Duration.ofDays(30))));
        Ejecucion b = repo.guardar(ejecucion(p.usuarioA(), "rec-2-" + UUID.randomUUID(), BASE.plus(Duration.ofDays(31))));

        assertThat(repo.recientes(p.usuarioA(), 2)).extracting(Ejecucion::id).containsExactly(b.id(), a.id());
        assertThat(repo.recientes(p.usuarioA(), 1)).hasSize(1);
    }

    @Test
    void el_usuario_b_no_ve_afirmaciones_pendientes_ni_puede_asociar_lo_del_usuario_a() {
        Personas p = personas();
        AfirmacionConRol h1 = hipotesis("Los clientes están de vacaciones");
        Ejecucion deA = comoUsuario(p.usuarioA()).guardar(ejecucion(p.usuarioA(), "privada-proy-" + UUID.randomUUID(), BASE), List.of(h1),
                List.of(new Pendiente(TipoPendiente.VERIFICACION, Optional.of(h1.afirmacionId()), Optional.empty(), "Verificar")));
        RepositorioEjecucion comoB = comoUsuario(p.usuarioB());

        assertThat(comoB.afirmacionesDe(p.usuarioB(), deA.id())).isEmpty();
        assertThat(comoB.pendientes(p.usuarioB())).noneMatch(pg -> pg.ejecucionId().equals(deA.id()));
        assertThat(comoB.asociar(p.usuarioB(), deA.id(), Optional.empty())).isFalse();
        assertThat(comoB.recientes(p.usuarioB(), 50)).extracting(Ejecucion::id).doesNotContain(deA.id());
    }

    @Test
    void el_usuario_b_no_ve_las_ejecuciones_del_usuario_a() {
        Personas p = personas();
        Ejecucion deA = comoUsuario(p.usuarioA()).guardar(ejecucion(p.usuarioA(), "privada-" + UUID.randomUUID(), BASE));

        assertThat(comoUsuario(p.usuarioB()).porId(p.usuarioB(), deA.id())).isEmpty();
        assertThat(comoUsuario(p.usuarioB()).porTecnica(p.usuarioB(), IdTecnica.de("T28")).stream().map(Ejecucion::id)).doesNotContain(deA.id());
    }
}
