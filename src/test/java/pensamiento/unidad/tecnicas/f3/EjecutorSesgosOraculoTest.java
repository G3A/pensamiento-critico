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
import pensamiento.tecnicas.f3.EjecutorSesgos;
import pensamiento.tecnicas.f3.ResultadoSesgos;
import pensamiento.testutil.builders.Contextos;

/** Oráculo de T14 · Sesgos cognitivos: los ejemplos de docs/ejemplos/T14.md, con el catálogo de sesgos real. */
class EjecutorSesgosOraculoTest {

    record SesgoEsperado(String id, boolean probable, String senal) {
    }

    record Esperado(List<SesgoEsperado> sesgos, List<String> pendientes, String resumen) {
    }

    private final EjecutorSesgos t14 = new EjecutorSesgos();

    static Stream<Ejemplo> ejemplos() {
        return new CatalogoJson().ejemplosDe(EjecutorSesgos.ID).stream();
    }

    @Test
    void hay_tres_ejemplos_uno_por_ambito_y_ocho_sesgos_en_el_catalogo() {
        assertThat(ejemplos().map(Ejemplo::ambito)).containsExactlyInAnyOrder(Ejemplo.Ambito.PERSONAL, Ejemplo.Ambito.TRABAJO, Ejemplo.Ambito.COMUNIDAD);
        assertThat(new CatalogoJson().sesgos()).extracting(CatalogoJson.Sesgo::id)
                .containsExactly("anclaje", "costo_hundido", "confirmacion", "exceso_confianza", "disponibilidad", "statu_quo", "arrastre", "halo");
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("ejemplos")
    void cada_ejemplo_produce_lo_escrito_a_mano(Ejemplo ejemplo) {
        EjecutorSesgos.Config config = MapeadorJson.leer(ejemplo.config(), EjecutorSesgos.Config.class);
        EjecutorSesgos.Entrada entrada = MapeadorJson.leer(ejemplo.datos(), EjecutorSesgos.Entrada.class);
        Esperado esperado = MapeadorJson.leer(ejemplo.resultado(), Esperado.class);

        assertThat(t14.validar(config, entrada).errores()).isEmpty();
        Resultado<ResultadoSesgos> r = t14.ejecutar(config, entrada, Contextos.sinIa());

        assertThat(r.valor().sesgos()).extracting(s -> new SesgoEsperado(s.id(), s.probable(), s.senal())).containsExactlyElementsOf(esperado.sesgos());
        assertThat(r.pendientes()).extracting(Pendiente::descripcion).containsExactlyElementsOf(esperado.pendientes());
        assertThat(r.resumen()).isEqualTo(esperado.resumen());
        assertThat(r.resumen().toLowerCase()).doesNotContain("sin sesgos");
        assertThat(MapeadorJson.leer(MapeadorJson.escribir(r.valor()), ResultadoSesgos.class)).isEqualTo(r.valor());
    }

    @Test
    void la_senal_en_el_texto_se_encuentra_sin_mayusculas_ni_tildes_y_cita_el_original() {
        EjecutorSesgos.Config config = new EjecutorSesgos.Config(List.of("statu_quo"), EjecutorSesgos.ContextoDeUso.DECISION, EjecutorSesgos.Modo.LISTA);
        ResultadoSesgos r = t14.ejecutar(config, new EjecutorSesgos.Entrada("Así ha sido siempre en la junta, ¿para qué cambiar?", List.of()),
                Contextos.sinIa()).valor();
        assertThat(r.sesgos()).singleElement().satisfies(s -> assertThat(s.senal()).isEqualTo("Encontré «Así ha sido siempre» en tu situación."));
    }
}
