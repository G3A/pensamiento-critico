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
import pensamiento.tecnicas.f3.EjecutorTasasBase;
import pensamiento.tecnicas.f3.EjecutorTasasBase.Criterio;
import pensamiento.tecnicas.f3.ResultadoTasasBase;
import pensamiento.testutil.builders.Contextos;

/** Oráculo de T18 · Correlación, causalidad y tasas base: los ejemplos de docs/ejemplos/T18.md, con el cálculo hecho a mano. */
class EjecutorTasasBaseOraculoTest {

    record Esperado(ResultadoTasasBase.Calculo calculo, String fraseCalculo, int cumplidos, List<String> avisos, List<String> pendientes,
                    String resumen) {
    }

    private final EjecutorTasasBase t18 = new EjecutorTasasBase();

    static Stream<Ejemplo> ejemplos() {
        return new CatalogoJson().ejemplosDe(EjecutorTasasBase.ID).stream();
    }

    @Test
    void hay_tres_ejemplos_uno_por_ambito() {
        assertThat(ejemplos().map(Ejemplo::ambito)).containsExactlyInAnyOrder(Ejemplo.Ambito.PERSONAL, Ejemplo.Ambito.TRABAJO, Ejemplo.Ambito.COMUNIDAD);
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("ejemplos")
    void cada_ejemplo_produce_lo_escrito_a_mano(Ejemplo ejemplo) {
        EjecutorTasasBase.Config config = MapeadorJson.leer(ejemplo.config(), EjecutorTasasBase.Config.class);
        EjecutorTasasBase.Entrada entrada = MapeadorJson.leer(ejemplo.datos(), EjecutorTasasBase.Entrada.class);
        Esperado esperado = MapeadorJson.leer(ejemplo.resultado(), Esperado.class);

        assertThat(t18.validar(config, entrada).errores()).isEmpty();
        Resultado<ResultadoTasasBase> r = t18.ejecutar(config, entrada, Contextos.sinIa());

        assertThat(r.valor().calculo()).isEqualTo(esperado.calculo());
        assertThat(r.valor().fraseCalculo()).isEqualTo(esperado.fraseCalculo());
        assertThat(r.valor().cumplidos()).isEqualTo(esperado.cumplidos());
        assertThat(r.valor().avisos()).containsExactlyElementsOf(esperado.avisos());
        assertThat(r.pendientes()).extracting(Pendiente::descripcion).containsExactlyElementsOf(esperado.pendientes());
        assertThat(r.resumen()).isEqualTo(esperado.resumen());
        assertThat(r.resumen().toLowerCase()).doesNotContain("causa probada");
        assertThat(MapeadorJson.leer(MapeadorJson.escribir(r.valor()), ResultadoTasasBase.class)).isEqualTo(r.valor());
    }

    @Test
    void con_otra_tasa_base_las_frecuencias_cambian_como_se_calcularon_a_mano() {
        // 10 de cada 1000, sensibilidad 90, especificidad 95: 9 detectados, 1 sin detectar; 990 × 5 / 100 = 49,5 → 50 falsos;
        // 59 positivos; 9 / 59 = 15,25 % → 15 %.
        EjecutorTasasBase.Config config = new EjecutorTasasBase.Config(List.of(Criterio.values()), true, EjecutorTasasBase.Formato.PORCENTAJE);
        EjecutorTasasBase.Entrada entrada = new EjecutorTasasBase.Entrada("El examen del colegio salió positivo.", true, 10, 1000, 90, 95, List.of());
        ResultadoTasasBase r = t18.ejecutar(config, entrada, Contextos.sinIa()).valor();
        assertThat(r.calculo()).isEqualTo(new ResultadoTasasBase.Calculo(1000, 10, 990, 9, 1, 50, 940, 59, 15, 90));
        assertThat(r.fraseCalculo()).isEqualTo("Probabilidad real si da positivo: 15%.");
    }
}
