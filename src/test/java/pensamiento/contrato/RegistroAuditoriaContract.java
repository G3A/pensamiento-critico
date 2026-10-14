package pensamiento.contrato;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import pensamiento.nucleo.puertos.RegistroAuditoria;

/** Contrato de la auditoría: solo inserción, lectura de lo propio en orden inverso de fecha, usuario opcional. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class RegistroAuditoriaContract {

    public record Personas(UUID institucion, UUID usuarioA, UUID usuarioB) {
    }

    protected abstract Personas personas();

    protected abstract RegistroAuditoria comoUsuario(UUID usuarioId);

    private static RegistroAuditoria.Evento evento(Personas p, UUID quien, RegistroAuditoria.Accion accion, Instant cuando) {
        return new RegistroAuditoria.Evento(Optional.of(quien), p.institucion(), accion, "expediente", Optional.of(UUID.randomUUID()), cuando);
    }

    @Test
    void lo_registrado_se_lee_del_mas_reciente_al_mas_antiguo() {
        Personas p = personas();
        Instant base = Instant.parse("2026-10-07T15:00:00Z");
        RegistroAuditoria sut = comoUsuario(p.usuarioA());
        sut.registrar(evento(p, p.usuarioA(), RegistroAuditoria.Accion.CREAR, base));
        sut.registrar(evento(p, p.usuarioA(), RegistroAuditoria.Accion.EXPORTAR, base.plusSeconds(60)));

        List<RegistroAuditoria.Evento> eventos = sut.deUsuario(p.usuarioA());

        assertThat(eventos.stream().map(RegistroAuditoria.Evento::accion).toList())
                .containsSubsequence(RegistroAuditoria.Accion.EXPORTAR, RegistroAuditoria.Accion.CREAR);
        assertThat(eventos.stream().map(RegistroAuditoria.Evento::fecha).toList()).isSortedAccordingTo(java.util.Comparator.reverseOrder());
    }

    @Test
    void el_usuario_b_no_lee_los_eventos_del_usuario_a() {
        Personas p = personas();
        comoUsuario(p.usuarioA()).registrar(evento(p, p.usuarioA(), RegistroAuditoria.Accion.BORRAR, Instant.now()));
        assertThat(comoUsuario(p.usuarioB()).deUsuario(p.usuarioA())).isEmpty();
    }

    @Test
    void las_acciones_de_cuentas_guardan_su_detalle() {
        Personas p = personas();
        RegistroAuditoria sut = comoUsuario(p.usuarioA());
        Instant cuando = Instant.parse("2026-10-14T15:00:00Z");
        sut.registrar(new RegistroAuditoria.Evento(Optional.of(p.usuarioA()), p.institucion(), RegistroAuditoria.Accion.FALLO_ENTRAR,
                "usuario", Optional.of(p.usuarioA()), cuando, Optional.of("ana maria")));
        sut.registrar(new RegistroAuditoria.Evento(Optional.of(p.usuarioA()), p.institucion(), RegistroAuditoria.Accion.CAMBIAR_CLAVE,
                "usuario", Optional.of(p.usuarioA()), cuando.plusSeconds(1)));

        assertThat(sut.deUsuario(p.usuarioA())).extracting(RegistroAuditoria.Evento::accion, RegistroAuditoria.Evento::detalle)
                .containsSubsequence(org.assertj.core.groups.Tuple.tuple(RegistroAuditoria.Accion.CAMBIAR_CLAVE, Optional.empty()),
                        org.assertj.core.groups.Tuple.tuple(RegistroAuditoria.Accion.FALLO_ENTRAR, Optional.of("ana maria")));
    }

    @Test
    void un_evento_sin_usuario_se_acepta() {
        Personas p = personas();
        RegistroAuditoria.Evento sistema = new RegistroAuditoria.Evento(Optional.empty(), p.institucion(), RegistroAuditoria.Accion.CREAR,
                "usuario", Optional.empty(), Instant.now());
        comoUsuario(p.usuarioA()).registrar(sistema);
        assertThat(comoUsuario(p.usuarioA()).deUsuario(p.usuarioA())).noneMatch(e -> e.usuarioId().isEmpty());
    }
}
