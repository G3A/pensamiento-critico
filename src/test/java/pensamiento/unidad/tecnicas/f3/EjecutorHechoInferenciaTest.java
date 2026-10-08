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
import pensamiento.nucleo.Resultado;
import pensamiento.nucleo.TipoAfirmacion;
import pensamiento.tecnicas.f3.EjecutorHechoInferencia;
import pensamiento.tecnicas.f3.ResultadoHechoInferencia;
import pensamiento.testutil.builders.Contextos;
import pensamiento.testutil.fakes.FakeIa;

/**
 * T17 · Hecho, inferencia, juicio: el oráculo con los ejemplos de docs/ejemplos/T17.md y, con el Fake de Ia, el
 * ejemplo 3 con el tipo que propone el modelo para la oración sin etiquetar.
 */
class EjecutorHechoInferenciaTest {

    record Esperado(List<String> lineas, List<String> pendientes, String resumen) {
    }

    private final EjecutorHechoInferencia t17 = new EjecutorHechoInferencia();

    static Stream<Ejemplo> ejemplos() {
        return new CatalogoJson().ejemplosDe(EjecutorHechoInferencia.ID).stream();
    }

    @Test
    void hay_tres_ejemplos_uno_por_ambito() {
        assertThat(ejemplos().map(Ejemplo::ambito)).containsExactlyInAnyOrder(Ejemplo.Ambito.PERSONAL, Ejemplo.Ambito.TRABAJO, Ejemplo.Ambito.COMUNIDAD);
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("ejemplos")
    void cada_ejemplo_produce_lo_escrito_a_mano(Ejemplo ejemplo) {
        EjecutorHechoInferencia.Config config = MapeadorJson.leer(ejemplo.config(), EjecutorHechoInferencia.Config.class);
        EjecutorHechoInferencia.Entrada entrada = MapeadorJson.leer(ejemplo.datos(), EjecutorHechoInferencia.Entrada.class);
        Esperado esperado = MapeadorJson.leer(ejemplo.resultado(), Esperado.class);

        assertThat(t17.validar(config, entrada).errores()).isEmpty();
        Resultado<ResultadoHechoInferencia> r = t17.ejecutar(config, entrada, Contextos.sinIa());

        assertThat(r.valor().oraciones()).extracting(ResultadoHechoInferencia.OracionEtiquetada::linea).containsExactlyElementsOf(esperado.lineas());
        assertThat(r.pendientes()).extracting(Pendiente::descripcion).containsExactlyElementsOf(esperado.pendientes());
        assertThat(r.resumen()).isEqualTo(esperado.resumen());
        assertThat(MapeadorJson.leer(MapeadorJson.escribir(r.valor()), ResultadoHechoInferencia.class)).isEqualTo(r.valor());
    }

    @Test
    void el_tipo_que_propone_el_modelo_no_cuenta_hasta_adoptarse() {
        Ejemplo nota = ejemplos().filter(e -> e.titulo().equals("La nota de matemáticas")).findFirst().orElseThrow();
        EjecutorHechoInferencia.Config config = MapeadorJson.leer(nota.config(), EjecutorHechoInferencia.Config.class);
        EjecutorHechoInferencia.Entrada entrada = MapeadorJson.leer(nota.datos(), EjecutorHechoInferencia.Entrada.class);
        FakeIa ia = new FakeIa();
        ia.programarClasificacionCruda("{\"etiqueta\":\"causal\",\"por_que\":\"atribuye la nota a una causa: el celular\"}");

        ConModelo.Propuestas propuestas = t17.proponer(config, entrada, Contextos.conIa(ia), t -> { }, 1);
        assertThat(propuestas.nuevas()).singleElement().satisfies(p -> {
            assertThat(p.destino()).isEqualTo("2");
            assertThat(p.rotulo()).isEqualTo("Oración 2 · relación causal");
        });
        EjecutorHechoInferencia.Entrada conPropuesta = new EjecutorHechoInferencia.Entrada(entrada.oraciones(), propuestas.nuevas());
        assertThat(t17.ejecutar(config, conPropuesta, Contextos.sinIa()).resumen()).isEqualTo("3 oraciones: 1 hecho, 1 juicio y 1 sin etiquetar.");

        Resultado<ResultadoHechoInferencia> adoptada = t17.ejecutar(config, t17.adoptar(conPropuesta, "IA1"), Contextos.sinIa());
        assertThat(adoptada.resumen()).isEqualTo("3 oraciones: 1 hecho, 1 inferencia y 1 juicio.");
        assertThat(adoptada.pendientes()).extracting(Pendiente::descripcion)
                .containsExactly("Buscar evidencia para la inferencia: Seguro es porque juega mucho en el celular.");
        assertThat(adoptada.afirmaciones()).filteredOn(a -> a.origen() == OrigenAfirmacion.MODELO).singleElement().satisfies(a -> {
            assertThat(a.tipo()).isEqualTo(TipoAfirmacion.CAUSAL);
            assertThat(a.cuenta()).isTrue();
        });
        assertThat(adoptada.afirmaciones()).extracting(AfirmacionConRol::tipo)
                .containsExactly(TipoAfirmacion.DATO_ESTADISTICO, TipoAfirmacion.CAUSAL, TipoAfirmacion.JUICIO_DE_VALOR);
    }

    @Test
    void un_tipo_inactivo_no_se_acepta() {
        EjecutorHechoInferencia.Config solo = new EjecutorHechoInferencia.Config(List.of("hecho"), EjecutorHechoInferencia.Modo.MANUAL);
        EjecutorHechoInferencia.Entrada entrada = new EjecutorHechoInferencia.Entrada(
                List.of(new EjecutorHechoInferencia.Oracion("Va a llover.", "prediccion", null)), List.of());
        assertThat(t17.validar(solo, entrada).errores()).extracting(e -> e.campo()).containsExactly("oraciones");
    }
}
