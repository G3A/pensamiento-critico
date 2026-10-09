package pensamiento.unidad.tecnicas.f4;

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
import pensamiento.tecnicas.f4.EjecutorLecturaLateral;
import pensamiento.tecnicas.f4.EjecutorLecturaLateral.Externa;
import pensamiento.tecnicas.f4.EjecutorLecturaLateral.Postura;
import pensamiento.tecnicas.f4.ResultadoLecturaLateral;
import pensamiento.testutil.builders.Contextos;

/** T20 · Lectura lateral: el oráculo con los ejemplos de docs/ejemplos/T20.md y los veredictos que los ejemplos no cubren. */
class EjecutorLecturaLateralTest {

    record Esperado(String veredicto, String motivo, String nota, List<String> pendientes, String resumen) {
    }

    private final EjecutorLecturaLateral t20 = new EjecutorLecturaLateral();

    static Stream<Ejemplo> ejemplos() {
        return new CatalogoJson().ejemplosDe(EjecutorLecturaLateral.ID).stream();
    }

    @Test
    void hay_tres_ejemplos_uno_por_ambito() {
        assertThat(ejemplos().map(Ejemplo::ambito)).containsExactlyInAnyOrder(Ejemplo.Ambito.PERSONAL, Ejemplo.Ambito.TRABAJO, Ejemplo.Ambito.COMUNIDAD);
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("ejemplos")
    void cada_ejemplo_produce_lo_escrito_a_mano(Ejemplo ejemplo) {
        EjecutorLecturaLateral.Config config = MapeadorJson.leer(ejemplo.config(), EjecutorLecturaLateral.Config.class);
        EjecutorLecturaLateral.Entrada entrada = MapeadorJson.leer(ejemplo.datos(), EjecutorLecturaLateral.Entrada.class);
        Esperado esperado = MapeadorJson.leer(ejemplo.resultado(), Esperado.class);

        assertThat(t20.validar(config, entrada).errores()).isEmpty();
        Resultado<ResultadoLecturaLateral> r = t20.ejecutar(config, entrada, Contextos.sinIa());

        assertThat(r.valor().veredicto()).isEqualTo(esperado.veredicto());
        assertThat(r.valor().motivo()).isEqualTo(esperado.motivo());
        assertThat(r.valor().nota()).isEqualTo(esperado.nota());
        assertThat(r.pendientes()).extracting(Pendiente::descripcion).containsExactlyElementsOf(esperado.pendientes());
        assertThat(r.resumen()).isEqualTo(esperado.resumen());
        assertThat(r.resumen().toLowerCase()).doesNotContain("verdader");
        assertThat(MapeadorJson.leer(MapeadorJson.escribir(r.valor()), ResultadoLecturaLateral.class)).isEqualTo(r.valor());
    }

    @Test
    void con_independientes_que_confirman_y_contradicen_queda_dividida() {
        EjecutorLecturaLateral.Entrada entrada = new EjecutorLecturaLateral.Entrada("Folleto", "El dato.", List.of(
                new Externa("Uno", "Lo confirma.", Postura.CONFIRMA, true),
                new Externa("Dos", "Lo niega.", Postura.CONTRADICE, true),
                new Externa("Tres", "También lo niega.", Postura.CONTRADICE, true)));

        Resultado<ResultadoLecturaLateral> r = t20.ejecutar(new EjecutorLecturaLateral.Config(2, true), entrada, Contextos.sinIa());

        assertThat(r.valor().veredicto()).isEqualTo("dividida");
        assertThat(r.valor().motivo()).isEqualTo("1 la confirma y 2 la contradicen: busca el dato original.");
        assertThat(r.pendientes()).extracting(Pendiente::descripcion).containsExactly("Rastrear el origen de: El dato.");
    }

    @Test
    void si_ninguna_que_cuenta_la_menciona_queda_sin_eco() {
        EjecutorLecturaLateral.Entrada entrada = new EjecutorLecturaLateral.Entrada("Folleto", "El dato.", List.of(
                new Externa("Uno", "Nada.", Postura.NO_MENCIONA, true),
                new Externa("Dos", "Nada.", Postura.NO_MENCIONA, true),
                new Externa("Tres", "Lo repite.", Postura.CONFIRMA, false)));

        Resultado<ResultadoLecturaLateral> r = t20.ejecutar(new EjecutorLecturaLateral.Config(2, true), entrada, Contextos.sinIa());

        assertThat(r.valor().veredicto()).isEqualTo("sin eco");
        assertThat(r.valor().motivo()).isEqualTo("Ninguna fuente independiente la menciona.");
        assertThat(r.valor().nota()).isEqualTo("No cuenta por no ser independiente: Tres.");
    }
}
