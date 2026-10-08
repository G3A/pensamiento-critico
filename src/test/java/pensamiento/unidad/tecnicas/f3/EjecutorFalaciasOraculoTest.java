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
import pensamiento.nucleo.TipoPendiente;
import pensamiento.tecnicas.f3.ConfigFalacias;
import pensamiento.tecnicas.f3.EjecutorFalacias;
import pensamiento.tecnicas.f3.EntradaFalacias;
import pensamiento.tecnicas.f3.ResultadoFalacias;
import pensamiento.testutil.builders.Contextos;
import pensamiento.testutil.fakes.FakeRepositorioEsquemas;

/**
 * Oráculo de T13 · Falacias como esquemas fallidos: los ejemplos de docs/ejemplos/T13.md, tal como la semilla
 * los transcribe a la tabla ejemplo, producen el resultado escrito a mano.
 */
class EjecutorFalaciasOraculoTest {

    record MarcaEsperada(String codigo, String fragmento, String esquema, int pregunta, String falacia, String estado, String porque) {
    }

    record Esperado(List<MarcaEsperada> marcas, List<String> pendientes, String resumen) {
    }

    private final EjecutorFalacias t13 = new EjecutorFalacias(new FakeRepositorioEsquemas());

    static Stream<Ejemplo> ejemplos() {
        return new CatalogoJson().ejemplosDe(EjecutorFalacias.ID).stream();
    }

    @Test
    void hay_tres_ejemplos_uno_por_ambito() {
        assertThat(ejemplos().map(Ejemplo::ambito)).containsExactlyInAnyOrder(Ejemplo.Ambito.PERSONAL, Ejemplo.Ambito.TRABAJO, Ejemplo.Ambito.COMUNIDAD);
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("ejemplos")
    void cada_ejemplo_produce_sus_marcas_escritas_a_mano(Ejemplo ejemplo) {
        ConfigFalacias config = MapeadorJson.leer(ejemplo.config(), ConfigFalacias.class);
        EntradaFalacias entrada = MapeadorJson.leer(ejemplo.datos(), EntradaFalacias.class);
        Esperado esperado = MapeadorJson.leer(ejemplo.resultado(), Esperado.class);

        assertThat(t13.validar(config, entrada).errores()).as("el ejemplo es válido").isEmpty();
        Resultado<ResultadoFalacias> resultado = t13.ejecutar(config, entrada, Contextos.sinIa());

        List<MarcaEsperada> marcas = resultado.valor().marcas().stream()
                .map(m -> new MarcaEsperada(m.codigo(), m.fragmento(), m.esquema(), m.pregunta(), m.falacia(), m.estado().toString(), m.porque()))
                .toList();
        assertThat(marcas).containsExactlyElementsOf(esperado.marcas());
        assertThat(resultado.pendientes()).extracting(Pendiente::descripcion).containsExactlyElementsOf(esperado.pendientes());
        assertThat(resultado.resumen()).isEqualTo(esperado.resumen());
        assertThat(resultado.afirmaciones()).as("T13 no produce afirmaciones en este hito").isEmpty();
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("ejemplos")
    void el_resultado_hace_ida_y_vuelta_por_el_jsonb_sin_perder_nada(Ejemplo ejemplo) {
        ConfigFalacias config = MapeadorJson.leer(ejemplo.config(), ConfigFalacias.class);
        EntradaFalacias entrada = MapeadorJson.leer(ejemplo.datos(), EntradaFalacias.class);
        ResultadoFalacias valor = t13.ejecutar(config, entrada, Contextos.sinIa()).valor();

        assertThat(MapeadorJson.leer(MapeadorJson.escribir(valor), ResultadoFalacias.class)).isEqualTo(valor);
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("ejemplos")
    void la_tarjeta_nunca_dice_valido_ni_sin_falacias(Ejemplo ejemplo) {
        ConfigFalacias config = MapeadorJson.leer(ejemplo.config(), ConfigFalacias.class);
        EntradaFalacias entrada = MapeadorJson.leer(ejemplo.datos(), EntradaFalacias.class);
        String resumen = t13.ejecutar(config, entrada, Contextos.sinIa()).resumen().toLowerCase();
        assertThat(resumen).doesNotContain("válido", "correcto", "sin falacias", "ok");
    }

    @Test
    void la_misma_marca_sin_confirmar_queda_propuesta_y_deja_un_pendiente_de_revision() {
        // Ejemplo 1 de T13.md sin la confirmación: la regla propone, la persona no ha confirmado (R06).
        EntradaFalacias sinConfirmar = new EntradaFalacias(
                "Los que se oponen a las cámaras son los que tienen algo que esconder, así que no hay que escucharlos.", List.of());

        Resultado<ResultadoFalacias> r = t13.ejecutar(ConfigFalacias.porDefecto(), sinConfirmar, Contextos.sinIa());

        assertThat(r.valor().marcas()).singleElement().satisfies(m -> assertThat(m.estado()).isEqualTo(ResultadoFalacias.Estado.PROPUESTA));
        assertThat(r.pendientes()).singleElement().satisfies(p -> {
            assertThat(p.tipo()).isEqualTo(TipoPendiente.REVISION);
            assertThat(p.descripcion()).isEqualTo("Responder la pregunta crítica de M1 (Ataque a la persona): "
                    + "¿La característica o la circunstancia de quien habla afecta la verdad de lo que dice?");
        });
        assertThat(r.resumen()).isEqualTo("1 marca: ninguna falacia confirmada y 1 esquema con preguntas sin responder.");
    }

    @Test
    void con_el_esquema_apagado_la_regla_no_marca() {
        ConfigFalacias sinAdHominem = new ConfigFalacias(List.of("autoridad"), ConfigFalacias.Sensibilidad.REGLAS, true);
        EntradaFalacias entrada = new EntradaFalacias(
                "Los que se oponen a las cámaras son los que tienen algo que esconder, así que no hay que escucharlos.", List.of("M1"));

        Resultado<ResultadoFalacias> r = t13.ejecutar(sinAdHominem, entrada, Contextos.sinIa());

        assertThat(r.valor().marcas()).isEmpty();
        assertThat(r.resumen()).isEqualTo("Sin marcas: las reglas no reconocieron ningún esquema.");
    }

    @Test
    void dos_oraciones_con_esquemas_distintos_se_numeran_en_el_orden_del_texto() {
        EntradaFalacias entrada = new EntradaFalacias("O bajamos los precios o cerramos la sucursal. "
                + "El proveedor dice que su harina nueva rinde más, así que conviene cambiarnos.", List.of("M2"));

        ResultadoFalacias v = t13.ejecutar(ConfigFalacias.porDefecto(), entrada, Contextos.sinIa()).valor();

        assertThat(v.marcas()).extracting(ResultadoFalacias.Marca::codigo, ResultadoFalacias.Marca::esquema, ResultadoFalacias.Marca::estado)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("M1", "alternativas", ResultadoFalacias.Estado.PROPUESTA),
                        org.assertj.core.groups.Tuple.tuple("M2", "autoridad", ResultadoFalacias.Estado.CONFIRMADA));
        // Las posiciones apuntan al fragmento dentro del texto, para pintarlo en su lugar.
        ResultadoFalacias.Marca m2 = v.marcas().get(1);
        assertThat(entrada.texto().substring(m2.inicio(), m2.fin())).isEqualTo(m2.fragmento());
        assertThat(v.resumen()).isEqualTo("2 marcas: 1 falacia confirmada (apelación a una autoridad interesada) y 1 esquema con preguntas sin responder.");
    }
}
