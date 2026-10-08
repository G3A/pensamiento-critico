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
import pensamiento.nucleo.ConModelo;
import pensamiento.nucleo.Ejemplo;
import pensamiento.nucleo.OrigenAfirmacion;
import pensamiento.nucleo.Pendiente;
import pensamiento.nucleo.Resultado;
import pensamiento.tecnicas.f1.EjecutorAnalogia;
import pensamiento.tecnicas.f1.ResultadoAnalogia;
import pensamiento.testutil.builders.Contextos;
import pensamiento.testutil.fakes.FakeIa;

/**
 * T07 · Razonamiento por analogía: el oráculo con los ejemplos de docs/ejemplos/T07.md y, con el Fake de Ia, el
 * ejemplo 3 con la diferencia del modelo, que no cuenta hasta adoptarse.
 */
class EjecutorAnalogiaTest {

    record Esperado(String fuerza, String motivo, List<String> pendientes, String resumen) {
    }

    private static final String PROPUESTA = "El barrio de al lado contrató vigilancia privada al mismo tiempo que puso las cámaras.";

    private final EjecutorAnalogia t07 = new EjecutorAnalogia();

    static Stream<Ejemplo> ejemplos() {
        return new CatalogoJson().ejemplosDe(EjecutorAnalogia.ID).stream();
    }

    @Test
    void hay_tres_ejemplos_uno_por_ambito() {
        assertThat(ejemplos().map(Ejemplo::ambito)).containsExactlyInAnyOrder(Ejemplo.Ambito.PERSONAL, Ejemplo.Ambito.TRABAJO, Ejemplo.Ambito.COMUNIDAD);
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("ejemplos")
    void cada_ejemplo_produce_lo_escrito_a_mano(Ejemplo ejemplo) {
        EjecutorAnalogia.Config config = MapeadorJson.leer(ejemplo.config(), EjecutorAnalogia.Config.class);
        EjecutorAnalogia.Entrada entrada = MapeadorJson.leer(ejemplo.datos(), EjecutorAnalogia.Entrada.class);
        Esperado esperado = MapeadorJson.leer(ejemplo.resultado(), Esperado.class);

        assertThat(t07.validar(config, entrada).errores()).isEmpty();
        Resultado<ResultadoAnalogia> r = t07.ejecutar(config, entrada, Contextos.sinIa());

        assertThat(r.valor().fuerza().toString()).isEqualTo(esperado.fuerza());
        assertThat(r.valor().motivo()).isEqualTo(esperado.motivo());
        assertThat(r.pendientes()).extracting(Pendiente::descripcion).containsExactlyElementsOf(esperado.pendientes());
        assertThat(r.resumen()).isEqualTo(esperado.resumen());
        assertThat(MapeadorJson.leer(MapeadorJson.escribir(r.valor()), ResultadoAnalogia.class)).isEqualTo(r.valor());
    }

    @Test
    void la_diferencia_del_modelo_no_cuenta_hasta_adoptarse_y_nunca_se_marca_clave_sola() {
        Ejemplo camaras = ejemplos().filter(e -> e.titulo().equals("Las cámaras del barrio de al lado")).findFirst().orElseThrow();
        EjecutorAnalogia.Config config = MapeadorJson.leer(camaras.config(), EjecutorAnalogia.Config.class);
        EjecutorAnalogia.Entrada entrada = MapeadorJson.leer(camaras.datos(), EjecutorAnalogia.Entrada.class);
        FakeIa ia = new FakeIa();
        ia.programarRespuesta(PROPUESTA);

        ConModelo.Propuestas propuestas = t07.proponer(config, entrada, Contextos.conIa(ia), t -> { }, 1);
        assertThat(ia.chatsRecibidos().getFirst().mensajes().getLast().contenido())
                .contains("«Los dos barrios tienen calles parecidas.»").contains("Diferencias: (ninguna)");
        EjecutorAnalogia.Entrada conPropuesta = new EjecutorAnalogia.Entrada(entrada.caso(), entrada.conclusion(), entrada.similitudes(),
                entrada.diferencias(), propuestas.nuevas());

        Resultado<ResultadoAnalogia> sinAdoptar = t07.ejecutar(config, conPropuesta, Contextos.sinIa());
        assertThat(sinAdoptar.resumen()).isEqualTo("2 similitudes y 0 diferencias · fuerza incompleta.");
        assertThat(sinAdoptar.afirmaciones()).filteredOn(a -> a.origen() == OrigenAfirmacion.MODELO).allMatch(a -> !a.cuenta());

        Resultado<ResultadoAnalogia> adoptada = t07.ejecutar(config, t07.adoptar(conPropuesta, "IA1"), Contextos.sinIa());
        assertThat(adoptada.resumen()).isEqualTo("2 similitudes y 1 diferencia · fuerza fuerte.");
        assertThat(adoptada.valor().motivo())
                .isEqualTo("2 similitudes contra 1 diferencia y ninguna clave. Revisa que las similitudes importen para la conclusión.");
        assertThat(adoptada.valor().diferencias()).singleElement().satisfies(d -> {
            assertThat(d.delModelo()).isTrue();
            assertThat(d.clave()).isFalse();
        });
        assertThat(adoptada.afirmaciones()).filteredOn(a -> a.origen() == OrigenAfirmacion.MODELO).singleElement()
                .extracting(AfirmacionConRol::cuenta).isEqualTo(true);
    }

    @Test
    void sin_exigir_la_verificacion_una_clave_sin_verificar_deja_la_fuerza_media() {
        EjecutorAnalogia.Config config = new EjecutorAnalogia.Config(1, 1, false, EjecutorAnalogia.Modo.PLANTILLAS);
        EjecutorAnalogia.Entrada entrada = new EjecutorAnalogia.Entrada("A mi vecina le sirvió la dieta.", "A mí también me servirá.",
                List.of(new EjecutorAnalogia.Similitud("Misma edad.")),
                List.of(new EjecutorAnalogia.Diferencia("Ella hace ejercicio todos los días.", true, false, null)), List.of());
        ResultadoAnalogia r = t07.ejecutar(config, entrada, Contextos.sinIa()).valor();
        assertThat(r.fuerza()).isEqualTo(ResultadoAnalogia.Fuerza.MEDIA);
        assertThat(r.motivo()).isEqualTo("La diferencia clave «Ella hace ejercicio todos los días.» puede romper la analogía.");
    }
}
