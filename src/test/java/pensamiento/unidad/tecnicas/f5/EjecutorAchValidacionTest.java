package pensamiento.unidad.tecnicas.f5;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import pensamiento.nucleo.Json;
import pensamiento.nucleo.Validacion;
import pensamiento.tecnicas.f5.ConfigAch;
import pensamiento.tecnicas.f5.EjecutorAch;
import pensamiento.tecnicas.f5.EntradaAch;
import pensamiento.testutil.builders.Contextos;

/** Lo que T28 rechaza antes de calcular, con el mensaje junto al campo que lo provoca. */
class EjecutorAchValidacionTest {

    private final EjecutorAch ach = new EjecutorAch();
    private final ConfigAch porDefecto = ConfigAch.POR_DEFECTO;

    private static EntradaAch.Hipotesis h(String texto) {
        return new EntradaAch.Hipotesis(texto);
    }

    private static EntradaAch.Evidencia e(String texto, EntradaAch.Peso peso, String... celdas) {
        return new EntradaAch.Evidencia(texto, peso, List.of(celdas));
    }

    private List<String> camposConError(ConfigAch config, EntradaAch entrada) {
        return ach.validar(config, entrada).errores().stream().map(Validacion.Error::campo).toList();
    }

    @Test
    void una_sola_hipotesis_no_alcanza_porque_ach_compara_explicaciones() {
        EntradaAch entrada = new EntradaAch("¿Por qué?", List.of(h("La feria")), List.of(e("Solo los sábados", EntradaAch.Peso.ALTO, "C")));
        assertThat(camposConError(porDefecto, entrada)).containsExactly("hipotesis");
    }

    @Test
    void mas_hipotesis_que_el_maximo_configurado_se_rechazan() {
        EntradaAch entrada = new EntradaAch("¿Por qué?", List.of(h("A"), h("B"), h("C")),
                List.of(e("Dato", EntradaAch.Peso.BAJO, "C", "N", "I")));
        assertThat(camposConError(new ConfigAch(2, ConfigAch.Escala.CIN, true), entrada)).containsExactly("hipotesis");
    }

    @Test
    void sin_evidencias_no_hay_matriz() {
        EntradaAch entrada = new EntradaAch("¿Por qué?", List.of(h("A"), h("B")), List.of());
        assertThat(camposConError(porDefecto, entrada)).containsExactly("evidencias");
    }

    @Test
    void con_pesos_activos_cada_evidencia_necesita_su_peso_y_sin_pesos_no() {
        EntradaAch entrada = new EntradaAch("¿Por qué?", List.of(h("A"), h("B")), List.of(e("Dato", null, "C", "I")));
        assertThat(camposConError(porDefecto, entrada)).containsExactly("evidencias[0].peso");
        assertThat(camposConError(new ConfigAch(4, ConfigAch.Escala.CIN, false), entrada)).isEmpty();
    }

    @Test
    void una_celda_fuera_de_la_escala_se_senala_en_su_posicion() {
        EntradaAch entrada = new EntradaAch("¿Por qué?", List.of(h("A"), h("B")), List.of(e("Dato", EntradaAch.Peso.ALTO, "C", "3")));
        assertThat(camposConError(new ConfigAch(4, ConfigAch.Escala.NUMERICA, true), entrada)).containsExactly("evidencias[0].celdas[0]", "evidencias[0].celdas[1]");
        assertThat(camposConError(porDefecto, entrada)).containsExactly("evidencias[0].celdas[1]");
    }

    @Test
    void textos_vacios_se_senalan_por_campo() {
        EntradaAch entrada = new EntradaAch(" ", List.of(h("A"), h("")), List.of(e("", EntradaAch.Peso.ALTO, "C", "N")));
        assertThat(camposConError(porDefecto, entrada)).containsExactly("pregunta", "hipotesis[1].texto", "evidencias[0].texto");
    }

    @Test
    void una_entrada_invalida_no_se_ejecuta() {
        EntradaAch entrada = new EntradaAch("¿Por qué?", List.of(h("A")), List.of());
        assertThatThrownBy(() -> ach.ejecutar(porDefecto, entrada, Contextos.sinIa())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void la_version_1_no_tiene_nada_que_migrar() {
        assertThatThrownBy(() -> ach.migrar(Json.VACIO, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void si_todas_empatan_no_hay_mas_refutada() {
        EntradaAch entrada = new EntradaAch("¿Por qué?", List.of(h("A"), h("B")), List.of(e("Dato", EntradaAch.Peso.ALTO, "N", "N")));
        var valor = ach.ejecutar(porDefecto, entrada, Contextos.sinIa()).valor();
        assertThat(valor.menosRefutadas()).containsExactly("H1", "H2");
        assertThat(valor.masRefutadas()).isEmpty();
        assertThat(valor.resumen()).isEqualTo("Empate entre H1 y H2 (0 inconsistencias ponderadas cada una).");
    }
}
