package pensamiento.unidad.tecnicas.f3;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

import pensamiento.tecnicas.f3.ReglasFalacias;

/** Cómo las reglas de T13 parten el texto y citan la pista, independiente de qué esquema reconocen. */
class ReglasFalaciasTest {

    @Test
    void parte_por_punto_signo_de_cierre_o_salto_de_linea_pero_no_por_punto_y_coma_ni_decimales() {
        String texto = "Subió 1.5 por ciento; nadie lo notó.  ¿Por qué?\nPorque sí!";

        assertThat(ReglasFalacias.oraciones(texto)).extracting(ReglasFalacias.Oracion::texto)
                .containsExactly("Subió 1.5 por ciento; nadie lo notó.", "¿Por qué?", "Porque sí!");
    }

    @Test
    void cada_oracion_sabe_donde_empieza_y_termina_en_el_texto_original() {
        String texto = "  Primera.   Segunda sin punto";
        for (ReglasFalacias.Oracion o : ReglasFalacias.oraciones(texto)) {
            assertThat(texto.substring(o.inicio(), o.fin())).isEqualTo(o.texto());
        }
    }

    @Test
    void la_pista_se_cita_con_las_tildes_y_mayusculas_del_texto_aunque_se_compare_sin_ellas() {
        var hallazgos = ReglasFalacias.buscar("QUÉ VA A SABER el vecino de cuentas, si nunca ahorra.", Set.of("ad_hominem"));

        assertThat(hallazgos).singleElement().satisfies(h -> assertThat(h.porque())
                .isEqualTo("Encontré «QUÉ VA A SABER»: descalifica a quien habla por su situación, no por sus razones."));
    }

    @Test
    void un_texto_sin_patrones_no_produce_hallazgos() {
        assertThat(ReglasFalacias.buscar("La junta se reúne el jueves a las 7.", Set.copyOf(ReglasFalacias.ESQUEMAS))).isEmpty();
    }
}
