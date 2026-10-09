package pensamiento.unidad.tecnicas.f4;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import pensamiento.catalogo.CatalogoJson;
import pensamiento.catalogo.MapeadorJson;
import pensamiento.nucleo.Ejemplo;
import pensamiento.nucleo.Resultado;
import pensamiento.nucleo.Validacion;
import pensamiento.tecnicas.f4.EjecutorCraap;
import pensamiento.tecnicas.f4.ResultadoCraap;
import pensamiento.testutil.builders.Contextos;

/**
 * T21 · CRAAP: el oráculo con los ejemplos de docs/ejemplos/T21.md y propiedades del puntaje: siempre de 0 a 25 y, con
 * los pesos iguales, la suma de los cinco criterios.
 */
class EjecutorCraapTest {

    record Esperado(List<Integer> puntajes, List<String> lineas, List<String> debiles, String resumen) {
    }

    private final EjecutorCraap t21 = new EjecutorCraap();

    static Stream<Ejemplo> ejemplos() {
        return new CatalogoJson().ejemplosDe(EjecutorCraap.ID).stream();
    }

    @Test
    void hay_tres_ejemplos_uno_por_ambito() {
        assertThat(ejemplos().map(Ejemplo::ambito)).containsExactlyInAnyOrder(Ejemplo.Ambito.PERSONAL, Ejemplo.Ambito.TRABAJO, Ejemplo.Ambito.COMUNIDAD);
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("ejemplos")
    void cada_ejemplo_produce_lo_escrito_a_mano(Ejemplo ejemplo) {
        EjecutorCraap.Config config = MapeadorJson.leer(ejemplo.config(), EjecutorCraap.Config.class);
        EjecutorCraap.Entrada entrada = MapeadorJson.leer(ejemplo.datos(), EjecutorCraap.Entrada.class);
        Esperado esperado = MapeadorJson.leer(ejemplo.resultado(), Esperado.class);

        assertThat(t21.validar(config, entrada).errores()).isEmpty();
        Resultado<ResultadoCraap> r = t21.ejecutar(config, entrada, Contextos.sinIa());

        assertThat(r.valor().fuentes()).extracting(ResultadoCraap.FuenteEvaluada::puntaje).containsExactlyElementsOf(esperado.puntajes());
        assertThat(r.valor().fuentes()).extracting(ResultadoCraap.FuenteEvaluada::linea).containsExactlyElementsOf(esperado.lineas());
        assertThat(r.valor().fuentes()).extracting(ResultadoCraap.FuenteEvaluada::debil).containsExactlyElementsOf(esperado.debiles());
        assertThat(r.resumen()).isEqualTo(esperado.resumen());
        assertThat(r.afirmaciones()).isEmpty();
        assertThat(r.pendientes()).isEmpty();
        assertThat(MapeadorJson.leer(MapeadorJson.escribir(r.valor()), ResultadoCraap.class)).isEqualTo(r.valor());
    }

    @Test
    void la_mitad_redondea_hacia_arriba() {
        // 4 + 4 + 3×2 + 3 + 4 = 21; 5 × 21 / 6 = 17,5.
        assertThat(EjecutorCraap.puntaje(List.of(4, 4, 3, 3, 4), List.of(1, 1, 2, 1, 1))).isEqualTo(18);
    }

    @Test
    void con_todos_los_pesos_en_cero_no_hay_puntaje() {
        Validacion v = t21.validar(new EjecutorCraap.Config(0, 0, 0, 0, 0, 18),
                new EjecutorCraap.Entrada(null, List.of(new EjecutorCraap.FuenteCraap("F", 1, 1, 1, 1, 1, null))));

        assertThat(v.errores()).extracting(Validacion.Error::mensaje).containsExactly("Al menos un criterio necesita peso mayor que 0.");
    }

    @Property
    void el_puntaje_siempre_queda_entre_0_y_25(@ForAll("criterios") List<Integer> valores, @ForAll("pesos") List<Integer> pesos) {
        if (pesos.stream().mapToInt(Integer::intValue).sum() == 0) {
            return;
        }
        assertThat(EjecutorCraap.puntaje(valores, pesos)).isBetween(0, 25);
    }

    @Property
    void con_los_pesos_iguales_el_puntaje_es_la_suma(@ForAll("criterios") List<Integer> valores, @ForAll("unPeso") int peso) {
        assertThat(EjecutorCraap.puntaje(valores, List.of(peso, peso, peso, peso, peso))).isEqualTo(valores.stream().mapToInt(Integer::intValue).sum());
    }

    @Provide
    Arbitrary<List<Integer>> criterios() {
        return Arbitraries.integers().between(0, 5).list().ofSize(5);
    }

    @Provide
    Arbitrary<List<Integer>> pesos() {
        return Arbitraries.integers().between(0, 5).list().ofSize(5);
    }

    @Provide
    Arbitrary<Integer> unPeso() {
        return Arbitraries.integers().between(1, 5);
    }
}
