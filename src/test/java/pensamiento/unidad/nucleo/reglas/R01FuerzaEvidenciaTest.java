package pensamiento.unidad.nucleo.reglas;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import pensamiento.nucleo.Fuente;
import pensamiento.nucleo.TipoAfirmacion;
import pensamiento.nucleo.reglas.R01FuerzaEvidencia;
import pensamiento.testutil.builders.Fuentes;

/** Oráculo: los ejemplos numéricos de R01 en la sección 5b del documento. Los esperados son literales, no se recalculan. */
class R01FuerzaEvidenciaTest {

    private static final LocalDate HOY = LocalDate.of(2026, 10, 7);
    private final R01FuerzaEvidencia.Parametros p = R01FuerzaEvidencia.Parametros.v1();

    @Test
    void un_metaanalisis_de_2024_independiente_con_original_y_craap_21_vale_8_para_un_dato_estadistico() {
        // Documento: 4 (revisión sistemática) + 1 (reciente) + 1 (independiente) + 1 (original) + 1 (CRAAP ≥ 18) = 8
        int fuerza = R01FuerzaEvidencia.fuerza(TipoAfirmacion.DATO_ESTADISTICO, Fuentes.metaanalisis2024(), HOY, p);
        assertThat(fuerza).isEqualTo(8);
    }

    @Test
    void el_testimonio_de_una_vecina_de_2025_no_independiente_y_sin_craap_vale_1_para_un_testimonio() {
        // Documento: 0 (fuente terciaria) + 1 (reciente) = 1
        int fuerza = R01FuerzaEvidencia.fuerza(TipoAfirmacion.TESTIMONIO, Fuentes.testimonioVecina2025(), HOY, p);
        assertThat(fuerza).isEqualTo(1);
    }

    @Test
    void para_un_hecho_puntual_manda_el_tipo_de_fuente_y_no_el_diseno_del_estudio() {
        // Primaria (2) aunque el diseño sea testimonio (0): un hecho puntual se puntúa por tipo de fuente
        Fuente primaria = Fuentes.fuente().tipo(Fuente.TipoFuente.PRIMARIA).diseno(Fuente.DisenoEstudio.TESTIMONIO).build();
        assertThat(R01FuerzaEvidencia.fuerza(TipoAfirmacion.HECHO, primaria, HOY, p)).isEqualTo(2);
    }

    @Test
    void para_una_relacion_causal_manda_el_diseno_y_sin_diseno_la_base_es_cero() {
        Fuente sinDiseno = Fuentes.fuente().tipo(Fuente.TipoFuente.PRIMARIA).build();
        assertThat(R01FuerzaEvidencia.fuerza(TipoAfirmacion.CAUSAL, sinDiseno, HOY, p)).isEqualTo(0);
    }

    @ParameterizedTest(name = "fecha {0} → bono reciente {1}")
    @CsvSource({
            "2021-10-07, 1",   // justo 5 años: todavía reciente (límite inclusivo)
            "2021-10-06, 0",   // un día más vieja: ya no
            "2026-10-07, 1"    // hoy
    })
    void el_bono_de_actualidad_aplica_hasta_cinco_anios_inclusive(String fecha, int esperado) {
        Fuente fuente = Fuentes.fuente().tipo(Fuente.TipoFuente.TERCIARIA).fecha(LocalDate.parse(fecha)).build();
        assertThat(R01FuerzaEvidencia.fuerza(TipoAfirmacion.HECHO, fuente, HOY, p)).isEqualTo(esperado);
    }

    @ParameterizedTest(name = "CRAAP {0} → bono {1}")
    @CsvSource({"17, 0", "18, 1", "25, 1"})
    void el_bono_craap_aplica_desde_el_umbral_18_inclusive(int craap, int esperado) {
        Fuente fuente = Fuentes.fuente().tipo(Fuente.TipoFuente.TERCIARIA).craap(craap).build();
        assertThat(R01FuerzaEvidencia.fuerza(TipoAfirmacion.HECHO, fuente, HOY, p)).isEqualTo(esperado);
    }
}
