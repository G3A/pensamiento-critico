package pensamiento.unidad.tecnicas.f6;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import pensamiento.nucleo.AfirmacionConRol;
import pensamiento.nucleo.ConModelo;
import pensamiento.nucleo.Ejecucion;
import pensamiento.nucleo.OrigenAfirmacion;
import pensamiento.nucleo.Pendiente;
import pensamiento.nucleo.Propuesta;
import pensamiento.nucleo.Resultado;
import pensamiento.tecnicas.comun.ModeloLocal;
import pensamiento.tecnicas.f6.EjecutorSteelman;
import pensamiento.tecnicas.f6.EjecutorSteelman.Config;
import pensamiento.tecnicas.f6.EjecutorSteelman.Entrada;
import pensamiento.tecnicas.f6.EjecutorSteelman.Modo;
import pensamiento.tecnicas.f6.ResultadoSteelman;
import pensamiento.testutil.builders.Contextos;
import pensamiento.testutil.fakes.FakeIa;

/**
 * T34 · Steelmanning con el modelo (RF-14), con el Fake certificado de Ia: el ejemplo 1 de docs/ejemplos/T34.md. La
 * propuesta del modelo aparece sin contar, se adopta y entonces cuenta; sin Ollama la técnica sigue en modo manual.
 */
class EjecutorSteelmanConModeloTest {

    private static final String PROPUESTA = "Las cámaras cuestan, vigilan a los vecinos honestos todo el día, y no hay evidencia local "
            + "de que bajen los robos en vez de moverlos.";
    private static final Config CONFIG = new Config(120, Modo.MANUAL_Y_MODELO, true);
    private static final Entrada SIN_STEELMAN = new Entrada("Los que no quieren cámaras no les importa el barrio.",
            "Prefiero que no me graben cada vez que salgo de mi casa.", null, null, List.of(), List.of());

    private final EjecutorSteelman t34 = new EjecutorSteelman();

    @Test
    void la_propuesta_del_modelo_llega_con_tokens_provisionales_sin_adoptar_y_con_su_registro() {
        FakeIa ia = new FakeIa();
        ia.programarRespuesta(PROPUESTA);
        List<String> tokens = new ArrayList<>();

        ConModelo.Propuestas propuestas = t34.proponer(CONFIG, SIN_STEELMAN, Contextos.conIa(ia), tokens::add, 1);

        assertThat(propuestas.caida()).isEmpty();
        assertThat(tokens).isNotEmpty();
        assertThat(String.join("", tokens).strip()).isEqualTo(PROPUESTA);
        assertThat(propuestas.nuevas()).singleElement().satisfies(p -> {
            assertThat(p.codigo()).isEqualTo("IA1");
            assertThat(p.valor()).isEqualTo(PROPUESTA);
            assertThat(p.adoptada()).isFalse();
            assertThat(p.modelo()).isEqualTo("qwen3:4b");
            assertThat(p.prompt()).isEqualTo("t34-steelman.v1");
        });
        assertThat(ia.chatsRecibidos()).singleElement().satisfies(c -> {
            assertThat(c.reintentosMaximos()).as("máximo dos reintentos").isEqualTo(2);
            assertThat(c.mensajes().getFirst().contenido()).contains("No pases de 120 palabras").contains("Ejemplo 3");
            assertThat(c.mensajes().getLast().contenido()).contains("«Los que no quieren cámaras no les importa el barrio.»");
        });
    }

    @Test
    void sin_adoptar_la_propuesta_no_cuenta_y_adoptada_cuenta() {
        Propuesta ia1 = new Propuesta("IA1", "steelman", "Steelman propuesto", PROPUESTA, "", false, "qwen3:4b", "sha256:fake", "t34-steelman.v1");
        Entrada conPropuesta = new Entrada(SIN_STEELMAN.posturaOriginal(), SIN_STEELMAN.cita(), null, null, List.of(), List.of(ia1));

        Resultado<ResultadoSteelman> sinAdoptar = t34.ejecutar(CONFIG, conPropuesta, Contextos.sinIa());
        assertThat(sinAdoptar.resumen()).isEqualTo("Falta el steelman.");
        assertThat(sinAdoptar.valor().estado()).isEqualTo(ResultadoSteelman.Estado.INCOMPLETO);
        assertThat(sinAdoptar.pendientes()).isEmpty();
        assertThat(sinAdoptar.afirmaciones()).singleElement().satisfies(a -> {
            assertThat(a.origen()).isEqualTo(OrigenAfirmacion.MODELO);
            assertThat(a.adoptada()).isFalse();
            assertThat(a.cuenta()).isFalse();
        });
        assertThat(sinAdoptar.modelo()).contains(new Ejecucion.RegistroModelo("qwen3:4b", "sha256:fake", "t34-steelman.v1", 0.0, 42L));

        Resultado<ResultadoSteelman> adoptada = t34.ejecutar(CONFIG, t34.adoptar(conPropuesta, "IA1"), Contextos.sinIa());
        assertThat(adoptada.resumen()).isEqualTo("Steelman de 25 palabras · 0 razones añadidas · por confirmar.");
        assertThat(adoptada.valor().delModelo()).isTrue();
        assertThat(adoptada.pendientes()).extracting(Pendiente::descripcion).containsExactly("Responder al steelman: " + PROPUESTA);
        assertThat(adoptada.afirmaciones()).singleElement().satisfies(a -> {
            assertThat(a.origen()).isEqualTo(OrigenAfirmacion.MODELO);
            assertThat(a.adoptada()).isTrue();
        });
        assertThat(adoptada.afirmaciones()).allMatch(AfirmacionConRol::cuenta);
    }

    @Test
    void adoptar_dos_veces_o_un_codigo_que_no_existe_no_se_puede() {
        Propuesta ia1 = new Propuesta("IA1", "steelman", "Steelman propuesto", PROPUESTA, "", false, "qwen3:4b", "sha256:fake", "t34-steelman.v1");
        Entrada adoptada = t34.adoptar(new Entrada("x", null, null, null, List.of(), List.of(ia1)), "IA1");
        assertThatThrownBy(() -> t34.adoptar(adoptada, "IA1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> t34.adoptar(adoptada, "IA9")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void una_respuesta_con_caricatura_se_reintenta_y_vale_la_siguiente() {
        FakeIa ia = new FakeIa();
        ia.programarRespuesta("A los que se oponen solo les interesa su comodidad.");
        ia.programarRespuesta(PROPUESTA);
        ConModelo.Propuestas propuestas = t34.proponer(CONFIG, SIN_STEELMAN, Contextos.conIa(ia), t -> { }, 1);
        assertThat(propuestas.nuevas()).extracting(Propuesta::valor).containsExactly(PROPUESTA);
    }

    @Test
    void sin_ollama_o_si_se_agota_el_tiempo_sigue_en_modo_manual_y_lo_dice() {
        FakeIa apagada = new FakeIa();
        apagada.apagar();
        FakeIa lenta = new FakeIa();
        lenta.hacerLento();
        FakeIa terca = new FakeIa();
        terca.programarRespuesta("¿Por qué no quieren?");
        terca.programarRespuesta("¿Y si no?");
        terca.programarRespuesta("¿Seguro?");

        assertThat(t34.proponer(CONFIG, SIN_STEELMAN, Contextos.sinIa(), t -> { }, 1).caida()).contains(ModeloLocal.NO_DISPONIBLE);
        assertThat(t34.proponer(CONFIG, SIN_STEELMAN, Contextos.conIa(apagada), t -> { }, 1).caida()).contains(ModeloLocal.NO_DISPONIBLE);
        assertThat(t34.proponer(CONFIG, SIN_STEELMAN, Contextos.conIa(lenta), t -> { }, 1).caida()).contains(ModeloLocal.TIEMPO_AGOTADO);
        assertThat(t34.proponer(CONFIG, SIN_STEELMAN, Contextos.conIa(terca), t -> { }, 1).caida()).contains(ModeloLocal.RESPUESTA_INVALIDA);
        assertThat(t34.ejecutar(CONFIG, SIN_STEELMAN, Contextos.sinIa()).resumen()).isEqualTo("Falta el steelman.");
    }

    @Test
    void la_numeracion_sigue_despues_de_las_propuestas_que_ya_hay() {
        FakeIa ia = new FakeIa();
        ia.programarRespuesta(PROPUESTA);
        assertThat(t34.proponer(CONFIG, SIN_STEELMAN, Contextos.conIa(ia), t -> { }, 3).nuevas()).extracting(Propuesta::codigo).containsExactly("IA3");
        assertThat(t34.usaModelo(CONFIG)).isTrue();
        assertThat(t34.usaModelo(new Config(120, Modo.MANUAL, true))).isFalse();
        assertThat(Optional.of(t34)).get().isInstanceOf(ConModelo.class);
    }
}
