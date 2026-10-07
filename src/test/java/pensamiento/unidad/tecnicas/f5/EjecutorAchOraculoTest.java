package pensamiento.unidad.tecnicas.f5;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import pensamiento.catalogo.CatalogoJson;
import pensamiento.catalogo.MapeadorJson;
import pensamiento.nucleo.AfirmacionConRol;
import pensamiento.nucleo.Ejemplo;
import pensamiento.nucleo.OrigenAfirmacion;
import pensamiento.nucleo.Pendiente;
import pensamiento.nucleo.Resultado;
import pensamiento.nucleo.RolAfirmacion;
import pensamiento.nucleo.SentidoAfirmacion;
import pensamiento.nucleo.TipoPendiente;
import pensamiento.tecnicas.f5.ConfigAch;
import pensamiento.tecnicas.f5.EjecutorAch;
import pensamiento.tecnicas.f5.EntradaAch;
import pensamiento.tecnicas.f5.ResultadoAch;
import pensamiento.testutil.builders.Contextos;

/**
 * Oráculo de T28 · Análisis de hipótesis en competencia (ACH): los ejemplos de docs/ejemplos/T28.md, tal como
 * la semilla los transcribe a la tabla ejemplo, deben producir el resultado escrito a mano. El esperado sale
 * del ejemplo, nunca de la fórmula de producción.
 */
class EjecutorAchOraculoTest {

    /** Lo que el ejemplo dice que debe salir, transcrito de la prosa. */
    record Esperado(Map<String, Integer> inconsistencias, List<String> menosRefutadas, boolean empate, List<String> masRefutadas,
                    List<String> evidenciasEnContraDeLaMasRefutada, List<ResultadoAch.Verificacion> verificar,
                    List<String> pendientes, String resumen) {
    }

    private final EjecutorAch ach = new EjecutorAch();

    static Stream<Ejemplo> ejemplos() {
        return new CatalogoJson().ejemplosDe(EjecutorAch.ID).stream();
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("ejemplos")
    void cada_ejemplo_produce_su_resultado_escrito_a_mano(Ejemplo ejemplo) {
        ConfigAch config = MapeadorJson.leer(ejemplo.config(), ConfigAch.class);
        EntradaAch entrada = MapeadorJson.leer(ejemplo.datos(), EntradaAch.class);
        Esperado esperado = MapeadorJson.leer(ejemplo.resultado(), Esperado.class);

        assertThat(ach.validar(config, entrada).errores()).as("el ejemplo es válido").isEmpty();
        Resultado<ResultadoAch> resultado = ach.ejecutar(config, entrada, Contextos.sinIa());
        ResultadoAch valor = resultado.valor();

        Map<String, Integer> inconsistencias = new LinkedHashMap<>();
        valor.hipotesis().forEach(h -> inconsistencias.put(h.codigo(), h.inconsistencias()));
        assertThat(inconsistencias).containsExactlyEntriesOf(esperado.inconsistencias());
        assertThat(valor.menosRefutadas()).containsExactlyElementsOf(esperado.menosRefutadas());
        assertThat(valor.empate()).isEqualTo(esperado.empate());
        assertThat(valor.masRefutadas()).containsExactlyElementsOf(esperado.masRefutadas());
        assertThat(valor.hipotesis(esperado.masRefutadas().getFirst()).evidenciasEnContra())
                .containsExactlyElementsOf(esperado.evidenciasEnContraDeLaMasRefutada());
        assertThat(valor.verificar()).containsExactlyElementsOf(esperado.verificar());
        assertThat(resultado.pendientes()).extracting(Pendiente::descripcion).containsExactlyElementsOf(esperado.pendientes());
        assertThat(resultado.resumen()).isEqualTo(esperado.resumen());
        assertThat(valor.resumen()).isEqualTo(esperado.resumen());
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("ejemplos")
    void las_hipotesis_salen_como_afirmaciones_producidas_por_el_usuario(Ejemplo ejemplo) {
        ConfigAch config = MapeadorJson.leer(ejemplo.config(), ConfigAch.class);
        EntradaAch entrada = MapeadorJson.leer(ejemplo.datos(), EntradaAch.class);

        Resultado<ResultadoAch> resultado = ach.ejecutar(config, entrada, Contextos.sinIa());

        assertThat(resultado.afirmaciones()).hasSize(entrada.hipotesis().size());
        assertThat(resultado.afirmaciones()).allSatisfy(a -> {
            assertThat(a.rol()).isEqualTo(RolAfirmacion.HIPOTESIS);
            assertThat(a.sentido()).isEqualTo(SentidoAfirmacion.PRODUCIDA);
            assertThat(a.origen()).isEqualTo(OrigenAfirmacion.USUARIO);
            assertThat(a.adoptada()).isTrue();
        });
        assertThat(resultado.afirmaciones()).extracting(AfirmacionConRol::texto)
                .containsExactlyElementsOf(entrada.hipotesis().stream().map(EntradaAch.Hipotesis::texto).toList());
        // El JSONB guarda identificadores, nunca copias: cada hipótesis del valor apunta a su afirmación.
        assertThat(resultado.valor().hipotesis()).extracting(ResultadoAch.HipotesisEvaluada::afirmacionId)
                .containsExactlyElementsOf(resultado.afirmaciones().stream().map(AfirmacionConRol::afirmacionId).toList());
        assertThat(resultado.pendientes()).allSatisfy(p -> {
            assertThat(p.tipo()).isEqualTo(TipoPendiente.VERIFICACION);
            assertThat(p.objetoId()).isPresent();
        });
        assertThat(resultado.versionEsquema()).isEqualTo(1);
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("ejemplos")
    void el_resultado_hace_ida_y_vuelta_por_el_jsonb_sin_perder_nada(Ejemplo ejemplo) {
        ConfigAch config = MapeadorJson.leer(ejemplo.config(), ConfigAch.class);
        EntradaAch entrada = MapeadorJson.leer(ejemplo.datos(), EntradaAch.class);
        ResultadoAch valor = ach.ejecutar(config, entrada, Contextos.sinIa()).valor();

        assertThat(MapeadorJson.leer(MapeadorJson.escribir(valor), ResultadoAch.class)).isEqualTo(valor);
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("ejemplos")
    void la_tarjeta_nunca_dice_confirmada_ni_verdadera(Ejemplo ejemplo) {
        ConfigAch config = MapeadorJson.leer(ejemplo.config(), ConfigAch.class);
        EntradaAch entrada = MapeadorJson.leer(ejemplo.datos(), EntradaAch.class);
        Resultado<ResultadoAch> resultado = ach.ejecutar(config, entrada, Contextos.sinIa());

        String todo = (resultado.resumen() + " " + String.join(" ", resultado.pendientes().stream().map(Pendiente::descripcion).toList())).toLowerCase();
        assertThat(todo).doesNotContain("confirmada", "verdadera", "correcta");
    }

    @Test
    void hay_tres_ejemplos_de_ambito_distinto_y_un_caso_adicional_de_trabajo() {
        List<Ejemplo> ejemplos = ejemplos().toList();
        assertThat(ejemplos).extracting(Ejemplo::titulo)
                .containsExactly("Las ventas de los sábados", "La asamblea vacía", "La factura de luz", "El pan quemado");
        assertThat(ejemplos.subList(0, 3)).extracting(Ejemplo::ambito)
                .containsExactly(Ejemplo.Ambito.TRABAJO, Ejemplo.Ambito.COMUNIDAD, Ejemplo.Ambito.PERSONAL);
        assertThat(ejemplos).extracting(Ejemplo::orden).containsExactly(1, 2, 3, 4);
    }

}
