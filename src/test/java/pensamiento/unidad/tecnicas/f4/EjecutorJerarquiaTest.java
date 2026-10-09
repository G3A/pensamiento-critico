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
import pensamiento.nucleo.Validacion;
import pensamiento.tecnicas.f4.EjecutorJerarquia;
import pensamiento.tecnicas.f4.ResultadoJerarquia;
import pensamiento.testutil.builders.Contextos;

/** T23 · Jerarquía de evidencia: el oráculo con los ejemplos de docs/ejemplos/T23.md (R01 al 2026-10-07) y sus niveles. */
class EjecutorJerarquiaTest {

    record Esperado(List<Integer> fuerzas, int neta, String magnitud, String sentido, List<String> pendientes, String resumen) {
    }

    private final EjecutorJerarquia t23 = new EjecutorJerarquia();

    static Stream<Ejemplo> ejemplos() {
        return new CatalogoJson().ejemplosDe(EjecutorJerarquia.ID).stream();
    }

    @Test
    void hay_tres_ejemplos_uno_por_ambito() {
        assertThat(ejemplos().map(Ejemplo::ambito)).containsExactlyInAnyOrder(Ejemplo.Ambito.PERSONAL, Ejemplo.Ambito.TRABAJO, Ejemplo.Ambito.COMUNIDAD);
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("ejemplos")
    void cada_ejemplo_produce_lo_escrito_a_mano(Ejemplo ejemplo) {
        EjecutorJerarquia.Config config = MapeadorJson.leer(ejemplo.config(), EjecutorJerarquia.Config.class);
        EjecutorJerarquia.Entrada entrada = MapeadorJson.leer(ejemplo.datos(), EjecutorJerarquia.Entrada.class);
        Esperado esperado = MapeadorJson.leer(ejemplo.resultado(), Esperado.class);

        assertThat(t23.validar(config, entrada).errores()).isEmpty();
        Resultado<ResultadoJerarquia> r = t23.ejecutar(config, entrada, Contextos.sinIa());

        assertThat(r.valor().evidencias()).extracting(ResultadoJerarquia.EvidenciaPesada::fuerza).containsExactlyElementsOf(esperado.fuerzas());
        assertThat(r.valor().neta()).isEqualTo(esperado.neta());
        assertThat(r.valor().magnitud()).isEqualTo(esperado.magnitud());
        assertThat(r.valor().sentido()).isEqualTo(esperado.sentido());
        assertThat(r.pendientes()).extracting(Pendiente::descripcion).containsExactlyElementsOf(esperado.pendientes());
        assertThat(r.resumen()).isEqualTo(esperado.resumen());
        assertThat(MapeadorJson.leer(MapeadorJson.escribir(r.valor()), ResultadoJerarquia.class)).isEqualTo(r.valor());
    }

    @Test
    void la_dieta_sin_gluten_ordena_los_niveles_por_diseno_y_marca_los_vacios() {
        Ejemplo dieta = ejemplos().filter(e -> e.titulo().equals("La dieta sin gluten")).findFirst().orElseThrow();
        Resultado<ResultadoJerarquia> r = t23.ejecutar(MapeadorJson.leer(dieta.config(), EjecutorJerarquia.Config.class),
                MapeadorJson.leer(dieta.datos(), EjecutorJerarquia.Entrada.class), Contextos.sinIa());

        assertThat(r.valor().porDiseno()).isTrue();
        assertThat(r.valor().niveles()).extracting(ResultadoJerarquia.Nivel::nombre)
                .containsExactly("revisión sistemática", "ensayo controlado", "observacional", "opinión de experto", "testimonio");
        assertThat(r.valor().niveles()).extracting(ResultadoJerarquia.Nivel::evidencias)
                .containsExactly(List.of(), List.of("E1"), List.of(), List.of(), List.of("E2", "E3", "E4"));
    }

    @Test
    void un_juicio_de_valor_no_se_pesa() {
        Validacion v = t23.validar(EjecutorJerarquia.Config.v1(), new EjecutorJerarquia.Entrada("El pan integral es mejor.", "juicio_de_valor",
                List.of(new EjecutorJerarquia.EvidenciaRegistrada("Una opinión", "primaria", "no_aplica", null, false, false, null, "apoya"))));

        assertThat(v.errores()).extracting(Validacion.Error::campo).containsExactly("tipo");
    }
}
