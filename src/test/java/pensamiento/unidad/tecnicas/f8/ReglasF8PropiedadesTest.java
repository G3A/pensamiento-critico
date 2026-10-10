package pensamiento.unidad.tecnicas.f8;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;

import pensamiento.nucleo.CambioOpinion;
import pensamiento.nucleo.NivelBloom;
import pensamiento.tecnicas.f8.EjecutorCambiosOpinion;
import pensamiento.tecnicas.f8.EjecutorDiarioRazonamiento;
import pensamiento.tecnicas.f8.EscaleraBloom;
import pensamiento.tecnicas.f8.ResultadoCambiosOpinion;
import pensamiento.tecnicas.f8.ResultadoDiarioRazonamiento;
import pensamiento.tecnicas.f8.Sm2;

/**
 * Propiedades de las reglas de F8: con SM-2 (T49 · Repetición espaciada) el intervalo nunca baja tras un acierto ni sube tras
 * un error y la facilidad nunca baja de 1,3; con T48 · Taxonomía de Bloom un solo intento nunca abre dos niveles; la línea de
 * tiempo de T46 · Registro de cambios de opinión va siempre de la fecha más nueva a la más vieja y no pierde ningún cambio;
 * T45 · Diario de razonamiento pone cada registro de la ventana una sola vez.
 */
class ReglasF8PropiedadesTest {

    private static final LocalDate HOY = LocalDate.parse("2026-10-07");
    private static final List<String> FACILIDADES = List.of("1.3", "2.0", "2.5", "3.0");

    // ---------------------------------------------------------------------------------------------
    // SM-2
    // ---------------------------------------------------------------------------------------------

    private static Sm2.Estado repasar(String facilidad, List<Boolean> respuestas) {
        Sm2.Estado e = Sm2.inicial(new BigDecimal(facilidad));
        LocalDate d = HOY.minusDays(400);
        for (boolean acierto : respuestas) {
            e = Sm2.responder(e, acierto, d);
            d = d.plusDays(e.intervalo());
        }
        return e;
    }

    @Property
    void tras_un_acierto_el_intervalo_nunca_baja(@ForAll("facilidades") String facilidad, @ForAll @Size(max = 30) List<Boolean> antes) {
        Sm2.Estado previo = repasar(facilidad, antes);
        Sm2.Estado despues = Sm2.responder(previo, true, HOY);
        assertThat(despues.intervalo()).isGreaterThanOrEqualTo(previo.intervalo()).isPositive();
        assertThat(despues.proximo()).isAfter(HOY);
    }

    @Property
    void tras_un_error_el_intervalo_nunca_sube(@ForAll("facilidades") String facilidad, @ForAll @Size(max = 30) List<Boolean> antes) {
        Sm2.Estado previo = repasar(facilidad, antes);
        Sm2.Estado despues = Sm2.responder(previo, false, HOY);
        assertThat(despues.intervalo()).isEqualTo(1);
        assertThat(despues.repeticiones()).isZero();
        if (previo.repasos() > 0) {
            assertThat(despues.intervalo()).isLessThanOrEqualTo(previo.intervalo());
        }
    }

    @Property
    void la_facilidad_nunca_baja_de_1_3(@ForAll("facilidades") String facilidad, @ForAll @Size(max = 40) List<Boolean> respuestas) {
        assertThat(repasar(facilidad, respuestas).facilidad()).isGreaterThanOrEqualTo(Sm2.FACILIDAD_MINIMA);
    }

    @Provide
    Arbitrary<String> facilidades() {
        return Arbitraries.of(FACILIDADES);
    }

    // ---------------------------------------------------------------------------------------------
    // Bloom
    // ---------------------------------------------------------------------------------------------

    @Provide
    Arbitrary<List<EscaleraBloom.Intento>> intentos() {
        return Combinators.combine(Arbitraries.of(NivelBloom.values()), Arbitraries.of(true, false)).as(EscaleraBloom.Intento::new)
                .list().ofMaxSize(60);
    }

    @Provide
    Arbitrary<List<NivelBloom>> activos() {
        return Arbitraries.subsetOf(NivelBloom.values()).ofMinSize(1).map(s -> Arrays.stream(NivelBloom.values()).filter(s::contains).toList());
    }

    @Property
    void un_solo_intento_nunca_abre_dos_niveles(@ForAll("activos") List<NivelBloom> activos, @ForAll @IntRange(min = 1, max = 6) int meta,
                                                @ForAll("intentos") List<EscaleraBloom.Intento> intentos) {
        EscaleraBloom.Parametros p = new EscaleraBloom.Parametros(activos, true, meta);
        int abiertosAntes = abiertos(EscaleraBloom.calcular(p, List.of()));
        List<EscaleraBloom.Intento> hechos = new ArrayList<>();
        for (EscaleraBloom.Intento i : intentos) {
            hechos.add(i);
            int abiertosDespues = abiertos(EscaleraBloom.calcular(p, hechos));
            assertThat(abiertosDespues - abiertosAntes).isBetween(0, 1);
            abiertosAntes = abiertosDespues;
        }
    }

    private static int abiertos(EscaleraBloom.Progreso p) {
        return (int) p.niveles().stream().filter(n -> n.estado() == EscaleraBloom.Estado.DOMINADO || n.estado() == EscaleraBloom.Estado.EN_CURSO).count();
    }

    @Property
    void con_avance_automatico_nunca_hay_un_nivel_dominado_despues_de_uno_bloqueado(@ForAll("activos") List<NivelBloom> activos,
                                                                                     @ForAll @IntRange(min = 1, max = 6) int meta,
                                                                                     @ForAll("intentos") List<EscaleraBloom.Intento> intentos) {
        EscaleraBloom.Progreso p = EscaleraBloom.calcular(new EscaleraBloom.Parametros(activos, true, meta), intentos);
        boolean bloqueado = false;
        for (EscaleraBloom.Nivel n : p.niveles()) {
            if (n.estado() == EscaleraBloom.Estado.BLOQUEADO) {
                bloqueado = true;
            }
            if (bloqueado) {
                assertThat(n.estado()).isIn(EscaleraBloom.Estado.BLOQUEADO, EscaleraBloom.Estado.APAGADO);
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // T46 y T45
    // ---------------------------------------------------------------------------------------------

    @Provide
    Arbitrary<List<EjecutorCambiosOpinion.Cambio>> cambios() {
        Arbitrary<Integer> confianza = Arbitraries.integers().between(0, 100);
        return Combinators.combine(Arbitraries.integers().between(0, 900), Arbitraries.of("Abrir en el centro", "Las cámaras son la solución",
                        "Mi hija debe estudiar afuera"), confianza, confianza, Arbitraries.of(CambioOpinion.Causa.values()))
                .filter((dias, postura, antes, despues, causa) -> !antes.equals(despues))
                .as((dias, postura, antes, despues, causa) -> new EjecutorCambiosOpinion.Cambio(HOY.minusDays(dias).toString(), postura, antes, despues,
                        causa, "T08"))
                .list().ofMaxSize(40);
    }

    @Property
    void la_linea_de_tiempo_va_de_la_fecha_mas_nueva_a_la_mas_vieja_y_no_pierde_cambios(@ForAll("cambios") List<EjecutorCambiosOpinion.Cambio> cambios) {
        var config = new EjecutorCambiosOpinion.Config(true, List.of(CambioOpinion.Causa.values()), 12);
        ResultadoCambiosOpinion r = EjecutorCambiosOpinion.calcular(config, cambios, List.of(), HOY);
        assertThat(r.linea()).hasSize(cambios.size());
        assertThat(r.linea()).extracting(ResultadoCambiosOpinion.Cambio::fecha).isSortedAccordingTo((a, b) -> b.compareTo(a));
        assertThat(r.porCausa().stream().mapToInt(ResultadoCambiosOpinion.PorCausa::cambios).sum()).isEqualTo(r.total());
    }

    @Provide
    Arbitrary<List<EjecutorDiarioRazonamiento.Registro>> registros() {
        return Combinators.combine(Arbitraries.integers().between(0, 120), Arbitraries.integers().between(1, 49), Arbitraries.integers().between(0, 3))
                .as((dias, tecnica, cambios) -> new EjecutorDiarioRazonamiento.Registro(HOY.minusDays(dias).toString(), String.format("T%02d", tecnica),
                        "Registro " + dias + "-" + tecnica, null, cambios))
                .list().ofMinSize(1).ofMaxSize(60);
    }

    @Property
    void cada_registro_de_la_ventana_aparece_una_sola_vez_y_los_demas_se_cuentan_fuera(
            @ForAll("registros") List<EjecutorDiarioRazonamiento.Registro> registros, @ForAll @IntRange(min = 1, max = 12) int semanas) {
        var config = new EjecutorDiarioRazonamiento.Config(List.of("F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8"), true, semanas);
        ResultadoDiarioRazonamiento r = EjecutorDiarioRazonamiento.calcular(config, registros, HOY);
        LocalDate inicio = HOY.with(java.time.DayOfWeek.MONDAY).minusWeeks(semanas - 1L);
        long enVentana = registros.stream().filter(x -> !LocalDate.parse(x.fecha()).isBefore(inicio)).count();
        assertThat(r.semanas().stream().mapToInt(s -> s.registros().size()).sum()).isEqualTo((int) enVentana);
        assertThat(r.semanas()).extracting(ResultadoDiarioRazonamiento.Semana::desde).isSortedAccordingTo((a, b) -> b.compareTo(a));
        r.semanas().forEach(s -> assertThat(s.registros()).extracting(ResultadoDiarioRazonamiento.Registro::fecha)
                .allSatisfy(f -> assertThat(LocalDate.parse(f).with(java.time.DayOfWeek.MONDAY)).hasToString(s.desde())));
    }
}
