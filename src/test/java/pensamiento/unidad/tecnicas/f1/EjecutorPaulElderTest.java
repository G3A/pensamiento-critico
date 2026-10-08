package pensamiento.unidad.tecnicas.f1;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import pensamiento.catalogo.CatalogoJson;
import pensamiento.catalogo.MapeadorJson;
import pensamiento.nucleo.ConModelo;
import pensamiento.nucleo.Ejemplo;
import pensamiento.nucleo.OrigenAfirmacion;
import pensamiento.nucleo.Pendiente;
import pensamiento.nucleo.Propuesta;
import pensamiento.nucleo.Resultado;
import pensamiento.nucleo.RolAfirmacion;
import pensamiento.tecnicas.f1.EjecutorPaulElder;
import pensamiento.tecnicas.f1.ResultadoPaulElder;
import pensamiento.testutil.builders.Contextos;
import pensamiento.testutil.fakes.FakeIa;

/**
 * T04 · Elementos y estándares de Paul-Elder: el oráculo con los ejemplos de docs/ejemplos/T04.md y, con el Fake de
 * Ia, el ejemplo 1 con las oraciones del texto libre que el modelo asigna a elementos vacíos.
 */
class EjecutorPaulElderTest {

    record EstandarEsperado(String estandar, String estado) {
    }

    record Esperado(int llenos, List<String> vacios, List<EstandarEsperado> estandares, boolean suficiente, List<String> pendientes, String resumen) {
    }

    private final EjecutorPaulElder t04 = new EjecutorPaulElder();

    static Stream<Ejemplo> ejemplos() {
        return new CatalogoJson().ejemplosDe(EjecutorPaulElder.ID).stream();
    }

    @Test
    void hay_tres_ejemplos_uno_por_ambito() {
        assertThat(ejemplos().map(Ejemplo::ambito)).containsExactlyInAnyOrder(Ejemplo.Ambito.PERSONAL, Ejemplo.Ambito.TRABAJO, Ejemplo.Ambito.COMUNIDAD);
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("ejemplos")
    void cada_ejemplo_produce_lo_escrito_a_mano(Ejemplo ejemplo) {
        EjecutorPaulElder.Config config = MapeadorJson.leer(ejemplo.config(), EjecutorPaulElder.Config.class);
        EjecutorPaulElder.Entrada entrada = MapeadorJson.leer(ejemplo.datos(), EjecutorPaulElder.Entrada.class);
        Esperado esperado = MapeadorJson.leer(ejemplo.resultado(), Esperado.class);

        assertThat(t04.validar(config, entrada).errores()).isEmpty();
        Resultado<ResultadoPaulElder> r = t04.ejecutar(config, entrada, Contextos.sinIa());

        assertThat(r.valor().llenos()).isEqualTo(esperado.llenos());
        assertThat(r.valor().elementos()).filteredOn(e -> !e.lleno()).extracting(ResultadoPaulElder.ElementoEvaluado::elemento)
                .containsExactlyElementsOf(esperado.vacios());
        assertThat(r.valor().estandares()).extracting(s -> new EstandarEsperado(s.estandar(), s.estado())).containsExactlyElementsOf(esperado.estandares());
        assertThat(r.valor().suficiente()).isEqualTo(esperado.suficiente());
        assertThat(r.pendientes()).extracting(Pendiente::descripcion).containsExactlyElementsOf(esperado.pendientes());
        assertThat(r.resumen()).isEqualTo(esperado.resumen());
        assertThat(r.resumen().toLowerCase()).doesNotContain("nota", "válido");
        assertThat(MapeadorJson.leer(MapeadorJson.escribir(r.valor()), ResultadoPaulElder.class)).isEqualTo(r.valor());
    }

    @Test
    void la_oracion_que_el_modelo_asigna_a_un_elemento_vacio_cuenta_solo_al_adoptarla() {
        Ejemplo camaras = ejemplos().filter(e -> e.titulo().equals("Las cámaras del barrio")).findFirst().orElseThrow();
        EjecutorPaulElder.Config config = MapeadorJson.leer(camaras.config(), EjecutorPaulElder.Config.class);
        EjecutorPaulElder.Entrada entrada = MapeadorJson.leer(camaras.datos(), EjecutorPaulElder.Entrada.class);
        FakeIa ia = new FakeIa();
        ia.programarClasificacionCruda("{\"etiqueta\":\"proposito\",\"por_que\":\"dice lo que se quiere lograr\"}");
        ia.programarClasificacionCruda("{\"etiqueta\":\"implicaciones\",\"por_que\":\"dice qué pasaría con las cámaras\"}");
        ia.programarClasificacionCruda("{\"etiqueta\":\"puntos_de_vista\",\"por_que\":\"presenta lo que piensan otros\"}");

        ConModelo.Propuestas propuestas = t04.proponer(config, entrada, Contextos.conIa(ia), t -> { }, 1);
        assertThat(propuestas.nuevas()).extracting(Propuesta::codigo, Propuesta::destino, Propuesta::valor).containsExactly(
                org.assertj.core.groups.Tuple.tuple("IA1", "implicaciones", "Las cámaras grabarían a todos los que pasan, también a los vecinos."),
                org.assertj.core.groups.Tuple.tuple("IA2", "puntos_de_vista", "Algunos dicen que es mejor contratar un vigilante."));

        EjecutorPaulElder.Entrada conPropuestas = MapeadorJson.mapper().convertValue(
                java.util.Map.of("tema", entrada.tema(), "proposito", entrada.proposito(), "pregunta", entrada.pregunta(), "informacion", entrada.informacion(),
                        "supuestos", entrada.supuestos(), "inferencias", entrada.inferencias(), "claridad", 7, "exactitud", 3, "amplitud", 2,
                        "propuestas", propuestas.nuevas()), EjecutorPaulElder.Entrada.class);
        assertThat(t04.ejecutar(config, conPropuestas, Contextos.sinIa()).valor().llenos()).isEqualTo(5);

        Resultado<ResultadoPaulElder> adoptada = t04.ejecutar(config, t04.adoptar(conPropuestas, "IA1"), Contextos.sinIa());
        assertThat(adoptada.valor().llenos()).isEqualTo(6);
        assertThat(adoptada.valor().suficiente()).isTrue();
        assertThat(adoptada.pendientes()).isEmpty();
        assertThat(adoptada.resumen()).isEqualTo("Tema: cámaras en el barrio · 6 de 8 elementos · 3 de 9 estándares puntuados.");
        assertThat(adoptada.valor().elementos()).filteredOn(ResultadoPaulElder.ElementoEvaluado::delModelo)
                .extracting(ResultadoPaulElder.ElementoEvaluado::elemento).containsExactly("implicaciones");
        assertThat(adoptada.valor().propuestas()).extracting(Propuesta::adoptada).containsExactly(true, false);
    }

    @Test
    void un_supuesto_adoptado_del_modelo_queda_con_origen_modelo() {
        EjecutorPaulElder.Config config = new EjecutorPaulElder.Config(List.of(EjecutorPaulElder.Elemento.SUPUESTOS), List.of(), 1,
                EjecutorPaulElder.Modo.PLANTILLAS_Y_MODELO);
        Propuesta ia1 = new Propuesta("IA1", "supuestos", "Supuestos", "Los vecinos van a pagar la cuota.", "", false, "qwen3:4b", "sha256:fake", "t04-elemento.v1");
        EjecutorPaulElder.Entrada entrada = MapeadorJson.mapper().convertValue(java.util.Map.of("tema", "cuota", "propuestas", List.of(ia1)),
                EjecutorPaulElder.Entrada.class);
        Resultado<ResultadoPaulElder> r = t04.ejecutar(config, t04.adoptar(entrada, "IA1"), Contextos.sinIa());
        assertThat(r.afirmaciones()).singleElement().satisfies(a -> {
            assertThat(a.rol()).isEqualTo(RolAfirmacion.SUPUESTO);
            assertThat(a.origen()).isEqualTo(OrigenAfirmacion.MODELO);
            assertThat(a.cuenta()).isTrue();
        });
    }
}
