package pensamiento.unidad.tecnicas.f2;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import pensamiento.catalogo.CatalogoJson;
import pensamiento.catalogo.MapeadorJson;
import pensamiento.nucleo.AfirmacionConRol;
import pensamiento.nucleo.CambioOpinion;
import pensamiento.nucleo.Ejecutor;
import pensamiento.nucleo.Ejemplo;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Pendiente;
import pensamiento.nucleo.Resultado;
import pensamiento.nucleo.RolAfirmacion;
import pensamiento.nucleo.TipoAfirmacion;
import pensamiento.nucleo.TipoPendiente;
import pensamiento.tecnicas.f2.EjecutorCincoPorques;
import pensamiento.tecnicas.f2.EjecutorEscalera;
import pensamiento.tecnicas.f2.EjecutorFalsacion;
import pensamiento.tecnicas.f2.EjecutorPreguntasSocraticas;
import pensamiento.tecnicas.f2.EjecutorTerminos;
import pensamiento.tecnicas.f2.ResultadoCincoPorques;
import pensamiento.tecnicas.f2.ResultadoEscalera;
import pensamiento.tecnicas.f2.ResultadoFalsacion;
import pensamiento.tecnicas.f2.ResultadoPreguntasSocraticas;
import pensamiento.tecnicas.f2.ResultadoTerminos;
import pensamiento.testutil.builders.Contextos;

/**
 * Oráculo de T08 a T12 con los ejemplos de docs/ejemplos/T08.md a T12.md, leídos del catálogo en modo plantillas: lo que
 * el ejecutor produce es lo escrito a mano, nunca recalculado con la fórmula de producción.
 */
class EjecutoresF2OraculoTest {

    private static final CatalogoJson CATALOGO = new CatalogoJson();

    record ResumenYPendientes(String resumen, List<String> pendientes) {
    }

    static Stream<IdTecnica> tecnicas() {
        return Stream.of(EjecutorPreguntasSocraticas.ID, EjecutorCincoPorques.ID, EjecutorEscalera.ID, EjecutorFalsacion.ID, EjecutorTerminos.ID);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("tecnicas")
    void cada_tecnica_tiene_tres_ejemplos_uno_por_ambito(IdTecnica id) {
        assertThat(CATALOGO.ejemplosDe(id)).extracting(Ejemplo::ambito)
                .containsExactlyInAnyOrder(Ejemplo.Ambito.PERSONAL, Ejemplo.Ambito.TRABAJO, Ejemplo.Ambito.COMUNIDAD);
    }

    static <C, E, R> Resultado<R> correr(Ejecutor<C, E, R> ejecutor, Ejemplo ejemplo) {
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

    // ---------------------------------------------------------------------------------------------
    // T08 · Preguntas socráticas
    // ---------------------------------------------------------------------------------------------

    record TurnoEsperado(int numero, String tipo, String elemento, String rama, String porque, String pregunta) {
    }

    record EsperadoT08(List<TurnoEsperado> turnos, TurnoEsperado siguiente, String cierre, int llenos, Map<String, Integer> estandares, String cambio) {
    }

    private static TurnoEsperado comoEsperado(ResultadoPreguntasSocraticas.Turno t) {
        return new TurnoEsperado(t.numero(), t.tipo(), t.elemento(), t.rama(), t.porque(), t.pregunta());
    }

    @Test
    void t08_cada_ejemplo_da_los_turnos_el_panel_y_los_estandares_escritos_a_mano() {
        EjecutorPreguntasSocraticas t08 = new EjecutorPreguntasSocraticas();
        for (Ejemplo e : CATALOGO.ejemplosDe(EjecutorPreguntasSocraticas.ID)) {
            Resultado<ResultadoPreguntasSocraticas> r = correr(t08, e);
            EsperadoT08 esperado = MapeadorJson.leer(e.resultado(), EsperadoT08.class);
            assertThat(r.valor().turnos()).extracting(EjecutoresF2OraculoTest::comoEsperado).as(e.titulo()).containsExactlyElementsOf(esperado.turnos());
            assertThat(r.valor().turnos()).as("todas del banco").allSatisfy(t -> assertThat(t.origen()).isEqualTo("banco"));
            if (esperado.siguiente() == null) {
                assertThat(r.valor().siguiente()).as(e.titulo()).isNull();
                assertThat(r.valor().cierre()).as(e.titulo()).isEqualTo(esperado.cierre());
            } else {
                assertThat(comoEsperado(r.valor().siguiente())).as(e.titulo()).isEqualTo(esperado.siguiente());
            }
            assertThat(r.valor().llenos()).as(e.titulo()).isEqualTo(esperado.llenos());
            assertThat(r.valor().estandares()).as(e.titulo()).extracting(ResultadoPreguntasSocraticas.EstandarPanel::id,
                    ResultadoPreguntasSocraticas.EstandarPanel::puntaje).containsExactlyElementsOf(esperado.estandares().entrySet().stream()
                    .map(x -> org.assertj.core.groups.Tuple.tuple(x.getKey(), x.getValue())).toList());
            assertThat(r.valor().cambio()).as(e.titulo()).isEqualTo(esperado.cambio());
        }
    }

    @Test
    void t08_el_cambio_de_confianza_se_declara_sobre_la_postura_con_su_causa() {
        EjecutorPreguntasSocraticas t08 = new EjecutorPreguntasSocraticas();
        Ejemplo sucursal = CATALOGO.ejemplosDe(EjecutorPreguntasSocraticas.ID).stream().filter(e -> e.titulo().equals("La segunda sucursal")).findFirst()
                .orElseThrow();

        Resultado<ResultadoPreguntasSocraticas> r = correr(t08, sucursal);

        AfirmacionConRol postura = r.afirmaciones().stream().filter(a -> a.rol() == RolAfirmacion.POSTURA).findFirst().orElseThrow();
        assertThat(postura.tipo()).isEqualTo(TipoAfirmacion.JUICIO_DE_VALOR);
        assertThat(r.cambios()).singleElement().satisfies(c -> {
            assertThat(c.afirmacionId()).isEqualTo(postura.afirmacionId());
            assertThat(c.confianzaAntes()).isEqualTo(80);
            assertThat(c.confianzaDespues()).isEqualTo(65);
            assertThat(c.causa()).isEqualTo(CambioOpinion.Causa.EVIDENCIA);
        });
        assertThat(r.afirmaciones()).extracting(AfirmacionConRol::rol)
                .containsExactly(RolAfirmacion.POSTURA, RolAfirmacion.SUPUESTO, RolAfirmacion.CONDICION_FALSACION);
    }

    @Test
    void t08_una_respuesta_de_mas_despues_del_cierre_no_se_puede_evaluar() {
        EjecutorPreguntasSocraticas t08 = new EjecutorPreguntasSocraticas();
        var config = new EjecutorPreguntasSocraticas.Config(List.of(pensamiento.tecnicas.f2.EstrategiaSocratica.TipoSocratico.SUPUESTOS,
                pensamiento.tecnicas.f2.EstrategiaSocratica.TipoSocratico.EVIDENCIA), pensamiento.tecnicas.f2.EstrategiaSocratica.Orden.FIJO, 6,
                EjecutorPreguntasSocraticas.Modo.PLANTILLAS);
        var entrada = new EjecutorPreguntasSocraticas.Entrada("Hay que subir el precio del pan.", pensamiento.tecnicas.f2.EstrategiaSocratica.ModoSesion.DECISION,
                List.of(new EjecutorPreguntasSocraticas.TurnoEntrada("Que los clientes pagan."), new EjecutorPreguntasSocraticas.TurnoEntrada("Las cuentas."),
                        new EjecutorPreguntasSocraticas.TurnoEntrada("Que alcanza."), new EjecutorPreguntasSocraticas.TurnoEntrada("Otra más.")),
                null, null, null, null, List.of());

        assertThat(t08.validar(config, entrada).errores()).extracting(pensamiento.nucleo.Validacion.Error::mensaje)
                .containsExactly("La sesión llegó al cierre en el turno 4: las respuestas de más no tienen pregunta.");
    }

    // ---------------------------------------------------------------------------------------------
    // T09 · 5 porqués
    // ---------------------------------------------------------------------------------------------

    record PorqueEsperado(String codigo, int nivel, ResultadoCincoPorques.Estado estado) {
    }

    record EsperadoT09(List<PorqueEsperado> porques, List<String> avisos) {
    }

    @Test
    void t09_cada_ejemplo_da_los_niveles_las_causas_raiz_y_los_avisos_escritos_a_mano() {
        EjecutorCincoPorques t09 = new EjecutorCincoPorques();
        for (Ejemplo e : CATALOGO.ejemplosDe(EjecutorCincoPorques.ID)) {
            Resultado<ResultadoCincoPorques> r = correr(t09, e);
            EsperadoT09 esperado = MapeadorJson.leer(e.resultado(), EsperadoT09.class);
            assertThat(r.valor().porques()).extracting(p -> new PorqueEsperado(p.codigo(), p.nivel(), p.estado())).as(e.titulo())
                    .containsExactlyElementsOf(esperado.porques());
            assertThat(r.valor().avisos()).as(e.titulo()).containsExactlyElementsOf(esperado.avisos());
            assertThat(r.pendientes()).filteredOn(p -> p.tipo() == TipoPendiente.VERIFICACION).allSatisfy(p ->
                    assertThat(r.afirmaciones()).filteredOn(a -> a.rol() == RolAfirmacion.CONCLUSION).extracting(AfirmacionConRol::afirmacionId)
                            .contains(p.objetoId().orElseThrow()));
        }
    }

    @Test
    void t09_un_porque_que_pasa_los_niveles_o_una_rama_apagada_no_se_pueden_evaluar() {
        EjecutorCincoPorques t09 = new EjecutorCincoPorques();
        var cuatro = new EjecutorCincoPorques.Entrada("Se quemó el pan.", List.of(new EjecutorCincoPorques.Porque("a", null, null, false),
                new EjecutorCincoPorques.Porque("b", null, null, false), new EjecutorCincoPorques.Porque("c", null, null, false),
                new EjecutorCincoPorques.Porque("d", null, null, false)));
        var conRama = new EjecutorCincoPorques.Entrada("Se quemó el pan.", List.of(new EjecutorCincoPorques.Porque("a", null, null, false),
                new EjecutorCincoPorques.Porque("b", null, "problema", false)));

        assertThat(t09.validar(new EjecutorCincoPorques.Config(3, false, false), cuatro).errores()).extracting(pensamiento.nucleo.Validacion.Error::mensaje)
                .containsExactly("P4 queda en el nivel 4 y la configuración permite 3: sube los niveles o quítalo.");
        assertThat(t09.validar(new EjecutorCincoPorques.Config(3, false, false), conRama).errores()).extracting(pensamiento.nucleo.Validacion.Error::mensaje)
                .containsExactly("Las ramas están apagadas en la configuración: deja vacío «responde a» en P2.");
    }

    // ---------------------------------------------------------------------------------------------
    // T10 · Escalera de inferencia
    // ---------------------------------------------------------------------------------------------

    record PeldanoEsperado(String id, String estado, String motivo) {
    }

    record EsperadoT10(List<PeldanoEsperado> peldanos, String nota) {
    }

    @Test
    void t10_cada_ejemplo_marca_el_peldano_debil_escrito_a_mano() {
        EjecutorEscalera t10 = new EjecutorEscalera();
        for (Ejemplo e : CATALOGO.ejemplosDe(EjecutorEscalera.ID)) {
            Resultado<ResultadoEscalera> r = correr(t10, e);
            EsperadoT10 esperado = MapeadorJson.leer(e.resultado(), EsperadoT10.class);
            assertThat(r.valor().peldanos()).extracting(p -> new PeldanoEsperado(p.id(), p.estado(), p.motivo())).as(e.titulo())
                    .containsExactlyElementsOf(esperado.peldanos());
            assertThat(r.valor().nota()).as(e.titulo()).isEqualTo(esperado.nota());
        }
    }

    // ---------------------------------------------------------------------------------------------
    // T11 · Falsación y "qué tendría que ser cierto"
    // ---------------------------------------------------------------------------------------------

    record EsperadoT11(List<String> estados, List<String> avisos, List<String> acciones) {
    }

    @Test
    void t11_cada_ejemplo_da_los_estados_avisos_y_acciones_escritos_a_mano() {
        EjecutorFalsacion t11 = new EjecutorFalsacion();
        for (Ejemplo e : CATALOGO.ejemplosDe(EjecutorFalsacion.ID)) {
            Resultado<ResultadoFalsacion> r = correr(t11, e);
            EsperadoT11 esperado = MapeadorJson.leer(e.resultado(), EsperadoT11.class);
            assertThat(r.valor().condiciones()).extracting(ResultadoFalsacion.Condicion::estado).as(e.titulo()).containsExactlyElementsOf(esperado.estados());
            assertThat(r.valor().avisos()).as(e.titulo()).containsExactlyElementsOf(esperado.avisos());
            assertThat(r.valor().acciones()).as(e.titulo()).containsExactlyElementsOf(esperado.acciones());
            assertThat(r.afirmaciones()).as(e.titulo()).filteredOn(a -> a.rol() == RolAfirmacion.CONDICION_FALSACION).hasSize(1);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // T12 · Definición de términos y detección de ambigüedad
    // ---------------------------------------------------------------------------------------------

    record TerminoEsperado(String codigo, String termino, String estado) {
    }

    record EsperadoT12(List<TerminoEsperado> terminos, List<String> avisos) {
    }

    @Test
    void t12_cada_ejemplo_marca_los_terminos_y_sus_estados_escritos_a_mano() {
        EjecutorTerminos t12 = new EjecutorTerminos();
        for (Ejemplo e : CATALOGO.ejemplosDe(EjecutorTerminos.ID)) {
            Resultado<ResultadoTerminos> r = correr(t12, e);
            EsperadoT12 esperado = MapeadorJson.leer(e.resultado(), EsperadoT12.class);
            assertThat(r.valor().terminos()).extracting(t -> new TerminoEsperado(t.codigo(), t.termino(), t.estado())).as(e.titulo())
                    .containsExactlyElementsOf(esperado.terminos());
            assertThat(r.valor().avisos()).as(e.titulo()).containsExactlyElementsOf(esperado.avisos());
            assertThat(r.valor().terminos()).as("cada marca está donde dice el texto")
                    .allSatisfy(t -> assertThat(r.valor().texto().substring(t.inicio(), t.fin())).isEqualTo(t.termino()));
        }
    }
}
