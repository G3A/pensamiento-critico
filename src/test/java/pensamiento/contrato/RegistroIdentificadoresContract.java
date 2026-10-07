package pensamiento.contrato;

import static org.assertj.core.api.Assertions.assertThat;

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
