package pensamiento.unidad.tecnicas.f8;

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
import pensamiento.nucleo.CambioOpinion;
import pensamiento.nucleo.Ejecutor;
import pensamiento.nucleo.Ejemplo;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Pendiente;
import pensamiento.nucleo.Resultado;
import pensamiento.nucleo.RolAfirmacion;
import pensamiento.nucleo.SentidoAfirmacion;
import pensamiento.nucleo.TipoAfirmacion;
import pensamiento.nucleo.Validacion;
import pensamiento.tecnicas.f8.EjecutorBloom;
import pensamiento.tecnicas.f8.EjecutorCambiosOpinion;
import pensamiento.tecnicas.f8.EjecutorDiarioRazonamiento;
import pensamiento.tecnicas.f8.EjecutorReflexion;
import pensamiento.tecnicas.f8.EjecutorRepeticion;
import pensamiento.tecnicas.f8.ResultadoBloom;
import pensamiento.tecnicas.f8.ResultadoCambiosOpinion;
import pensamiento.tecnicas.f8.ResultadoDiarioRazonamiento;
import pensamiento.tecnicas.f8.ResultadoReflexion;
import pensamiento.tecnicas.f8.ResultadoRepeticion;
import pensamiento.testutil.builders.Contextos;

/**
 * Oráculo de T45 a T49 con los ejemplos de docs/ejemplos/T45.md a T49.md, leídos del catálogo: lo que el ejecutor produce es
 * lo escrito a mano, nunca recalculado con la fórmula de producción. Hoy es el miércoles 7 de octubre de 2026 (reloj fijo).
 */
class EjecutoresF8OraculoTest {

    private static final CatalogoJson CATALOGO = new CatalogoJson();

    record ResumenYPendientes(String resumen, List<String> pendientes) {
    }

    static Stream<IdTecnica> tecnicas() {
        return Stream.of(EjecutorDiarioRazonamiento.ID, EjecutorCambiosOpinion.ID, EjecutorReflexion.ID, EjecutorBloom.ID, EjecutorRepeticion.ID);
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

    // ---------------------------------------------------------------------------------------------
    // T45 · Diario de razonamiento
    // ---------------------------------------------------------------------------------------------

    record RegistroEsperado(String fecha, String tecnica, String resumen, String expediente, int cambios) {
    }

    record SemanaEsperada(String titulo, String resumen, List<RegistroEsperado> registros) {
    }

    record EsperadoT45(List<SemanaEsperada> semanas, List<ResultadoDiarioRazonamiento.PorFamilia> porFamilia, List<String> sinUsar,
                       List<String> avisos) {
    }

    @Test
    void t45_cada_ejemplo_da_las_semanas_los_resumenes_y_las_familias_escritos_a_mano() {
        EjecutorDiarioRazonamiento t45 = new EjecutorDiarioRazonamiento();
        for (Ejemplo e : CATALOGO.ejemplosDe(EjecutorDiarioRazonamiento.ID)) {
            Resultado<ResultadoDiarioRazonamiento> r = correr(t45, e);
            EsperadoT45 esperado = MapeadorJson.leer(e.resultado(), EsperadoT45.class);
            List<SemanaEsperada> semanas = r.valor().semanas().stream().map(s -> new SemanaEsperada(s.titulo(), s.resumen(),
                    s.registros().stream().map(x -> new RegistroEsperado(x.fecha(), x.tecnica(), x.resumen(), x.expediente(), x.cambios())).toList()))
                    .toList();
            assertThat(semanas).as(e.titulo()).containsExactlyElementsOf(esperado.semanas());
            assertThat(r.valor().porFamilia()).as(e.titulo()).containsExactlyElementsOf(esperado.porFamilia());
            assertThat(r.valor().sinUsar()).as(e.titulo()).containsExactlyElementsOf(esperado.sinUsar());
            assertThat(r.valor().avisos()).as(e.titulo()).containsExactlyElementsOf(esperado.avisos());
            assertThat(r.afirmaciones()).as(e.titulo()).isEmpty();
        }
    }

    @Test
    void t45_sin_registros_no_se_puede_evaluar() {
        var config = new EjecutorDiarioRazonamiento.Config(List.of("F1"), true, 4);
        assertThat(new EjecutorDiarioRazonamiento().validar(config, new EjecutorDiarioRazonamiento.Entrada(List.of())).errores())
                .extracting(Validacion.Error::mensaje).containsExactly("Agrega al menos un registro: una ejecución con su fecha.");
    }

    @Test
    void t45_un_registro_con_fecha_posterior_a_hoy_no_cuenta_y_se_avisa() {
        var config = new EjecutorDiarioRazonamiento.Config(List.of("F5"), true, 4);
        var registros = List.of(new EjecutorDiarioRazonamiento.Registro("2026-10-09", "T32", "Decisión del viernes", null, 0),
                new EjecutorDiarioRazonamiento.Registro("2026-10-06", "T29", "Pre-mortem", null, 0));

        Resultado<ResultadoDiarioRazonamiento> r = new EjecutorDiarioRazonamiento().ejecutar(config, new EjecutorDiarioRazonamiento.Entrada(registros),
                Contextos.sinIa());

        assertThat(r.valor().avisos()).containsExactly("Con fecha posterior a hoy: 1 registro que no cuenta.");
        assertThat(r.resumen()).isEqualTo("1 ejecución en 1 semana · 0 cambios de opinión.");
    }

    // ---------------------------------------------------------------------------------------------
    // T46 · Registro de cambios de opinión
    // ---------------------------------------------------------------------------------------------

    record CambioEsperado(String fecha, String postura, int antes, int despues, CambioOpinion.Causa causa, String tecnica) {
    }

    record SinRevisarEsperada(String postura, String desde, int meses) {
    }

    record DeclaradoEsperado(String postura, int antes, int despues, CambioOpinion.Causa causa) {
    }

    record EsperadoT46(List<CambioEsperado> linea, int anio, int total, Map<String, Integer> porCausa, String lectura,
                       List<SinRevisarEsperada> sinRevisar, List<String> avisos, List<DeclaradoEsperado> cambiosDeclarados) {
    }

    @Test
    void t46_cada_ejemplo_da_la_linea_el_resumen_del_ano_la_lectura_y_las_posturas_sin_revisar_escritos_a_mano() {
        EjecutorCambiosOpinion t46 = new EjecutorCambiosOpinion();
        for (Ejemplo e : CATALOGO.ejemplosDe(EjecutorCambiosOpinion.ID)) {
            Resultado<ResultadoCambiosOpinion> r = correr(t46, e);
            EsperadoT46 esperado = MapeadorJson.leer(e.resultado(), EsperadoT46.class);
            ResultadoCambiosOpinion v = r.valor();
            assertThat(v.linea()).extracting(c -> new CambioEsperado(c.fecha(), c.postura(), c.antes(), c.despues(), c.causa(), c.tecnica()))
                    .as(e.titulo()).containsExactlyElementsOf(esperado.linea());
            assertThat(v.anio()).isEqualTo(esperado.anio());
            assertThat(v.total()).as(e.titulo()).isEqualTo(esperado.total());
            Map<String, Integer> porCausa = new LinkedHashMap<>();
            v.porCausa().forEach(c -> porCausa.put(c.causa().toString(), c.cambios()));
            assertThat(porCausa).as(e.titulo()).containsExactlyEntriesOf(esperado.porCausa());
            assertThat(v.lectura()).as(e.titulo()).isEqualTo(esperado.lectura());
            assertThat(v.sinRevisar()).extracting(s -> new SinRevisarEsperada(s.postura(), s.desde(), s.meses()))
                    .as(e.titulo()).containsExactlyElementsOf(esperado.sinRevisar());
            assertThat(v.avisos()).as(e.titulo()).containsExactlyElementsOf(esperado.avisos());
            List<DeclaradoEsperado> declarados = r.cambios().stream().map(c -> new DeclaradoEsperado(r.afirmaciones().stream()
                    .filter(a -> a.afirmacionId().equals(c.afirmacionId())).map(AfirmacionConRol::texto).findFirst().orElseThrow(),
                    c.confianzaAntes(), c.confianzaDespues(), c.causa())).toList();
            assertThat(declarados).as(e.titulo()).containsExactlyElementsOf(esperado.cambiosDeclarados());
            assertThat(r.afirmaciones()).as(e.titulo()).allSatisfy(a -> {
                assertThat(a.rol()).isEqualTo(RolAfirmacion.POSTURA);
                assertThat(a.tipo()).isEqualTo(TipoAfirmacion.JUICIO_DE_VALOR);
                assertThat(a.sentido()).isEqualTo(SentidoAfirmacion.PRODUCIDA);
            });
        }
    }

    @Test
    void t46_un_cambio_a_mano_con_una_causa_apagada_no_se_registra() {
        var config = new EjecutorCambiosOpinion.Config(true, List.of(CambioOpinion.Causa.EVIDENCIA), 12);
        var entrada = new EjecutorCambiosOpinion.Entrada(List.of(), List.of(), "Las cámaras son la solución", 90, 60, CambioOpinion.Causa.PRESION);

        assertThat(new EjecutorCambiosOpinion().validar(config, entrada).errores()).extracting(Validacion.Error::mensaje)
                .containsExactly("Elige una causa de las que tienes activas.");
    }

    @Test
    void t46_un_cambio_a_mano_sin_postura_o_con_la_misma_confianza_dice_que_falta() {
        var config = new EjecutorCambiosOpinion.Config(true, List.of(CambioOpinion.Causa.MANUAL), 12);
        var entrada = new EjecutorCambiosOpinion.Entrada(List.of(), List.of(), " ", 50, 50, CambioOpinion.Causa.MANUAL);

        assertThat(new EjecutorCambiosOpinion().validar(config, entrada).errores()).extracting(Validacion.Error::mensaje)
                .containsExactly("Escribe la postura que cambió.", "No hay cambio de opinión si la confianza no cambió.");
    }

    // ---------------------------------------------------------------------------------------------
    // T47 · Reflexión estructurada
    // ---------------------------------------------------------------------------------------------

    record ItemEsperado(EjecutorReflexion.Pregunta pregunta, String respuesta) {
    }

    record EsperadoT47(List<ItemEsperado> items, int respondidas, String nota) {
    }

    @Test
    void t47_cada_ejemplo_da_las_preguntas_respondidas_y_pendientes_escritas_a_mano() {
        EjecutorReflexion t47 = new EjecutorReflexion();
        for (Ejemplo e : CATALOGO.ejemplosDe(EjecutorReflexion.ID)) {
            Resultado<ResultadoReflexion> r = correr(t47, e);
            EsperadoT47 esperado = MapeadorJson.leer(e.resultado(), EsperadoT47.class);
            assertThat(r.valor().items()).extracting(i -> new ItemEsperado(i.pregunta(), i.respuesta()))
                    .as(e.titulo()).containsExactlyElementsOf(esperado.items());
            assertThat(r.valor().items()).extracting(ResultadoReflexion.Item::texto)
                    .allSatisfy(t -> assertThat(t).startsWith("¿").endsWith("?"));
            assertThat(r.valor().respondidas()).as(e.titulo()).isEqualTo(esperado.respondidas());
            assertThat(r.valor().nota()).as(e.titulo()).isEqualTo(esperado.nota());
            assertThat(r.afirmaciones()).isEmpty();
        }
    }

    @Test
    void t47_una_reflexion_sin_respuestas_no_se_guarda() {
        var config = new EjecutorReflexion.Config(List.of(EjecutorReflexion.Pregunta.APRENDI), true);
        var entrada = new EjecutorReflexion.Entrada("Sesión socrática", null, null, "Respondo una pregunta apagada", null, null, null);

        assertThat(new EjecutorReflexion().validar(config, entrada).errores()).extracting(Validacion.Error::mensaje)
                .containsExactly("Responde al menos una pregunta: una reflexión vacía no se guarda.");
    }

    // ---------------------------------------------------------------------------------------------
    // T48 · Taxonomía de Bloom
    // ---------------------------------------------------------------------------------------------

    record EsperadoT48(List<ResultadoBloom.Nivel> niveles, String actual, String mensaje, List<String> avisos) {
    }

    @Test
    void t48_cada_ejemplo_da_los_niveles_el_nivel_actual_y_el_mensaje_escritos_a_mano() {
        EjecutorBloom t48 = new EjecutorBloom();
        for (Ejemplo e : CATALOGO.ejemplosDe(EjecutorBloom.ID)) {
            Resultado<ResultadoBloom> r = correr(t48, e);
            EsperadoT48 esperado = MapeadorJson.leer(e.resultado(), EsperadoT48.class);
            assertThat(r.valor().niveles()).as(e.titulo()).containsExactlyElementsOf(esperado.niveles());
            assertThat(r.valor().actual()).as(e.titulo()).hasToString(esperado.actual());
            assertThat(r.valor().mensaje()).as(e.titulo()).isEqualTo(esperado.mensaje());
            assertThat(r.valor().avisos()).as(e.titulo()).containsExactlyElementsOf(esperado.avisos());
        }
    }

    // ---------------------------------------------------------------------------------------------
    // T49 · Repetición espaciada
    // ---------------------------------------------------------------------------------------------

    record EsperadoT49(List<Integer> calendario, List<String> dias, List<ResultadoRepeticion.Concepto> conceptos, int racha, String facilidadMedia,
                       List<String> avisos) {
    }

    @Test
    void t49_cada_ejemplo_da_el_calendario_los_conceptos_la_racha_y_la_facilidad_escritos_a_mano() {
        EjecutorRepeticion t49 = new EjecutorRepeticion();
        for (Ejemplo e : CATALOGO.ejemplosDe(EjecutorRepeticion.ID)) {
            Resultado<ResultadoRepeticion> r = correr(t49, e);
            EsperadoT49 esperado = MapeadorJson.leer(e.resultado(), EsperadoT49.class);
            assertThat(r.valor().calendario()).extracting(ResultadoRepeticion.Dia::repasos).as(e.titulo()).containsExactlyElementsOf(esperado.calendario());
            assertThat(r.valor().calendario()).extracting(ResultadoRepeticion.Dia::nombre).as(e.titulo()).containsExactlyElementsOf(esperado.dias());
            assertThat(r.valor().conceptos()).as(e.titulo()).containsExactlyElementsOf(esperado.conceptos());
            assertThat(r.valor().racha()).as(e.titulo()).isEqualTo(esperado.racha());
            assertThat(r.valor().facilidadMedia()).as(e.titulo()).isEqualTo(esperado.facilidadMedia());
            assertThat(r.valor().avisos()).as(e.titulo()).containsExactlyElementsOf(esperado.avisos());
        }
    }

    @Test
    void t49_sin_repasos_dice_que_todavia_no_hay() {
        Resultado<ResultadoRepeticion> r = new EjecutorRepeticion().ejecutar(new EjecutorRepeticion.Config(10, "2.5"),
                new EjecutorRepeticion.Entrada(List.of()), Contextos.sinIa());

        assertThat(r.resumen()).isEqualTo("Todavía no hay repasos · algoritmo SM-2.");
        assertThat(r.valor().facilidadMedia()).isEqualTo("—");
    }
}
