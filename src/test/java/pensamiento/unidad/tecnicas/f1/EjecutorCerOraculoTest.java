package pensamiento.unidad.tecnicas.f1;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import pensamiento.catalogo.CatalogoJson;
import pensamiento.catalogo.MapeadorJson;
import pensamiento.nucleo.AfirmacionConRol;
import pensamiento.nucleo.Ejemplo;
import pensamiento.nucleo.Pendiente;
import pensamiento.nucleo.Resultado;
import pensamiento.nucleo.RolAfirmacion;
import pensamiento.tecnicas.f1.EjecutorCer;
import pensamiento.tecnicas.f1.ResultadoCer;
import pensamiento.testutil.builders.Contextos;

/** Oráculo de T03 · Afirmación, evidencia, razonamiento (CER): los ejemplos de docs/ejemplos/T03.md. */
class EjecutorCerOraculoTest {

    record PiezaEsperada(String pieza, String estado, String falta) {
    }

    record Esperado(List<PiezaEsperada> piezas, int completas, int total, List<String> pendientes, String resumen) {
    }

    private final EjecutorCer t03 = new EjecutorCer();

    static Stream<Ejemplo> ejemplos() {
        return new CatalogoJson().ejemplosDe(EjecutorCer.ID).stream();
    }

    @Test
    void hay_tres_ejemplos_uno_por_ambito() {
        assertThat(ejemplos().map(Ejemplo::ambito)).containsExactlyInAnyOrder(Ejemplo.Ambito.PERSONAL, Ejemplo.Ambito.TRABAJO, Ejemplo.Ambito.COMUNIDAD);
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("ejemplos")
    void cada_ejemplo_produce_lo_escrito_a_mano(Ejemplo ejemplo) {
        EjecutorCer.Config config = MapeadorJson.leer(ejemplo.config(), EjecutorCer.Config.class);
        EjecutorCer.Entrada entrada = MapeadorJson.leer(ejemplo.datos(), EjecutorCer.Entrada.class);
        Esperado esperado = MapeadorJson.leer(ejemplo.resultado(), Esperado.class);

        assertThat(t03.validar(config, entrada).errores()).isEmpty();
        Resultado<ResultadoCer> r = t03.ejecutar(config, entrada, Contextos.sinIa());

        assertThat(r.valor().piezas()).extracting(p -> new PiezaEsperada(p.pieza().toString(), p.estado().toString(), p.falta()))
                .containsExactlyElementsOf(esperado.piezas());
        assertThat(r.valor().completas()).isEqualTo(esperado.completas());
        assertThat(r.valor().total()).isEqualTo(esperado.total());
        assertThat(r.pendientes()).extracting(Pendiente::descripcion).containsExactlyElementsOf(esperado.pendientes());
        assertThat(r.resumen()).isEqualTo(esperado.resumen());
        assertThat(MapeadorJson.leer(MapeadorJson.escribir(r.valor()), ResultadoCer.class)).isEqualTo(r.valor());
    }

    @Test
    void la_afirmacion_es_conclusion_y_evidencia_y_contraargumento_son_premisas() {
        Ejemplo maestria = ejemplos().filter(e -> e.titulo().equals("La maestría afuera")).findFirst().orElseThrow();
        Resultado<ResultadoCer> r = t03.ejecutar(MapeadorJson.leer(maestria.config(), EjecutorCer.Config.class),
                MapeadorJson.leer(maestria.datos(), EjecutorCer.Entrada.class), Contextos.sinIa());
        assertThat(r.afirmaciones()).extracting(AfirmacionConRol::rol)
                .containsExactly(RolAfirmacion.CONCLUSION, RolAfirmacion.PREMISA, RolAfirmacion.PREMISA);
        assertThat(r.afirmaciones()).allMatch(AfirmacionConRol::cuenta);
    }

    @Test
    void una_pieza_obligatoria_vacia_no_deja_evaluar() {
        EjecutorCer.Config config = new EjecutorCer.Config(true, 8, List.of(ResultadoCer.Pieza.AFIRMACION, ResultadoCer.Pieza.EVIDENCIA));
        assertThat(t03.validar(config, new EjecutorCer.Entrada("Algo.", null, null, null, null)).errores())
                .extracting(e -> e.campo()).containsExactly("evidencia");
    }
}
