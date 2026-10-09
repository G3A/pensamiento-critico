package pensamiento.contrato;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import pensamiento.nucleo.puertos.RegistroIdentificadores;

/** Contrato: un identificador propio o inexistente no es ajeno; uno de otra persona sí, sea expediente o ejecución. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class RegistroIdentificadoresContract {

    public record Personas(UUID institucion, UUID usuarioA, UUID usuarioB) {
    }

    protected abstract Personas personas();

    /** Consulta como el usuario dado (el real fija el contexto RLS de ese usuario). */
    protected abstract RegistroIdentificadores comoUsuario(UUID usuarioId);

    /** Deja un expediente del usuario y devuelve su identificador. */
    protected abstract UUID expedienteDe(UUID usuarioId);

    /** Deja una ejecución del usuario y devuelve su identificador. */
    protected abstract UUID ejecucionDe(UUID usuarioId);

    /** Deja una predicción del Diario del usuario (con su ejecución y su afirmación) y devuelve su identificador. */
    protected abstract UUID prediccionDe(UUID usuarioId);

    /** Deja una sesión del Consejero del usuario con un turno y un cambio de opinión; devuelve los tres identificadores. */
    protected abstract List<UUID> sesionTurnoYCambioDe(UUID usuarioId);

    @Test
    void la_sesion_su_turno_y_el_cambio_de_opinion_del_usuario_a_son_de_otro_para_b_y_propios_para_a() {
        Personas p = personas();
        for (UUID id : sesionTurnoYCambioDe(p.usuarioA())) {
            assertThat(comoUsuario(p.usuarioB()).deOtroUsuario(p.usuarioB(), id)).as(id.toString()).isTrue();
            assertThat(comoUsuario(p.usuarioA()).deOtroUsuario(p.usuarioA(), id)).as(id.toString()).isFalse();
        }
    }

    @Test
    void la_prediccion_del_usuario_a_es_de_otro_para_el_usuario_b_y_propia_para_a() {
        Personas p = personas();
        UUID prediccion = prediccionDe(p.usuarioA());
        assertThat(comoUsuario(p.usuarioB()).deOtroUsuario(p.usuarioB(), prediccion)).isTrue();
        assertThat(comoUsuario(p.usuarioA()).deOtroUsuario(p.usuarioA(), prediccion)).isFalse();
    }

    @Test
    void un_identificador_que_no_existe_no_es_de_otro() {
        Personas p = personas();
        assertThat(comoUsuario(p.usuarioA()).deOtroUsuario(p.usuarioA(), UUID.randomUUID())).isFalse();
    }

    @Test
    void lo_propio_no_es_de_otro() {
        Personas p = personas();
        UUID expediente = expedienteDe(p.usuarioA());
        UUID ejecucion = ejecucionDe(p.usuarioA());
        assertThat(comoUsuario(p.usuarioA()).deOtroUsuario(p.usuarioA(), expediente)).isFalse();
        assertThat(comoUsuario(p.usuarioA()).deOtroUsuario(p.usuarioA(), ejecucion)).isFalse();
    }

    @Test
    void el_expediente_y_la_ejecucion_del_usuario_a_son_de_otro_para_el_usuario_b() {
        Personas p = personas();
        UUID expediente = expedienteDe(p.usuarioA());
        UUID ejecucion = ejecucionDe(p.usuarioA());
        assertThat(comoUsuario(p.usuarioB()).deOtroUsuario(p.usuarioB(), expediente)).isTrue();
        assertThat(comoUsuario(p.usuarioB()).deOtroUsuario(p.usuarioB(), ejecucion)).isTrue();
    }
}
