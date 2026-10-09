package pensamiento.contrato;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import pensamiento.nucleo.puertos.TransaccionComoUsuario;

/**
 * Contrato de la transacción como una persona, fuera de una petición web: devuelve lo que devuelve la acción; dentro, la
 * persona y la institución son las pedidas; fuera, no queda nadie fijado; una excepción de la acción llega a quien llama.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class TransaccionComoUsuarioContract {

    /** Una persona de una institución, ya existente en el backend. */
    public record Persona(UUID institucion, UUID usuario) {
    }

    protected abstract Persona persona();

    protected abstract TransaccionComoUsuario transaccion();

    /** Quién ve la base en este momento: el usuario fijado, o vacío si no hay ninguno. */
    protected abstract String usuarioQueVeLaBase();

    @Test
    void devuelve_lo_que_devuelve_la_accion() {
        Persona p = persona();
        assertThat(transaccion().ejecutar(p.usuario(), p.institucion(), () -> "indexado")).isEqualTo("indexado");
    }

    @Test
    void dentro_la_base_ve_a_esa_persona() {
        Persona p = persona();
        assertThat(transaccion().ejecutar(p.usuario(), p.institucion(), this::usuarioQueVeLaBase)).isEqualTo(p.usuario().toString());
        assertThat(usuarioQueVeLaBase()).isEmpty();
    }

    @Test
    void una_excepcion_de_la_accion_llega_a_quien_llama() {
        Persona p = persona();
        assertThatThrownBy(() -> transaccion().ejecutar(p.usuario(), p.institucion(), () -> {
            throw new IllegalStateException("el documento ya no existe");
        })).isInstanceOf(IllegalStateException.class).hasMessage("el documento ya no existe");
        assertThat(usuarioQueVeLaBase()).isEmpty();
    }
}
