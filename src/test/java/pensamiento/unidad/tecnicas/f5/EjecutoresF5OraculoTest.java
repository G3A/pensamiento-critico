package pensamiento.unidad.tecnicas.f5;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import pensamiento.catalogo.CatalogoJson;
import pensamiento.catalogo.MapeadorJson;
import pensamiento.nucleo.Ejecutor;
import pensamiento.nucleo.Ejemplo;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Pendiente;
import pensamiento.nucleo.Resultado;
import pensamiento.nucleo.RolAfirmacion;
import pensamiento.nucleo.TipoPendiente;
import pensamiento.tecnicas.f5.EjecutorBayes;
import pensamiento.tecnicas.f5.EjecutorCalibracion;
import pensamiento.tecnicas.f5.EjecutorDiarioDecisiones;
import pensamiento.tecnicas.f5.EjecutorFermi;
import pensamiento.tecnicas.f5.EjecutorInversion;
import pensamiento.tecnicas.f5.EjecutorMatrizPonderada;
import pensamiento.tecnicas.f5.EjecutorMejorExplicacion;
import pensamiento.tecnicas.f5.EjecutorPremortem;
import pensamiento.tecnicas.f5.EjecutorValorEsperado;
import pensamiento.tecnicas.f5.ResultadoBayes;
import pensamiento.tecnicas.f5.ResultadoCalibracion;
import pensamiento.tecnicas.f5.ResultadoDiario;
import pensamiento.tecnicas.f5.ResultadoFermi;
import pensamiento.tecnicas.f5.ResultadoInversion;
import pensamiento.tecnicas.f5.ResultadoMatriz;
import pensamiento.tecnicas.f5.ResultadoPremortem;
import pensamiento.tecnicas.f5.ResultadoValorEsperado;
import pensamiento.testutil.builders.Contextos;

/**
 * Oráculo de T24 a T27 y T29 a T33 con los ejemplos de docs/ejemplos/, leídos del catálogo: el cálculo hecho a mano en la
 * prosa es el valor esperado, nunca recalculado con la fórmula de producción. Hoy, en las pruebas, es 7 de octubre de 2026.
 */
class EjecutoresF5OraculoTest {

    private static final CatalogoJson CATALOGO = new CatalogoJson();

    record ResumenYPendientes(String resumen, List<String> pendientes) {
    }

    static Stream<IdTecnica> tecnicas() {
        return Stream.of(EjecutorBayes.ID, EjecutorCalibracion.ID, EjecutorFermi.ID, EjecutorValorEsperado.ID, EjecutorPremortem.ID,
                EjecutorInversion.ID, EjecutorMatrizPonderada.ID, EjecutorDiarioDecisiones.ID, EjecutorMejorExplicacion.ID);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("tecnicas")
    void cada_tecnica_tiene_tres_ejemplos_uno_por_ambito(IdTecnica id) {
        assertThat(CATALOGO.ejemplosDe(id)).extracting(Ejemplo::ambito)
                .containsExactlyInAnyOrder(Ejemplo.Ambito.PERSONAL, Ejemplo.Ambito.TRABAJO, Ejemplo.Ambito.COMUNIDAD);
    }

    private static <C, E, R> Resultado<R> correr(Ejecutor<C, E, R> ejecutor, Ejemplo ejemplo) {
        C config = MapeadorJson.leer(ejemplo.config(), ejecutor.tipos().config());
        E entrada = MapeadorJson.leer(ejemplo.datos(), ejecutor.tipos().entrada());
        assertThat(ejecutor.validar(config, entrada).errores()).as(ejemplo.titulo()).isEmpty();
        Resultado<R> r = ejecutor.ejecutar(config, entrada, Contextos.sinIa());
        ResumenYPendientes esperado = MapeadorJson.leer(ejemplo.resultado(), ResumenYPendientes.class);
        assertThat(r.resumen()).as(ejemplo.titulo()).isEqualTo(esperado.resumen());
        assertThat(r.pendientes()).extracting(Pendiente::descripcion).as(ejemplo.titulo()).containsExactlyElementsOf(esperado.pendientes());
        assertThat(MapeadorJson.leer(MapeadorJson.escribir(r.valor()), ejecutor.tipos().resultado())).as("ida y vuelta por JSON").isEqualTo(r.valor());
        return r;
    }

    record EsperadoT24(List<Integer> posteriores, List<String> razones, List<String> odds, List<String> avisos) {
    }

    @Test
    void t24_cada_ejemplo_da_los_posteriores_razones_y_odds_calculados_a_mano() {
        EjecutorBayes t24 = new EjecutorBayes();
        for (Ejemplo e : CATALOGO.ejemplosDe(EjecutorBayes.ID)) {
            ResultadoBayes r = correr(t24, e).valor();
            EsperadoT24 esperado = MapeadorJson.leer(e.resultado(), EsperadoT24.class);
            assertThat(r.pasos()).extracting(ResultadoBayes.Paso::posterior).as(e.titulo()).containsExactlyElementsOf(esperado.posteriores());
            assertThat(r.pasos()).extracting(ResultadoBayes.Paso::razon).as(e.titulo()).containsExactlyElementsOf(esperado.razones());
            assertThat(r.pasos()).extracting(ResultadoBayes.Paso::odds).as(e.titulo()).containsExactlyElementsOf(esperado.odds());
            assertThat(r.avisos()).as(e.titulo()).containsExactlyElementsOf(esperado.avisos());
        }
    }

    record EsperadoT25(String puntaje, List<ResultadoCalibracion.Tramo> tramos, List<String> avisos) {
    }

    @Test
    void t25_cada_ejemplo_da_el_puntaje_la_curva_y_los_avisos_calculados_a_mano() {
        EjecutorCalibracion t25 = new EjecutorCalibracion();
        for (Ejemplo e : CATALOGO.ejemplosDe(EjecutorCalibracion.ID)) {
            Resultado<ResultadoCalibracion> r = correr(t25, e);
            EsperadoT25 esperado = MapeadorJson.leer(e.resultado(), EsperadoT25.class);
            assertThat(r.valor().puntaje()).as(e.titulo()).isEqualTo(esperado.puntaje());
            assertThat(r.valor().tramos()).as(e.titulo()).containsExactlyElementsOf(esperado.tramos());
            assertThat(r.valor().avisos()).as(e.titulo()).containsExactlyElementsOf(esperado.avisos());
            assertThat(r.afirmaciones()).as("T25 no produce afirmaciones: la confianza ya está declarada").isEmpty();
        }
    }

    record EsperadoT26(String minimo, String maximo, String central, List<String> rangos, String masAncho, String referencia, List<String> avisos) {
    }

    @Test
    void t26_cada_ejemplo_da_el_rango_el_valor_central_y_el_factor_mas_incierto() {
        EjecutorFermi t26 = new EjecutorFermi();
        for (Ejemplo e : CATALOGO.ejemplosDe(EjecutorFermi.ID)) {
            ResultadoFermi r = correr(t26, e).valor();
            EsperadoT26 esperado = MapeadorJson.leer(e.resultado(), EsperadoT26.class);
            assertThat(List.of(r.minimo(), r.maximo(), r.central())).as(e.titulo())
                    .containsExactly(esperado.minimo(), esperado.maximo(), esperado.central());
            assertThat(r.factores()).extracting(ResultadoFermi.FactorEn::rango).as(e.titulo()).containsExactlyElementsOf(esperado.rangos());
            assertThat(r.factores()).filteredOn(ResultadoFermi.FactorEn::masAncho).extracting(ResultadoFermi.FactorEn::codigo).as(e.titulo())
                    .containsExactly(esperado.masAncho());
            assertThat(r.referencia()).as(e.titulo()).isEqualTo(esperado.referencia());
            assertThat(r.avisos()).as(e.titulo()).containsExactlyElementsOf(esperado.avisos());
        }
    }

    record FilaT27(int puesto, String opcion, String valor, Integer peorCaso, boolean empate) {
    }

    record EsperadoT27(List<FilaT27> ranking, List<String> avisos) {
    }

    @Test
    void t27_cada_ejemplo_da_el_ranking_con_valores_peor_caso_y_empates() {
        EjecutorValorEsperado t27 = new EjecutorValorEsperado();
        for (Ejemplo e : CATALOGO.ejemplosDe(EjecutorValorEsperado.ID)) {
            ResultadoValorEsperado r = correr(t27, e).valor();
            EsperadoT27 esperado = MapeadorJson.leer(e.resultado(), EsperadoT27.class);
            assertThat(r.ranking()).extracting(f -> new FilaT27(f.puesto(), f.opcion(), f.valor(), f.peorCaso(), f.empate())).as(e.titulo())
                    .containsExactlyElementsOf(esperado.ranking());
            assertThat(r.avisos()).as(e.titulo()).containsExactlyElementsOf(esperado.avisos());
        }
    }

    record EsperadoT29(String enunciado, List<String> orden, List<String> avisos) {
    }

    @Test
    void t29_cada_ejemplo_prioriza_las_causas_y_avisa_lo_que_falta() {
        EjecutorPremortem t29 = new EjecutorPremortem();
        for (Ejemplo e : CATALOGO.ejemplosDe(EjecutorPremortem.ID)) {
            ResultadoPremortem r = correr(t29, e).valor();
            EsperadoT29 esperado = MapeadorJson.leer(e.resultado(), EsperadoT29.class);
            assertThat(r.enunciado()).as(e.titulo()).isEqualTo(esperado.enunciado());
            assertThat(r.causas()).extracting(ResultadoPremortem.CausaPriorizada::texto).as(e.titulo()).containsExactlyElementsOf(esperado.orden());
            assertThat(r.avisos()).as(e.titulo()).containsExactlyElementsOf(esperado.avisos());
        }
    }

    record EsperadoT30(String estado, List<String> avisos) {
    }

    @Test
    void t30_cada_ejemplo_da_el_estado_y_los_avisos_escritos_a_mano() {
        EjecutorInversion t30 = new EjecutorInversion();
        for (Ejemplo e : CATALOGO.ejemplosDe(EjecutorInversion.ID)) {
            ResultadoInversion r = correr(t30, e).valor();
            EsperadoT30 esperado = MapeadorJson.leer(e.resultado(), EsperadoT30.class);
            assertThat(r.estado()).as(e.titulo()).isEqualTo(esperado.estado());
            assertThat(r.avisos()).as(e.titulo()).containsExactlyElementsOf(esperado.avisos());
        }
    }

    record FilaMatriz(int puesto, String opcion, String valor) {
    }

    record EsperadoMatriz(List<FilaMatriz> ranking, List<String> sensibilidad, String nivelSensibilidad, String justificacion) {
    }

    private static void matriz(Ejecutor<?, ?, ResultadoMatriz> ejecutor, IdTecnica id, boolean conJustificacion) {
        for (Ejemplo e : CATALOGO.ejemplosDe(id)) {
            ResultadoMatriz r = correr(ejecutor, e).valor();
            EsperadoMatriz esperado = MapeadorJson.leer(e.resultado(), EsperadoMatriz.class);
            assertThat(r.filas()).extracting(f -> new FilaMatriz(f.puesto(), f.opcion(), f.valor())).as(e.titulo())
                    .containsExactlyElementsOf(esperado.ranking());
            assertThat(r.sensibilidad()).as(e.titulo()).containsExactlyElementsOf(esperado.sensibilidad());
            assertThat(r.nivelSensibilidad()).as(e.titulo()).isEqualTo(esperado.nivelSensibilidad());
            if (conJustificacion) {
                assertThat(r.justificacion()).as(e.titulo()).isEqualTo(esperado.justificacion());
            }
        }
    }

    @Test
    void t31_cada_ejemplo_da_el_ranking_y_la_sensibilidad_calculados_a_mano() {
        matriz(new EjecutorMatrizPonderada(), EjecutorMatrizPonderada.ID, false);
    }

    @Test
    void t33_cada_ejemplo_da_la_mejor_explicacion_su_justificacion_y_su_sensibilidad() {
        matriz(new EjecutorMejorExplicacion(), EjecutorMejorExplicacion.ID, true);
    }

    record EsperadoT32(List<ResultadoDiario.Hito> linea, String bloqueo, List<String> avisos, LocalDate vence) {
    }

    @Test
    void t32_cada_ejemplo_registra_la_decision_con_su_prediccion_y_su_revision() {
        EjecutorDiarioDecisiones t32 = new EjecutorDiarioDecisiones();
        for (Ejemplo e : CATALOGO.ejemplosDe(EjecutorDiarioDecisiones.ID)) {
            Resultado<ResultadoDiario> r = correr(t32, e);
            EsperadoT32 esperado = MapeadorJson.leer(e.resultado(), EsperadoT32.class);
            assertThat(r.valor().linea()).as(e.titulo()).containsExactlyElementsOf(esperado.linea());
            assertThat(r.valor().bloqueo()).as(e.titulo()).isEqualTo(esperado.bloqueo());
            assertThat(r.bloqueoGuardado()).as(e.titulo()).isEqualTo(java.util.Optional.ofNullable(esperado.bloqueo()));
            assertThat(r.valor().avisos()).as(e.titulo()).containsExactlyElementsOf(esperado.avisos());
            assertThat(r.pendientes()).singleElement().satisfies(p -> {
                assertThat(p.tipo()).isEqualTo(TipoPendiente.REVISION);
                assertThat(p.vence()).contains(esperado.vence());
            });
            var prediccion = r.afirmaciones().stream().filter(a -> a.rol() == RolAfirmacion.PREDICCION).findFirst().orElseThrow();
            assertThat(r.predicciones()).singleElement().satisfies(p -> {
                assertThat(p.afirmacionId()).isEqualTo(prediccion.afirmacionId());
                assertThat(p.fechaRevision()).isEqualTo(esperado.vence());
                assertThat(p.id()).isEqualTo(r.valor().prediccionId());
            });
            assertThat(r.pendientes().getFirst().objetoId()).contains(prediccion.afirmacionId());
            assertThat(r.afirmaciones()).extracting(a -> a.rol())
                    .containsExactly(RolAfirmacion.OPCION, RolAfirmacion.PREDICCION, RolAfirmacion.CONDICION_FALSACION);
        }
    }

    @Test
    void t32_no_deja_saltar_el_contexto_las_alternativas_ni_que_me_haria_cambiar_de_opinion() {
        var config = new EjecutorDiarioDecisiones.Config(7);
        var entrada = new EjecutorDiarioDecisiones.Entrada("Abrir en la terminal.", " ", null, "Cubre costos en 6 meses.", 70, "", "2027-04-15");

        assertThat(new EjecutorDiarioDecisiones().validar(config, entrada).errores()).extracting(pensamiento.nucleo.Validacion.Error::campo)
                .containsExactly("contexto", "alternativas", "cambiarOpinion");
    }
}
