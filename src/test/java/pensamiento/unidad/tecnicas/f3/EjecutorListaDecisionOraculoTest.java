package pensamiento.unidad.tecnicas.f3;

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
import pensamiento.tecnicas.f3.EjecutorListaDecision;
import pensamiento.tecnicas.f3.ResultadoListaDecision;
import pensamiento.testutil.builders.Contextos;

/** Oráculo de T16 · Lista de verificación antes de decidir: los ejemplos de docs/ejemplos/T16.md, con el bloqueo del guardado. */
class EjecutorListaDecisionOraculoTest {

    record Esperado(List<String> estados, String bloqueo, String firma, List<String> pendientes, String resumen) {
    }

    private final EjecutorListaDecision t16 = new EjecutorListaDecision();

    static Stream<Ejemplo> ejemplos() {
        return new CatalogoJson().ejemplosDe(EjecutorListaDecision.ID).stream();
    }

    @Test
    void hay_tres_ejemplos_uno_por_ambito() {
        assertThat(ejemplos().map(Ejemplo::ambito)).containsExactlyInAnyOrder(Ejemplo.Ambito.PERSONAL, Ejemplo.Ambito.TRABAJO, Ejemplo.Ambito.COMUNIDAD);
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("ejemplos")
    void cada_ejemplo_produce_lo_escrito_a_mano(Ejemplo ejemplo) {
        EjecutorListaDecision.Config config = MapeadorJson.leer(ejemplo.config(), EjecutorListaDecision.Config.class);
        EjecutorListaDecision.Entrada entrada = MapeadorJson.leer(ejemplo.datos(), EjecutorListaDecision.Entrada.class);
        Esperado esperado = MapeadorJson.leer(ejemplo.resultado(), Esperado.class);

        assertThat(t16.validar(config, entrada).errores()).isEmpty();
        Resultado<ResultadoListaDecision> r = t16.ejecutar(config, entrada, Contextos.sinIa());

        assertThat(r.valor().items()).extracting(i -> i.estado().toString()).containsExactlyElementsOf(esperado.estados());
        assertThat(r.valor().bloqueo()).isEqualTo(esperado.bloqueo());
        assertThat(r.bloqueoGuardado().orElse(null)).as("el bloqueo llega al guardado").isEqualTo(esperado.bloqueo());
        assertThat(r.valor().firma()).isEqualTo(esperado.firma());
        assertThat(r.pendientes()).extracting(Pendiente::descripcion).containsExactlyElementsOf(esperado.pendientes());
        assertThat(r.resumen()).isEqualTo(esperado.resumen());
        assertThat(MapeadorJson.leer(MapeadorJson.escribir(r.valor()), ResultadoListaDecision.class)).isEqualTo(r.valor());
    }

    @Test
    void sin_bloquear_el_guardado_un_obligatorio_vacio_no_impide_guardar() {
        EjecutorListaDecision.Config config = new EjecutorListaDecision.Config(List.of(EjecutorListaDecision.Item.ALTERNATIVAS),
                List.of(EjecutorListaDecision.Item.ALTERNATIVAS), false);
        Resultado<ResultadoListaDecision> r = t16.ejecutar(config,
                new EjecutorListaDecision.Entrada("Pintar la fachada.", null, null, null, null, null, null, null, null), Contextos.sinIa());
        assertThat(r.bloqueoGuardado()).isEmpty();
        assertThat(r.valor().items()).extracting(ResultadoListaDecision.ItemEvaluado::estado).containsExactly(ResultadoListaDecision.Estado.FALTA_OBLIGATORIO);
        assertThat(r.resumen()).isEqualTo("0 de 1 ítems respondidos · sin firmar.");
    }
}
