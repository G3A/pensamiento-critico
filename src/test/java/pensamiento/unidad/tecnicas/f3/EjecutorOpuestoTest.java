package pensamiento.unidad.tecnicas.f3;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import pensamiento.catalogo.CatalogoJson;
import pensamiento.catalogo.MapeadorJson;
import pensamiento.nucleo.AfirmacionConRol;
import pensamiento.nucleo.ConModelo;
import pensamiento.nucleo.Ejemplo;
import pensamiento.nucleo.OrigenAfirmacion;
import pensamiento.nucleo.Pendiente;
import pensamiento.nucleo.Propuesta;
import pensamiento.nucleo.Resultado;
import pensamiento.tecnicas.f3.EjecutorOpuesto;
import pensamiento.tecnicas.f3.ResultadoOpuesto;
import pensamiento.testutil.builders.Contextos;
import pensamiento.testutil.fakes.FakeIa;

/**
 * T15 · Considera lo opuesto: el oráculo en modo manual con los ejemplos de docs/ejemplos/T15.md y, con el Fake de Ia,
 * el ejemplo 3 con la postura opuesta del modelo, que no cuenta hasta adoptarse.
 */
class EjecutorOpuestoTest {

    record Esperado(List<String> faltas, String faltantes, String confianza, List<String> pendientes, String resumen) {
    }

    private static final String PROPUESTA = "Las cámaras solo moverían los robos a la cuadra de al lado, donde no hay cámaras.";

    private final EjecutorOpuesto t15 = new EjecutorOpuesto();

    static Stream<Ejemplo> ejemplos() {
        return new CatalogoJson().ejemplosDe(EjecutorOpuesto.ID).stream();
    }

    @Test
    void hay_tres_ejemplos_uno_por_ambito() {
        assertThat(ejemplos().map(Ejemplo::ambito)).containsExactlyInAnyOrder(Ejemplo.Ambito.PERSONAL, Ejemplo.Ambito.TRABAJO, Ejemplo.Ambito.COMUNIDAD);
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("ejemplos")
    void cada_ejemplo_produce_lo_escrito_a_mano(Ejemplo ejemplo) {
        EjecutorOpuesto.Config config = MapeadorJson.leer(ejemplo.config(), EjecutorOpuesto.Config.class);
        EjecutorOpuesto.Entrada entrada = MapeadorJson.leer(ejemplo.datos(), EjecutorOpuesto.Entrada.class);
        Esperado esperado = MapeadorJson.leer(ejemplo.resultado(), Esperado.class);

        assertThat(t15.validar(config, entrada).errores()).isEmpty();
        Resultado<ResultadoOpuesto> r = t15.ejecutar(config, entrada, Contextos.sinIa());

        assertThat(r.valor().opuestas()).extracting(ResultadoOpuesto.OpuestaEvaluada::falta).containsExactlyElementsOf(esperado.faltas());
        assertThat(r.valor().faltantes()).isEqualTo(esperado.faltantes());
        assertThat(r.valor().confianza()).isEqualTo(esperado.confianza());
        assertThat(r.pendientes()).extracting(Pendiente::descripcion).containsExactlyElementsOf(esperado.pendientes());
        assertThat(r.resumen()).isEqualTo(esperado.resumen());
        assertThat(MapeadorJson.leer(MapeadorJson.escribir(r.valor()), ResultadoOpuesto.class)).isEqualTo(r.valor());
    }

    @Test
    void la_postura_opuesta_del_modelo_no_cuenta_hasta_adoptarse() {
        EjecutorOpuesto.Config config = new EjecutorOpuesto.Config(1, EjecutorOpuesto.Modo.MANUAL_Y_MODELO);
        EjecutorOpuesto.Entrada sinOpuesta = new EjecutorOpuesto.Entrada("Las cámaras van a bajar los robos en la cuadra.", 90, List.of(), null, List.of());
        FakeIa ia = new FakeIa();
        ia.programarRespuesta(PROPUESTA);

        ConModelo.Propuestas propuestas = t15.proponer(config, sinOpuesta, Contextos.conIa(ia), t -> { }, 1);
        assertThat(propuestas.nuevas()).singleElement().satisfies(p -> {
            assertThat(p.valor()).isEqualTo(PROPUESTA);
            assertThat(p.prompt()).isEqualTo("t15-opuesta.v1");
        });
        EjecutorOpuesto.Entrada conPropuesta = new EjecutorOpuesto.Entrada(sinOpuesta.postura(), 90, List.of(), null, propuestas.nuevas());

        Resultado<ResultadoOpuesto> sinAdoptar = t15.ejecutar(config, conPropuesta, Contextos.sinIa());
        assertThat(sinAdoptar.resumen()).isEqualTo("0 posturas opuestas de 1 · confianza 90% antes.");
        assertThat(sinAdoptar.afirmaciones()).filteredOn(a -> a.origen() == OrigenAfirmacion.MODELO)
                .singleElement().extracting(AfirmacionConRol::cuenta).isEqualTo(false);
        assertThat(sinAdoptar.modelo()).isPresent();

        Resultado<ResultadoOpuesto> adoptada = t15.ejecutar(config, t15.adoptar(conPropuesta, "IA1"), Contextos.sinIa());
        assertThat(adoptada.resumen()).isEqualTo("1 postura opuesta de 1 · confianza 90% antes.");
        assertThat(adoptada.valor().faltantes()).isNull();
        assertThat(adoptada.valor().opuestas()).singleElement().satisfies(o -> {
            assertThat(o.delModelo()).isTrue();
            assertThat(o.falta()).isEqualTo(EjecutorOpuesto.FALTA_QUE_CAMBIARIA);
        });
        assertThat(adoptada.afirmaciones()).filteredOn(a -> a.origen() == OrigenAfirmacion.MODELO).singleElement()
                .extracting(AfirmacionConRol::cuenta).isEqualTo(true);
        assertThat(adoptada.valor().propuestas()).extracting(Propuesta::adoptada).containsExactly(true);
    }

    @Test
    void con_las_opuestas_completas_no_pide_nada_al_modelo() {
        EjecutorOpuesto.Config config = new EjecutorOpuesto.Config(1, EjecutorOpuesto.Modo.MANUAL_Y_MODELO);
        EjecutorOpuesto.Entrada completa = new EjecutorOpuesto.Entrada("Abrir los domingos.", 60,
                List.of(new EjecutorOpuesto.Opuesta("Los domingos no pasa gente.", null, null)), null, List.of());
        FakeIa ia = new FakeIa();
        assertThat(t15.proponer(config, completa, Contextos.conIa(ia), t -> { }, 1).nuevas()).isEmpty();
        assertThat(ia.chatsRecibidos()).isEmpty();
    }
}
