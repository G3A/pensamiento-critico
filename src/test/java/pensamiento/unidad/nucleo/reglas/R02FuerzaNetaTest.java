package pensamiento.unidad.nucleo.reglas;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import pensamiento.nucleo.Evidencia;
import pensamiento.nucleo.reglas.R02FuerzaNeta;
import pensamiento.testutil.builders.Evidencias;

class R02FuerzaNetaTest {

    private final R02FuerzaNeta.Parametros p = R02FuerzaNeta.Parametros.v1();

    @Test
    void apoya_8_y_1_contra_6_da_mas_3_media_a_favor() {
        // Documento: 8 + 1 − 6 = +3, media a favor
        R02FuerzaNeta.FuerzaNeta neta = R02FuerzaNeta.neta(
                List.of(Evidencias.apoya(8), Evidencias.apoya(1), Evidencias.contradice(6)), p);
        assertThat(neta.valor()).isEqualTo(3);
        assertThat(neta.magnitud()).isEqualTo(R02FuerzaNeta.Magnitud.MEDIA);
        assertThat(neta.aFavor()).isTrue();
    }

    @Test
    void lo_que_matiza_no_suma_ni_resta() {
        R02FuerzaNeta.FuerzaNeta neta = R02FuerzaNeta.neta(List.of(Evidencias.apoya(2), Evidencias.matiza(8)), p);
        assertThat(neta.valor()).isEqualTo(2);
    }

    @Test
    void lo_etiquetado_por_el_modelo_y_no_adoptado_no_cuenta() {
        R02FuerzaNeta.FuerzaNeta neta = R02FuerzaNeta.neta(
                List.of(Evidencias.apoya(2), Evidencias.delModeloSinAdoptar(Evidencia.Postura.APOYA, 8)), p);
        assertThat(neta.valor()).isEqualTo(2);
    }

    @ParameterizedTest(name = "neta {0} → {1}")
    @CsvSource({"0, DEBIL", "2, DEBIL", "3, MEDIA", "5, MEDIA", "6, FUERTE", "-6, FUERTE", "-3, MEDIA"})
    void la_magnitud_se_clasifica_por_valor_absoluto_con_umbrales_3_y_6(int valor, R02FuerzaNeta.Magnitud esperada) {
        List<Evidencia> evidencias = valor >= 0 ? List.of(Evidencias.apoya(valor)) : List.of(Evidencias.contradice(-valor));
        assertThat(R02FuerzaNeta.neta(evidencias, p).magnitud()).isEqualTo(esperada);
    }
}
