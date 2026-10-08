package pensamiento.unidad.tecnicas.f1;

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
import pensamiento.tecnicas.f1.EjecutorValidez;
import pensamiento.tecnicas.f1.EjecutorValidez.Config;
import pensamiento.tecnicas.f1.EjecutorValidez.Entrada;
import pensamiento.tecnicas.f1.EjecutorValidez.Estado;
import pensamiento.tecnicas.f1.EjecutorValidez.Premisa;
import pensamiento.tecnicas.f1.EjecutorValidez.Seguimiento;
import pensamiento.tecnicas.f1.EjecutorValidez.TipoEsperado;
import pensamiento.tecnicas.f1.ResultadoValidez;
import pensamiento.testutil.builders.Contextos;

/** Oráculo de T05 · Validez y solidez: los ejemplos de docs/ejemplos/T05.md y la corrección 13. */
class EjecutorValidezOraculoTest {

    record Esperado(String tipo, String estadoForma, String fraseForma, String estadoSolidez, List<String> preguntas, List<String> pendientes,
                    String resumen) {
    }

    private final EjecutorValidez t05 = new EjecutorValidez();

    static Stream<Ejemplo> ejemplos() {
        return new CatalogoJson().ejemplosDe(EjecutorValidez.ID).stream();
    }

    @Test
    void hay_tres_ejemplos_uno_por_ambito() {
        assertThat(ejemplos().map(Ejemplo::ambito)).containsExactlyInAnyOrder(Ejemplo.Ambito.PERSONAL, Ejemplo.Ambito.TRABAJO, Ejemplo.Ambito.COMUNIDAD);
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("ejemplos")
    void cada_ejemplo_produce_lo_escrito_a_mano_y_nunca_dice_valido(Ejemplo ejemplo) {
        Config config = MapeadorJson.leer(ejemplo.config(), Config.class);
        Entrada entrada = MapeadorJson.leer(ejemplo.datos(), Entrada.class);
        Esperado esperado = MapeadorJson.leer(ejemplo.resultado(), Esperado.class);

        assertThat(t05.validar(config, entrada).errores()).isEmpty();
        Resultado<ResultadoValidez> r = t05.ejecutar(config, entrada, Contextos.sinIa());

        assertThat(r.valor().tipo().toString()).isEqualTo(esperado.tipo());
        assertThat(r.valor().estadoForma()).isEqualTo(esperado.estadoForma());
        assertThat(r.valor().fraseForma()).isEqualTo(esperado.fraseForma());
        assertThat(r.valor().estadoSolidez()).isEqualTo(esperado.estadoSolidez());
        assertThat(r.valor().preguntas()).containsExactlyElementsOf(esperado.preguntas());
        assertThat(r.pendientes()).extracting(Pendiente::descripcion).containsExactlyElementsOf(esperado.pendientes());
        assertThat(r.resumen()).isEqualTo(esperado.resumen());
        String todo = (r.resumen() + r.valor().fraseForma() + r.valor().fraseSolidez()).toLowerCase();
        assertThat(todo).doesNotContain("válid", "sólid", " ok");
        assertThat(MapeadorJson.leer(MapeadorJson.escribir(r.valor()), ResultadoValidez.class)).isEqualTo(r.valor());
    }

    @Test
    void sin_responder_la_forma_lo_dice_con_la_pregunta_de_su_tipo() {
        Entrada entrada = new Entrada(List.of(new Premisa("Siempre que llueve, el bus llega tarde.", Estado.ESTABLECIDA)),
                "Mañana el bus llegará tarde.", Seguimiento.NO_LO_SE);
        ResultadoValidez r = t05.ejecutar(new Config(TipoEsperado.DETECTAR, 0), entrada, Contextos.sinIa()).valor();
        assertThat(r.tipo()).isEqualTo(ResultadoValidez.Tipo.DEDUCTIVO);
        assertThat(r.fraseForma()).isEqualTo("Falta responder: si las premisas fueran ciertas, ¿podría la conclusión ser falsa?");
        assertThat(r.estadoSolidez()).isEqualTo("no se evalúa");
    }

    @Test
    void en_un_inductivo_la_tolerancia_deja_pasar_premisas_sin_establecer_y_en_un_deductivo_no() {
        Entrada inductivo = new Entrada(List.of(new Premisa("Las ventas de pan subieron tres diciembres seguidos.", Estado.SIN_ESTABLECER)),
                "Seguramente subirán este diciembre.", Seguimiento.SE_SIGUE);
        assertThat(t05.ejecutar(new Config(TipoEsperado.DETECTAR, 1), inductivo, Contextos.sinIa()).valor().estadoSolidez()).isEqualTo("establecida por ti");
        assertThat(t05.ejecutar(new Config(TipoEsperado.DEDUCTIVO, 1), inductivo, Contextos.sinIa()).valor().estadoSolidez()).isEqualTo("sin establecer");
    }
}
