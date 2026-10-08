package pensamiento.unidad.nucleo.reglas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import pensamiento.nucleo.Prediccion;
import pensamiento.nucleo.reglas.R05Calibracion;
import pensamiento.nucleo.reglas.R05Calibracion.Parametros;
import pensamiento.nucleo.reglas.R05Calibracion.Puntaje;
import pensamiento.nucleo.reglas.R05Calibracion.Tramo;

/**
 * Oráculo de R05 · Confianza y calibración con los números calculados a mano en docs/ejemplos/T25.md y en el ejemplo de
 * punta a punta de docs/ejemplos/T32.md. Los valores esperados son literales; nunca se recalculan con la fórmula.
 */
class R05CalibracionTest {

    private static final LocalDate HOY = LocalDate.of(2026, 10, 7);

    private static R05Calibracion.Prediccion si(int c) {
        return R05Calibracion.Prediccion.resuelta(c, true);
    }

    private static R05Calibracion.Prediccion no(int c) {
        return R05Calibracion.Prediccion.resuelta(c, false);
    }

    private static R05Calibracion.Prediccion el(int c, boolean seCumplio, String fecha) {
        return new R05Calibracion.Prediccion(c, Optional.of(seCumplio), Optional.of(LocalDate.parse(fecha)));
    }

    private static List<R05Calibracion.Prediccion> veces(int n, R05Calibracion.Prediccion p) {
        List<R05Calibracion.Prediccion> lista = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            lista.add(p);
        }
        return lista;
    }

    @Test
    void las_apuestas_de_la_casa_dan_brier_0_17_con_dos_avisos_provisionales_y_una_sin_resolver() {
        List<R05Calibracion.Prediccion> casa = List.of(si(80), si(90), no(60), si(70), si(90), no(70),
                new R05Calibracion.Prediccion(80, Optional.empty(), Optional.empty()));

        R05Calibracion.Calibracion c = R05Calibracion.calcular(casa, new Parametros(20, 5, Puntaje.BRIER, 0), HOY);

        assertThat(c.puntaje()).contains(new BigDecimal("0.17"));
        assertThat(c.puntajeTexto()).contains("0,17");
        assertThat(c.resueltas()).isEqualTo(6);
        assertThat(c.sinResolver()).isEqualTo(1);
        assertThat(c.tramos()).extracting(Tramo::desde, Tramo::hasta, Tramo::n, Tramo::confianzaMedia, Tramo::porcentajeCumplido, Tramo::provisional)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(60, 79, 3, 67, 33, true), org.assertj.core.groups.Tuple.tuple(80, 100, 3, 87, 100, true));
        assertThat(c.avisos()).containsExactly(
                "Cuando dices entre 60 y 79%, se cumple el 33% (declaras 67% en promedio): exceso de confianza. Aviso provisional: 6 resueltas, solo 3 en ese tramo.",
                "Cuando dices entre 80 y 100%, se cumple el 100% (declaras 87% en promedio): falta de confianza. Aviso provisional: 6 resueltas, solo 3 en ese tramo.");
    }

    @Test
    void la_punteria_de_la_panaderia_da_brier_0_22_y_un_solo_aviso_en_el_tramo_de_90_a_100() {
        List<R05Calibracion.Prediccion> panaderia = new ArrayList<>();
        panaderia.addAll(veces(2, si(60)));
        panaderia.addAll(veces(2, no(60)));
        panaderia.addAll(veces(4, si(70)));
        panaderia.addAll(veces(2, no(70)));
        panaderia.addAll(veces(6, si(80)));
        panaderia.addAll(veces(1, no(80)));
        panaderia.addAll(veces(3, si(90)));
        panaderia.addAll(veces(2, no(90)));

        R05Calibracion.Calibracion c = R05Calibracion.calcular(panaderia, new Parametros(10, 10, Puntaje.BRIER, 0), HOY);

        assertThat(c.puntajeTexto()).contains("0,22");
        assertThat(c.resueltas()).isEqualTo(22);
        assertThat(c.tramos()).extracting(Tramo::desde, Tramo::n, Tramo::confianzaMedia, Tramo::porcentajeCumplido)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(60, 4, 60, 50), org.assertj.core.groups.Tuple.tuple(70, 6, 70, 67),
                        org.assertj.core.groups.Tuple.tuple(80, 7, 80, 86), org.assertj.core.groups.Tuple.tuple(90, 5, 90, 60));
        assertThat(c.avisos()).containsExactly(
                "Cuando dices entre 90 y 100%, se cumple el 60% (declaras 90% en promedio): exceso de confianza. Aviso provisional: 22 resueltas, solo 5 en ese tramo.");
    }

    @Test
    void las_promesas_de_la_junta_dan_logaritmico_0_86_y_el_horizonte_deja_una_afuera() {
        List<R05Calibracion.Prediccion> junta = List.of(el(70, true, "2026-03-20"), el(60, true, "2026-06-01"), el(80, true, "2026-05-10"),
                el(60, false, "2026-08-30"), el(90, false, "2025-12-15"), el(90, true, "2025-01-10"));

        R05Calibracion.Calibracion c = R05Calibracion.calcular(junta, new Parametros(20, 5, Puntaje.LOGARITMICO, 12), HOY);

        assertThat(c.puntajeTexto()).contains("0,86");
        assertThat(c.resueltas()).isEqualTo(5);
        assertThat(c.fueraDeHorizonte()).isEqualTo(1);
        assertThat(c.avisos()).containsExactly(
                "Cuando dices entre 80 y 100%, se cumple el 50% (declaras 85% en promedio): exceso de confianza. Aviso provisional: 5 resueltas, solo 2 en ese tramo.");
    }

    @Test
    void la_sucursal_de_la_terminal_lleva_el_brier_de_0_13_a_0_12_al_resolverse() {
        List<R05Calibracion.Prediccion> antes = List.of(si(80), no(60), si(90), si(70));
        List<R05Calibracion.Prediccion> despues = new ArrayList<>(antes);
        despues.add(si(70));

        assertThat(R05Calibracion.calcular(antes, Parametros.diario(), HOY).puntajeTexto()).contains("0,13");
        assertThat(R05Calibracion.calcular(despues, Parametros.diario(), HOY).puntajeTexto()).contains("0,12");
        assertThat(R05Calibracion.calcular(despues, Parametros.diario(), HOY).tramoDe(70)).hasValueSatisfying(t -> {
            assertThat(t.n()).isEqualTo(2);
            assertThat(t.porcentajeCumplido()).isEqualTo(100);
            assertThat(t.aviso()).contains(
                    "Cuando dices entre 70 y 79%, se cumple el 100% (declaras 70% en promedio): falta de confianza. Aviso provisional: 5 resueltas, solo 2 en ese tramo.");
        });
    }

    @Test
    void sin_resueltas_no_hay_puntaje_ni_tramos() {
        R05Calibracion.Calibracion c = R05Calibracion.calcular(List.of(new R05Calibracion.Prediccion(70, Optional.empty(), Optional.empty())),
                Parametros.diario(), HOY);

        assertThat(c.puntaje()).isEmpty();
        assertThat(c.tramos()).isEmpty();
        assertThat(c.sinResolver()).isEqualTo(1);
    }

    @Test
    void una_confianza_por_debajo_de_50_dice_sobreestimas_y_no_exceso_de_confianza() {
        R05Calibracion.Calibracion c = R05Calibracion.calcular(List.of(no(30), no(30)), new Parametros(10, 1, Puntaje.BRIER, 0), HOY);

        assertThat(c.avisos()).containsExactly("Cuando dices entre 30 y 39%, se cumple el 0% (declaras 30% en promedio): sobreestimas.");
    }

    @Test
    void lo_que_suma_una_prediccion_que_falla_con_95_es_0_90() {
        assertThat(R05Calibracion.costoSiFalla(95)).isEqualByComparingTo("0.90");
        assertThat(R05Calibracion.costoSiFalla(5)).isEqualByComparingTo("0.90");
    }

    @Test
    void una_prediccion_resuelta_no_se_puede_volver_a_resolver() {
        Prediccion pendiente = new Prediccion(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "La sucursal cubre sus costos", 70,
                LocalDate.of(2027, 4, 15), Prediccion.Estado.PENDIENTE, Optional.empty());
        Prediccion resuelta = pendiente.resolver(true, Instant.parse("2027-04-15T15:00:00Z"));

        assertThat(resuelta.estado()).isEqualTo(Prediccion.Estado.ACIERTO);
        assertThatThrownBy(() -> resuelta.resolver(false, Instant.parse("2027-04-16T15:00:00Z")))
                .isInstanceOf(Prediccion.YaResuelta.class)
                .hasMessage("Esta predicción ya está resuelta: no se puede modificar.");
    }

    @Test
    void una_prediccion_vence_el_dia_de_su_revision_y_no_antes() {
        Prediccion p = new Prediccion(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "La sucursal cubre sus costos", 70,
                LocalDate.of(2027, 4, 15), Prediccion.Estado.PENDIENTE, Optional.empty());

        assertThat(p.vencida(LocalDate.of(2027, 4, 14))).isFalse();
        assertThat(p.vencida(LocalDate.of(2027, 4, 15))).isTrue();
        assertThat(p.resolver(true, Instant.parse("2027-04-15T15:00:00Z")).vencida(LocalDate.of(2027, 5, 1))).isFalse();
    }
}
