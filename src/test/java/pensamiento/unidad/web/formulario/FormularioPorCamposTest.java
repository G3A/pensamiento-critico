package pensamiento.unidad.web.formulario;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import pensamiento.catalogo.CatalogoJson;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Tecnica;
import pensamiento.web.formulario.Campo;
import pensamiento.web.formulario.EsquemaFormulario;
import pensamiento.web.formulario.LectorFormulario;
import pensamiento.web.formulario.LenguajeCampos;

/**
 * El formulario de T28 · Análisis de hipótesis en competencia (ACH) declarado en el lenguaje de campos: de los
 * parámetros al árbol de valores, las acciones de filas y los errores del JSON Schema derivado, por campo.
 */
class FormularioPorCamposTest {

    private final Tecnica t28 = new CatalogoJson().tecnicas().stream().filter(t -> t.id().equals(IdTecnica.de("T28"))).findFirst().orElseThrow();
    private final List<Campo> entrada = LenguajeCampos.campos(t28.esquemaEntrada());
    private final List<Campo> configuracion = LenguajeCampos.campos(t28.esquemaConfig());
    private final Map<String, Object> config = LenguajeCampos.mapa(t28.configDefault());

    private static Map<String, List<String>> parametros(String... paresClaveValor) {
        Map<String, List<String>> p = new LinkedHashMap<>();
        for (int i = 0; i < paresClaveValor.length; i += 2) {
            p.computeIfAbsent(paresClaveValor[i], k -> new java.util.ArrayList<>()).add(paresClaveValor[i + 1]);
        }
        return p;
    }

    private Map<String, List<String>> lasVentasDeLosSabadosConDosEvidencias() {
        return parametros(
                "pregunta", "¿Por qué bajaron un 18% las ventas de los sábados?",
                "hipotesis[0].texto", "Abrió una feria a dos cuadras",
                "hipotesis[1].texto", "Subió el precio del pan",
                "evidencias[0].texto", "La baja es solo los sábados", "evidencias[0].peso", "alto",
                "evidencias[0].celdas[0]", "C", "evidencias[0].celdas[1]", "I",
                "evidencias[1].texto", "El precio no cambia desde marzo", "evidencias[1].peso", "medio",
                "evidencias[1].celdas[0]", "N", "evidencias[1].celdas[1]", "I");
    }

    @Test
    void los_parametros_se_leen_como_un_arbol_de_filas_y_celdas() {
        Map<String, Object> valores = LectorFormulario.leer(entrada, lasVentasDeLosSabadosConDosEvidencias(), "");

        assertThat(valores.get("pregunta")).isEqualTo("¿Por qué bajaron un 18% las ventas de los sábados?");
        assertThat(valores.get("hipotesis")).asList().hasSize(2);
        assertThat(valores.get("evidencias")).asList().first().isEqualTo(Map.of(
                "texto", "La baja es solo los sábados", "peso", "alto", "celdas", List.of("C", "I")));
    }

    @Test
    void un_formulario_completo_no_tiene_errores_de_esquema() {
        Map<String, Object> valores = LectorFormulario.leer(entrada, lasVentasDeLosSabadosConDosEvidencias(), "");
        assertThat(EsquemaFormulario.validar(entrada, config, valores)).isEmpty();
    }

    @Test
    void cada_error_queda_junto_a_su_campo_con_la_ruta_del_formulario() {
        Map<String, List<String>> p = lasVentasDeLosSabadosConDosEvidencias();
        p.remove("pregunta");
        p.remove("evidencias[1].celdas[1]");
        p.put("hipotesis[1].texto", List.of("   "));
        Map<String, Object> valores = LectorFormulario.leer(entrada, p, "");

        Map<String, String> errores = EsquemaFormulario.validar(entrada, config, valores);

        assertThat(errores).containsOnlyKeys("pregunta", "hipotesis[1].texto", "evidencias[1].celdas[1]");
        assertThat(errores.get("pregunta")).isEqualTo("Pregunta: este campo es obligatorio.");
        assertThat(errores.get("evidencias[1].celdas[1]")).isEqualTo("Marca esta celda: elige una de las opciones.");
    }

    @Test
    void con_pesos_apagados_el_peso_no_se_pide() {
        Map<String, List<String>> p = lasVentasDeLosSabadosConDosEvidencias();
        p.remove("evidencias[0].peso");
        Map<String, Object> sinPesos = new LinkedHashMap<>(config);
        sinPesos.put("pesosActivos", false);
        Map<String, Object> valores = LectorFormulario.leer(entrada, p, "");

        assertThat(EsquemaFormulario.validar(entrada, sinPesos, valores)).isEmpty();
        assertThat(EsquemaFormulario.validar(entrada, config, valores)).containsOnlyKeys("evidencias[0].peso");
    }

    @Test
    void mas_hipotesis_que_el_maximo_de_la_configuracion_es_un_error_del_campo_de_filas() {
        Map<String, Object> maximoDos = new LinkedHashMap<>(config);
        maximoDos.put("maxHipotesis", 2);
        Map<String, List<String>> p = lasVentasDeLosSabadosConDosEvidencias();
        p.put("hipotesis[2].texto", List.of("Los clientes están de vacaciones"));
        Map<String, Object> valores = LectorFormulario.leer(entrada, p, "");

        assertThat(EsquemaFormulario.validar(entrada, maximoDos, valores)).containsEntry("hipotesis", "Hipótesis: como máximo 2.");
    }

    @Test
    @SuppressWarnings("unchecked")
    void anadir_una_hipotesis_agrega_una_celda_vacia_en_cada_evidencia_y_quitarla_la_retira() {
        Map<String, Object> valores = LectorFormulario.leer(entrada, lasVentasDeLosSabadosConDosEvidencias(), "");

        LectorFormulario.aplicar("anadir:hipotesis", entrada, valores, config);
        List<Map<String, Object>> evidencias = (List<Map<String, Object>>) valores.get("evidencias");
        assertThat((List<Object>) valores.get("hipotesis")).hasSize(3);
        assertThat(evidencias).allSatisfy(e -> assertThat((List<Object>) e.get("celdas")).hasSize(3).last().isNull());

        LectorFormulario.aplicar("quitar:hipotesis:0", entrada, valores, config);
        assertThat((List<Object>) valores.get("hipotesis")).hasSize(2);
        assertThat(evidencias.getFirst().get("celdas")).isEqualTo(java.util.Arrays.asList("I", null));
    }

    @Test
    void no_se_anade_una_fila_por_encima_del_maximo() {
        Map<String, Object> maximoDos = new LinkedHashMap<>(config);
        maximoDos.put("maxHipotesis", 2);
        Map<String, Object> valores = LectorFormulario.leer(entrada, lasVentasDeLosSabadosConDosEvidencias(), "");

        LectorFormulario.aplicar("anadir:hipotesis", entrada, valores, maximoDos);

        assertThat(valores.get("hipotesis")).asList().hasSize(2);
    }

    @Test
    void la_configuracion_se_valida_contra_su_propio_esquema() {
        Map<String, Object> mala = new LinkedHashMap<>(config);
        mala.put("maxHipotesis", 12);
        mala.put("escala", "colores");
        assertThat(EsquemaFormulario.validar(configuracion, mala, mala)).containsOnlyKeys("maxHipotesis", "escala");
        assertThat(EsquemaFormulario.validar(configuracion, config, config)).isEmpty();
    }

    @Test
    void un_entero_que_no_es_numero_se_senala_como_tal() {
        Map<String, Object> valores = LectorFormulario.leer(configuracion, parametros("escala", "cin", "maxHipotesis", "cuatro", "pesosActivos", "true"), "");
        assertThat(EsquemaFormulario.validar(configuracion, valores, valores)).containsEntry("maxHipotesis", "Máximo de hipótesis: escribe un número entero.");
    }

    @Test
    void el_interruptor_apagado_se_lee_como_falso_gracias_al_campo_oculto() {
        Map<String, Object> apagado = LectorFormulario.leer(configuracion, parametros("escala", "cin", "maxHipotesis", "4", "pesosActivos", "false"), "");
        Map<String, Object> encendido = LectorFormulario.leer(configuracion, parametros("escala", "cin", "maxHipotesis", "4", "pesosActivos", "false", "pesosActivos", "true"), "");
        assertThat(apagado.get("pesosActivos")).isEqualTo(false);
        assertThat(encendido.get("pesosActivos")).isEqualTo(true);
        assertThat(encendido.get("maxHipotesis")).isEqualTo(4);
    }
}
