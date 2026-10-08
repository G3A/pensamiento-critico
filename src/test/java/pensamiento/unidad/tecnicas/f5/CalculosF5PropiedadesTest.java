package pensamiento.unidad.tecnicas.f5;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

import pensamiento.tecnicas.f5.EjecutorBayes;
import pensamiento.tecnicas.f5.MatrizPonderada;
import pensamiento.tecnicas.f5.ResultadoBayes;
import pensamiento.testutil.builders.Contextos;

/**
 * Propiedades de los cálculos de F5: el posterior de T24 · Razonamiento bayesiano queda entre 0 y 100 y es monótono en la
 * verosimilitud; la matriz de T31 · Matriz de decisión ponderada da los mismos totales, ganador y cambios de peso si se
 * reordenan los criterios.
 */
class CalculosF5PropiedadesTest {

    private final EjecutorBayes t24 = new EjecutorBayes();

    private int posterior(int prior, int siCierta, int siFalsa) {
        var config = new EjecutorBayes.Config(50, EjecutorBayes.Formato.PORCENTAJE, 4);
        var entrada = new EjecutorBayes.Entrada("La sucursal cubre sus costos.", prior, List.of(new EjecutorBayes.Evidencia("Dato", siCierta, siFalsa)));
        ResultadoBayes r = t24.ejecutar(config, entrada, Contextos.sinIa()).valor();
        return r.pasos().getLast().posterior();
    }

    @Property
    void el_posterior_queda_entre_0_y_100(@ForAll @IntRange(min = 1, max = 99) int prior, @ForAll @IntRange(min = 1, max = 100) int siCierta,
                                          @ForAll @IntRange(min = 0, max = 100) int siFalsa) {
        assertThat(posterior(prior, siCierta, siFalsa)).isBetween(0, 100);
    }

    @Property
    void subir_la_probabilidad_si_es_cierta_nunca_baja_el_posterior(@ForAll @IntRange(min = 1, max = 99) int prior,
                                                                    @ForAll @IntRange(min = 1, max = 99) int siCierta,
                                                                    @ForAll @IntRange(min = 1, max = 100) int siFalsa) {
        assertThat(posterior(prior, siCierta + 1, siFalsa)).isGreaterThanOrEqualTo(posterior(prior, siCierta, siFalsa));
    }

    @Property
    void subir_la_probabilidad_si_es_falsa_nunca_sube_el_posterior(@ForAll @IntRange(min = 1, max = 99) int prior,
                                                                   @ForAll @IntRange(min = 1, max = 100) int siCierta,
                                                                   @ForAll @IntRange(min = 1, max = 99) int siFalsa) {
        assertThat(posterior(prior, siCierta, siFalsa + 1)).isLessThanOrEqualTo(posterior(prior, siCierta, siFalsa));
    }

    record Matriz(List<Integer> pesos, List<List<Integer>> puntajes, List<Integer> orden) {
    }

    @Property
    void reordenar_los_criterios_no_cambia_totales_ganador_ni_cambios_de_peso(@ForAll("matrices") Matriz m) {
        List<Integer> unos = m.puntajes().stream().map(p -> 1).toList();
        MatrizPonderada.Calculo original = MatrizPonderada.calcular(m.pesos(), m.puntajes(), unos);
        List<Integer> pesos = m.orden().stream().map(m.pesos()::get).toList();
        List<List<Integer>> puntajes = m.puntajes().stream().map(p -> m.orden().stream().map(p::get).toList()).toList();
        MatrizPonderada.Calculo reordenado = MatrizPonderada.calcular(pesos, puntajes, unos);

        assertThat(reordenado.totales()).isEqualTo(original.totales());
        assertThat(reordenado.puestos()).isEqualTo(original.puestos());
        assertThat(reordenado.ganador()).isEqualTo(original.ganador());
        assertThat(reordenado.nivel()).isEqualTo(original.nivel());
        Set<Object> antes = IntStream.range(0, m.pesos().size()).mapToObj(c -> List.of(c, original.cambios().get(c))).collect(Collectors.toSet());
        Set<Object> despues = IntStream.range(0, m.pesos().size()).mapToObj(c -> List.of(m.orden().get(c), reordenado.cambios().get(c)))
                .collect(Collectors.toSet());
        assertThat(despues).isEqualTo(antes);
    }

    @Provide
    Arbitrary<Matriz> matrices() {
        return Arbitraries.integers().between(1, 5).flatMap(criterios -> Arbitraries.integers().between(2, 5).flatMap(opciones -> {
            Arbitrary<List<Integer>> pesos = Arbitraries.integers().between(1, 10).list().ofSize(criterios);
            Arbitrary<List<List<Integer>>> puntajes = Arbitraries.integers().between(1, 5).list().ofSize(criterios).list().ofSize(opciones);
            Arbitrary<List<Integer>> orden = Arbitraries.longs().map(semilla -> {
                List<Integer> indices = new ArrayList<>(IntStream.range(0, criterios).boxed().toList());
                Collections.shuffle(indices, new java.util.Random(semilla));
                return indices;
            });
            return net.jqwik.api.Combinators.combine(pesos, puntajes, orden).as(Matriz::new);
        }));
    }
}
