package pensamiento.unidad.tecnicas.f1;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import pensamiento.argdown.ParserArgdown;
import pensamiento.catalogo.CatalogoJson;
import pensamiento.catalogo.MapeadorJson;
import pensamiento.nucleo.Argumento;
import pensamiento.nucleo.Ejemplo;
import pensamiento.nucleo.Pendiente;
import pensamiento.nucleo.Resultado;
import pensamiento.nucleo.TipoPendiente;
import pensamiento.tecnicas.f1.EjecutorPremisasOcultas;
import pensamiento.tecnicas.f1.ResultadoMapa;
import pensamiento.testutil.builders.Contextos;

/** Oráculo de T06 · Reconstrucción de premisas ocultas: los ejemplos de docs/ejemplos/T06.md. */
class EjecutorPremisasOcultasOraculoTest {

    record NodoEsperado(String codigo, String texto, String rol) {
    }

    record ArgumentoEsperado(String codigo, String titulo, String sentido, int peso, String conclusion, List<String> premisas,
                             boolean aplicable, String motivo) {
    }

    record Esperado(String argdown, List<NodoEsperado> nodos, List<ArgumentoEsperado> argumentos, List<String> pendientes, String resumen) {
    }

    private final EjecutorPremisasOcultas t06 = new EjecutorPremisasOcultas(new ParserArgdown());

    static Stream<Ejemplo> ejemplos() {
        return new CatalogoJson().ejemplosDe(EjecutorPremisasOcultas.ID).stream();
    }

    @Test
    void hay_tres_ejemplos_uno_por_ambito() {
        assertThat(ejemplos().map(Ejemplo::ambito)).containsExactlyInAnyOrder(Ejemplo.Ambito.PERSONAL, Ejemplo.Ambito.TRABAJO, Ejemplo.Ambito.COMUNIDAD);
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("ejemplos")
    void cada_ejemplo_marca_la_premisa_oculta_como_se_escribio_a_mano(Ejemplo ejemplo) {
        EjecutorPremisasOcultas.Config config = MapeadorJson.leer(ejemplo.config(), EjecutorPremisasOcultas.Config.class);
        EjecutorPremisasOcultas.Entrada entrada = MapeadorJson.leer(ejemplo.datos(), EjecutorPremisasOcultas.Entrada.class);
        Esperado esperado = MapeadorJson.leer(ejemplo.resultado(), Esperado.class);

        assertThat(t06.validar(config, entrada).errores()).as("el ejemplo es válido").isEmpty();
        Resultado<ResultadoMapa> r = t06.ejecutar(config, entrada, Contextos.sinIa());
        ResultadoMapa v = r.valor();

        assertThat(v.argdown()).isEqualTo(esperado.argdown());
        assertThat(v.nodos()).extracting(n -> new NodoEsperado(n.codigo(), n.texto(), n.rol().toString())).containsExactlyElementsOf(esperado.nodos());
        assertThat(v.argumentos()).extracting(a -> new ArgumentoEsperado(a.codigo(), a.titulo(), a.sentido().toString(), a.peso(), a.conclusion(),
                a.premisas(), a.aplicable(), a.motivo())).containsExactlyElementsOf(esperado.argumentos());
        assertThat(r.pendientes()).extracting(Pendiente::descripcion).containsExactlyElementsOf(esperado.pendientes());
        assertThat(r.pendientes()).extracting(Pendiente::tipo).containsOnly(TipoPendiente.VERIFICACION);
        assertThat(r.resumen()).isEqualTo(esperado.resumen()).isEqualTo(v.resumen());
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("ejemplos")
    void las_premisas_ocultas_se_guardan_como_asumibles_y_las_explicitas_no(Ejemplo ejemplo) {
        EjecutorPremisasOcultas.Config config = MapeadorJson.leer(ejemplo.config(), EjecutorPremisasOcultas.Config.class);
        EjecutorPremisasOcultas.Entrada entrada = MapeadorJson.leer(ejemplo.datos(), EjecutorPremisasOcultas.Entrada.class);

        Resultado<ResultadoMapa> r = t06.ejecutar(config, entrada, Contextos.sinIa());

        List<Argumento.Premisa> premisas = r.argumentos().getFirst().argumento().premisas();
        assertThat(premisas).extracting(Argumento.Premisa::asumible).containsExactlyElementsOf(
                Stream.concat(entrada.premisas().stream().map(p -> false), entrada.ocultas().stream().map(p -> true)).toList());
        assertThat(premisas).extracting(Argumento.Premisa::orden).containsExactlyElementsOf(
                java.util.stream.IntStream.rangeClosed(1, premisas.size()).boxed().toList());
    }

    @Test
    void exigir_premisa_oculta_sin_escribir_ninguna_dice_que_hace_falta_junto_al_campo() {
        var validacion = t06.validar(new EjecutorPremisasOcultas.Config(true, 2), new EjecutorPremisasOcultas.Entrada("Venderé más en el centro.",
                List.of(new EjecutorPremisasOcultas.Texto("Por el centro pasa más gente.")), List.of()));

        assertThat(validacion.errores()).singleElement().satisfies(e -> {
            assertThat(e.campo()).isEqualTo("ocultas");
            assertThat(e.mensaje()).isEqualTo("Escribe al menos una premisa oculta: ¿qué das por sentado para pasar de las premisas a la conclusión?");
        });
    }

    @Test
    void mas_premisas_ocultas_que_el_maximo_configurado_es_un_error() {
        var validacion = t06.validar(new EjecutorPremisasOcultas.Config(false, 1), new EjecutorPremisasOcultas.Entrada("Hay que cambiar al niño de colegio.",
                List.of(new EjecutorPremisasOcultas.Texto("Sus notas bajaron.")),
                List.of(new EjecutorPremisasOcultas.Texto("Fue culpa del colegio."), new EjecutorPremisasOcultas.Texto("En otro le iría mejor."))));

        assertThat(validacion.errores()).extracting(e -> e.campo() + ": " + e.mensaje())
                .containsExactly("ocultas: Tu configuración admite como máximo 1 premisas ocultas.");
    }

    @Test
    void un_texto_que_empieza_con_signo_no_se_puede_escribir_en_argdown_y_lo_dice() {
        var validacion = t06.validar(new EjecutorPremisasOcultas.Config(false, 1), new EjecutorPremisasOcultas.Entrada("-3 grados es mucho frío.",
                List.of(new EjecutorPremisasOcultas.Texto("Anoche heló.")), List.of()));

        assertThat(validacion.errores()).extracting(e -> e.campo()).containsExactly("conclusion");
    }
}
