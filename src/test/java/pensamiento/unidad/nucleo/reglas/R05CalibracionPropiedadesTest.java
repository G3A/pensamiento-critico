package pensamiento.unidad.nucleo.reglas;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

import pensamiento.nucleo.reglas.R05Calibracion;

/**
 * Propiedades de R05: el Brier siempre queda entre 0 y 1, y acercar una confianza a lo que pasó nunca lo empeora (decrece
 * con mejor calibración). Los tramos cuentan todas las resueltas, una sola vez.
 */
class R05CalibracionPropiedadesTest {

    @Property
    void el_brier_queda_entre_0_y_1(@ForAll("resueltas") List<R05Calibracion.Prediccion> resueltas) {
        BigDecimal brier = R05Calibracion.brier(resueltas);
        assertThat(brier).isBetween(BigDecimal.ZERO, BigDecimal.ONE);
    }

    @Property
    void acercar_una_confianza_a_lo_que_paso_nunca_sube_el_brier(@ForAll("resueltas") List<R05Calibracion.Prediccion> resueltas,
                                                                @ForAll @IntRange(min = 0, max = 30) int indice,
                                                                @ForAll @IntRange(min = 1, max = 100) int paso) {
        int i = indice % resueltas.size();
        R05Calibracion.Prediccion p = resueltas.get(i);
        boolean seCumplio = p.seCumplio().orElseThrow();
        int nueva = seCumplio ? Math.min(100, p.confianza() + paso) : Math.max(0, p.confianza() - paso);
        List<R05Calibracion.Prediccion> mejor = new ArrayList<>(resueltas);
        mejor.set(i, R05Calibracion.Prediccion.resuelta(nueva, seCumplio));

        assertThat(R05Calibracion.brier(mejor)).isLessThanOrEqualTo(R05Calibracion.brier(resueltas));
    }

    @Property
    void los_tramos_cuentan_cada_resuelta_una_vez(@ForAll("resueltas") List<R05Calibracion.Prediccion> resueltas,
                                                  @ForAll("anchos") int ancho) {
        R05Calibracion.Calibracion c = R05Calibracion.calcular(resueltas, new R05Calibracion.Parametros(ancho, 10, R05Calibracion.Puntaje.BRIER, 0),
                java.time.LocalDate.of(2026, 10, 7));

        assertThat(c.tramos().stream().mapToInt(R05Calibracion.Tramo::n).sum()).isEqualTo(resueltas.size());
        assertThat(c.tramos()).allSatisfy(t -> {
            assertThat(t.porcentajeCumplido()).isBetween(0, 100);
            assertThat(t.confianzaMedia()).isBetween(t.desde(), t.hasta());
        });
    }

    @Provide
    Arbitrary<Integer> anchos() {
        return Arbitraries.of(10, 20);
    }

    @Provide
    Arbitrary<List<R05Calibracion.Prediccion>> resueltas() {
        Arbitrary<R05Calibracion.Prediccion> una = Combinators.combine(Arbitraries.integers().between(0, 100), Arbitraries.of(true, false))
                .as(R05Calibracion.Prediccion::resuelta);
        return una.list().ofMinSize(1).ofMaxSize(40);
    }
}
