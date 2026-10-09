package pensamiento.contrato;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import pensamiento.nucleo.CambioOpinion;
import pensamiento.nucleo.puertos.RepositorioCambiosOpinion;

/**
 * Contrato de los cambios de opinión de cada persona (R05): lo guardado se lee igual, con el texto de su afirmación y la
 * fecha dada; no encontrado es vacío; otra persona no ve nada; insistir no duplica; el orden es del más viejo al más
 * nuevo; restaurar importa tal cual y no pisa lo que ya existe.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class RepositorioCambiosOpinionContract {

    /** Dos usuarios distintos de la misma institución, ya existentes en el backend. */
    public record Personas(UUID institucion, UUID usuarioA, UUID usuarioB) {
    }

    /** Una ejecución guardada del usuario con una afirmación por texto, en ese orden. */
    public record EjecucionConAfirmaciones(UUID ejecucionId, List<UUID> afirmaciones) {
    }

    protected static final Instant CERRADA_EN = Instant.parse("2026-10-09T15:30:00Z");

    protected abstract Personas personas();

    protected abstract RepositorioCambiosOpinion comoUsuario(UUID usuarioId);

    protected abstract EjecucionConAfirmaciones dadaUnaEjecucionConAfirmaciones(UUID usuarioId, List<String> textos);

    @Test
    void lo_guardado_se_lee_igual_con_el_texto_de_su_afirmacion() {
        Personas p = personas();
        EjecucionConAfirmaciones e = dadaUnaEjecucionConAfirmaciones(p.usuarioA(), List.of("Conviene abrir la segunda sucursal, señora"));
        CambioOpinion.Declarado d = new CambioOpinion.Declarado(UUID.randomUUID(), e.afirmaciones().getFirst(), 80, 60, CambioOpinion.Causa.EVIDENCIA);

        comoUsuario(p.usuarioA()).guardar(p.usuarioA(), p.institucion(), e.ejecucionId(), List.of(d), CERRADA_EN);

        CambioOpinion esperado = new CambioOpinion(d.id(), d.afirmacionId(), "Conviene abrir la segunda sucursal, señora", 80, 60,
                CambioOpinion.Causa.EVIDENCIA, Optional.of(e.ejecucionId()), CERRADA_EN);
        assertThat(comoUsuario(p.usuarioA()).deEjecucion(p.usuarioA(), e.ejecucionId())).containsExactly(esperado);
        assertThat(comoUsuario(p.usuarioA()).deUsuario(p.usuarioA())).contains(esperado);
    }

    @Test
    void una_ejecucion_que_no_existe_no_tiene_cambios() {
        Personas p = personas();
        assertThat(comoUsuario(p.usuarioA()).deEjecucion(p.usuarioA(), UUID.randomUUID())).isEmpty();
    }

    @Test
    void otra_persona_no_ve_los_cambios() {
        Personas p = personas();
        EjecucionConAfirmaciones e = dadaUnaEjecucionConAfirmaciones(p.usuarioA(), List.of("Las cámaras bajan los robos"));
        CambioOpinion.Declarado d = new CambioOpinion.Declarado(UUID.randomUUID(), e.afirmaciones().getFirst(), 90, 50, CambioOpinion.Causa.STEELMAN);
        comoUsuario(p.usuarioA()).guardar(p.usuarioA(), p.institucion(), e.ejecucionId(), List.of(d), CERRADA_EN);
        RepositorioCambiosOpinion comoB = comoUsuario(p.usuarioB());

        assertThat(comoB.deEjecucion(p.usuarioB(), e.ejecucionId())).isEmpty();
        assertThat(comoB.deUsuario(p.usuarioB())).extracting(CambioOpinion::id).doesNotContain(d.id());
    }

    @Test
    void guardar_dos_veces_el_mismo_cambio_no_duplica() {
        Personas p = personas();
        EjecucionConAfirmaciones e = dadaUnaEjecucionConAfirmaciones(p.usuarioA(), List.of("Debo renunciar"));
        CambioOpinion.Declarado d = new CambioOpinion.Declarado(UUID.randomUUID(), e.afirmaciones().getFirst(), 70, 40, CambioOpinion.Causa.MANUAL);
        RepositorioCambiosOpinion repo = comoUsuario(p.usuarioA());

        repo.guardar(p.usuarioA(), p.institucion(), e.ejecucionId(), List.of(d), CERRADA_EN);
        repo.guardar(p.usuarioA(), p.institucion(), e.ejecucionId(), List.of(d), CERRADA_EN.plusSeconds(60));

        assertThat(repo.deEjecucion(p.usuarioA(), e.ejecucionId())).hasSize(1)
                .allSatisfy(c -> assertThat(c.creadoEn()).isEqualTo(CERRADA_EN));
    }

    @Test
    void los_de_la_persona_vienen_del_mas_viejo_al_mas_nuevo() {
        Personas p = personas();
        EjecucionConAfirmaciones e = dadaUnaEjecucionConAfirmaciones(p.usuarioA(), List.of("Después", "Antes"));
        CambioOpinion.Declarado despues = new CambioOpinion.Declarado(UUID.randomUUID(), e.afirmaciones().get(0), 60, 30, CambioOpinion.Causa.MANUAL);
        CambioOpinion.Declarado antes = new CambioOpinion.Declarado(UUID.randomUUID(), e.afirmaciones().get(1), 20, 50, CambioOpinion.Causa.EVIDENCIA);
        RepositorioCambiosOpinion repo = comoUsuario(p.usuarioA());
        repo.guardar(p.usuarioA(), p.institucion(), e.ejecucionId(), List.of(despues), CERRADA_EN.plusSeconds(3600));
        repo.guardar(p.usuarioA(), p.institucion(), e.ejecucionId(), List.of(antes), CERRADA_EN);

        assertThat(repo.deUsuario(p.usuarioA())).extracting(CambioOpinion::id).containsSubsequence(antes.id(), despues.id());
    }

    @Test
    void restaurar_importa_tal_cual_y_si_ya_existe_no_cambia_nada() {
        Personas p = personas();
        EjecucionConAfirmaciones e = dadaUnaEjecucionConAfirmaciones(p.usuarioA(), List.of("Mamá estaría mejor con nosotros"));
        CambioOpinion importado = new CambioOpinion(UUID.randomUUID(), e.afirmaciones().getFirst(), "texto que no manda", 85, 55,
                CambioOpinion.Causa.STEELMAN, Optional.of(e.ejecucionId()), CERRADA_EN);
        RepositorioCambiosOpinion repo = comoUsuario(p.usuarioA());

        repo.restaurar(p.usuarioA(), p.institucion(), importado);
        repo.restaurar(p.usuarioA(), p.institucion(), new CambioOpinion(importado.id(), e.afirmaciones().getFirst(), "otro", 10, 20,
                CambioOpinion.Causa.MANUAL, Optional.of(e.ejecucionId()), CERRADA_EN.plusSeconds(60)));

        assertThat(repo.deEjecucion(p.usuarioA(), e.ejecucionId())).containsExactly(new CambioOpinion(importado.id(), e.afirmaciones().getFirst(),
                "Mamá estaría mejor con nosotros", 85, 55, CambioOpinion.Causa.STEELMAN, Optional.of(e.ejecucionId()), CERRADA_EN));
    }
}
