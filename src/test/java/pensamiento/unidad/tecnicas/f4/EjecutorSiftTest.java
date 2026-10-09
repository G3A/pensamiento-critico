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
import pensamiento.nucleo.RolAfirmacion;
import pensamiento.nucleo.TipoPendiente;
import pensamiento.nucleo.Validacion;
import pensamiento.tecnicas.f4.EjecutorSift;
import pensamiento.tecnicas.f4.ResultadoSift;
import pensamiento.testutil.builders.Contextos;

/** T19 · SIFT: el oráculo con los ejemplos de docs/ejemplos/T19.md y las reglas de validación de las notas. */
class EjecutorSiftTest {

    record Esperado(List<String> pasos, String senal, String motivo, List<String> pendientes, String resumen) {
    }

    private final EjecutorSift t19 = new EjecutorSift();

    static Stream<Ejemplo> ejemplos() {
        return new CatalogoJson().ejemplosDe(EjecutorSift.ID).stream();
    }

    @Test
    void hay_tres_ejemplos_uno_por_ambito() {
        assertThat(ejemplos().map(Ejemplo::ambito)).containsExactlyInAnyOrder(Ejemplo.Ambito.PERSONAL, Ejemplo.Ambito.TRABAJO, Ejemplo.Ambito.COMUNIDAD);
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("ejemplos")
    void cada_ejemplo_produce_lo_escrito_a_mano(Ejemplo ejemplo) {
        EjecutorSift.Config config = MapeadorJson.leer(ejemplo.config(), EjecutorSift.Config.class);
        EjecutorSift.Entrada entrada = MapeadorJson.leer(ejemplo.datos(), EjecutorSift.Entrada.class);
        Esperado esperado = MapeadorJson.leer(ejemplo.resultado(), Esperado.class);

        assertThat(t19.validar(config, entrada).errores()).isEmpty();
        Resultado<ResultadoSift> r = t19.ejecutar(config, entrada, Contextos.sinIa());

        assertThat(r.valor().pasos()).extracting(ResultadoSift.PasoEvaluado::linea).containsExactlyElementsOf(esperado.pasos());
        assertThat(r.valor().senal()).isEqualTo(esperado.senal());
        assertThat(r.valor().motivo()).isEqualTo(esperado.motivo());
        assertThat(r.pendientes()).extracting(Pendiente::descripcion).containsExactlyElementsOf(esperado.pendientes());
        assertThat(r.pendientes()).allSatisfy(p -> assertThat(p.tipo()).isEqualTo(TipoPendiente.VERIFICACION));
        assertThat(r.resumen()).isEqualTo(esperado.resumen());
        assertThat(r.resumen().toLowerCase()).doesNotContain("verdader");
        assertThat(r.afirmaciones()).singleElement().satisfies(a -> assertThat(a.rol()).isEqualTo(RolAfirmacion.HIPOTESIS));
        assertThat(MapeadorJson.leer(MapeadorJson.escribir(r.valor()), ResultadoSift.class)).isEqualTo(r.valor());
    }

    @Test
    void con_notas_obligatorias_un_paso_hecho_sin_nota_no_pasa() {
        EjecutorSift.Config config = new EjecutorSift.Config(List.of(EjecutorSift.Paso.INVESTIGA), 5, true);
        EjecutorSift.Entrada entrada = new EjecutorSift.Entrada("Las cámaras redujeron 70% los robos.", "Grupo del barrio", false,
                EjecutorSift.Interes.INTERESADA, " ", null, null, null, null);

        Validacion v = t19.validar(config, entrada);

        assertThat(v.errores()).extracting(Validacion.Error::mensaje).containsExactly("Escribe lo que encontraste en «Investiga la fuente».");
    }

    @Test
    void los_pasos_apagados_se_ignoran_aunque_traigan_respuesta() {
        EjecutorSift.Config config = new EjecutorSift.Config(List.of(EjecutorSift.Paso.DETENTE), 5, true);
        EjecutorSift.Entrada entrada = new EjecutorSift.Entrada("El titular.", "Un grupo", true, EjecutorSift.Interes.INTERESADA, "Vende cámaras.",
                EjecutorSift.Cobertura.CONTRADICE, "Otros dicen otra cosa.", EjecutorSift.Origen.DISTINTO, "El original no lo dice.");

        Resultado<ResultadoSift> r = t19.ejecutar(config, entrada, Contextos.sinIa());

        assertThat(r.valor().senal()).isEqualTo("sin alertas");
        assertThat(r.valor().motivo()).isEqualTo("El paso activo no encontró alertas: si lo compartes, enlaza el original.");
        assertThat(r.resumen()).isEqualTo("Sin alertas · 1 de 1 pasos hechos.");
    }

    @Test
    void sin_alertas_pero_con_pasos_pendientes_la_ficha_queda_incompleta() {
        EjecutorSift.Config config = new EjecutorSift.Config(List.of(EjecutorSift.Paso.values()), 5, false);
        EjecutorSift.Entrada entrada = new EjecutorSift.Entrada("El titular.", "Un grupo", true, EjecutorSift.Interes.INDEPENDIENTE, null,
                null, null, null, null);

        Resultado<ResultadoSift> r = t19.ejecutar(config, entrada, Contextos.sinIa());

        assertThat(r.valor().senal()).isEqualTo("incompleta");
        assertThat(r.valor().motivo()).isEqualTo("Faltan pasos: Encuentra mejor cobertura y Rastrea el origen.");
        assertThat(r.pendientes()).extracting(Pendiente::descripcion)
                .containsExactly("Buscar mejor cobertura de: El titular.", "Rastrear el origen de: El titular.");
    }
}
