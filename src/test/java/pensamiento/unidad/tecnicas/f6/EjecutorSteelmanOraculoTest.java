package pensamiento.unidad.tecnicas.f6;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import pensamiento.catalogo.CatalogoJson;
import pensamiento.catalogo.MapeadorJson;
import pensamiento.nucleo.Ejemplo;
import pensamiento.nucleo.Pendiente;
import pensamiento.nucleo.Resultado;
import pensamiento.tecnicas.f6.EjecutorSteelman;
import pensamiento.tecnicas.f6.ResultadoSteelman;
import pensamiento.testutil.builders.Contextos;

/**
 * Oráculo de T34 · Steelmanning en modo manual: los ejemplos de docs/ejemplos/T34.md, como la semilla los transcribe a
 * la tabla ejemplo, producen el estado, las preguntas, los pendientes y el resumen escritos a mano.
 */
class EjecutorSteelmanOraculoTest {

    record Esperado(String estado, List<String> preguntas, List<String> pendientes, String resumen) {
    }

    private final EjecutorSteelman t34 = new EjecutorSteelman();

    static Stream<Ejemplo> ejemplos() {
        return new CatalogoJson().ejemplosDe(EjecutorSteelman.ID).stream();
    }

    @Test
    void hay_tres_ejemplos_uno_por_ambito() {
        assertThat(ejemplos().map(Ejemplo::ambito)).containsExactlyInAnyOrder(Ejemplo.Ambito.PERSONAL, Ejemplo.Ambito.TRABAJO, Ejemplo.Ambito.COMUNIDAD);
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("ejemplos")
    void cada_ejemplo_produce_lo_escrito_a_mano(Ejemplo ejemplo) {
        EjecutorSteelman.Config config = MapeadorJson.leer(ejemplo.config(), EjecutorSteelman.Config.class);
        EjecutorSteelman.Entrada entrada = MapeadorJson.leer(ejemplo.datos(), EjecutorSteelman.Entrada.class);
        Esperado esperado = MapeadorJson.leer(ejemplo.resultado(), Esperado.class);

        assertThat(t34.validar(config, entrada).errores()).as("el ejemplo es válido").isEmpty();
        Resultado<ResultadoSteelman> r = t34.ejecutar(config, entrada, Contextos.sinIa());

        assertThat(r.valor().estado().toString()).isEqualTo(esperado.estado());
        assertThat(r.valor().preguntas()).containsExactlyElementsOf(esperado.preguntas());
        assertThat(r.pendientes()).extracting(Pendiente::descripcion).containsExactlyElementsOf(esperado.pendientes());
        assertThat(r.resumen()).isEqualTo(esperado.resumen());
        assertThat(r.modelo()).as("en modo manual no hay registro del modelo").isEmpty();
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("ejemplos")
    void el_resultado_hace_ida_y_vuelta_por_el_jsonb(Ejemplo ejemplo) {
        EjecutorSteelman.Config config = MapeadorJson.leer(ejemplo.config(), EjecutorSteelman.Config.class);
        EjecutorSteelman.Entrada entrada = MapeadorJson.leer(ejemplo.datos(), EjecutorSteelman.Entrada.class);
        ResultadoSteelman valor = t34.ejecutar(config, entrada, Contextos.sinIa()).valor();
        assertThat(MapeadorJson.leer(MapeadorJson.escribir(valor), ResultadoSteelman.class)).isEqualTo(valor);
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("ejemplos")
    void la_tarjeta_nunca_dice_correcto_ni_ok(Ejemplo ejemplo) {
        EjecutorSteelman.Config config = MapeadorJson.leer(ejemplo.config(), EjecutorSteelman.Config.class);
        EjecutorSteelman.Entrada entrada = MapeadorJson.leer(ejemplo.datos(), EjecutorSteelman.Entrada.class);
        Resultado<ResultadoSteelman> r = t34.ejecutar(config, entrada, Contextos.sinIa());
        String todo = (r.resumen() + " " + String.join(" ", r.valor().preguntas())).toLowerCase();
        assertThat(todo).doesNotContain("correcto", "válido", "bien hecho", " ok");
        assertThat(r.valor().preguntas()).last().isEqualTo(EjecutorSteelman.SIEMPRE);
    }
}
