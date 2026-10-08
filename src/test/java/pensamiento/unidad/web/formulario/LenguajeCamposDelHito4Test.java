package pensamiento.unidad.web.formulario;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import pensamiento.catalogo.CatalogoJson;
import pensamiento.nucleo.Tecnica;
import pensamiento.web.formulario.Campo;
import pensamiento.web.formulario.EsquemaFormulario;
import pensamiento.web.formulario.LenguajeCampos;

/**
 * Las dos extensiones del lenguaje de campos del hito 4: opcionesDesde (las categorías propias de T43 · Diagrama de
 * Ishikawa escritas en la configuración, y solo los operadores activos de T44 · SCAMPER y pensamiento lateral) y
 * visibleSi con "~" (un campo que existe si un conjunto de la configuración contiene un valor).
 */
class LenguajeCamposDelHito4Test {

    private static final CatalogoJson CATALOGO = new CatalogoJson();

    private static List<Campo> entrada(String id) {
        Tecnica t = CATALOGO.tecnicas().stream().filter(x -> x.id().valor().equals(id)).findFirst().orElseThrow();
        return LenguajeCampos.campos(t.esquemaEntrada());
    }

    @Test
    void las_categorias_de_ishikawa_salen_del_texto_de_la_configuracion_separado_por_comas() {
        Campo categoria = LenguajeCampos.buscar(entrada("T43"), "causas.categoria");

        assertThat(categoria.opcionesCon(Map.of("categorias", "Mañana,  Transporte , Personas,,Mañana")))
                .containsExactly(new Campo.Opcion("Mañana", "Mañana"), new Campo.Opcion("Transporte", "Transporte"),
                        new Campo.Opcion("Personas", "Personas"));
    }

    @Test
    void scamper_solo_ofrece_los_operadores_activos_y_valida_contra_ellos() {
        List<Campo> campos = entrada("T44");
        Campo operador = LenguajeCampos.buscar(campos, "ideas.operador");
        Map<String, Object> config = Map.of("operadores", List.of("sustituir", "eliminar"), "ideasMinimas", 1, "minutosPorOperador", 0);

        assertThat(operador.opcionesCon(config)).extracting(Campo.Opcion::valor).containsExactly("sustituir", "eliminar");
        Map<String, String> errores = EsquemaFormulario.validar(campos, config, Map.of("problema", "¿Cómo llegamos al colegio?",
                "ideas", List.of(Map.of("texto", "Bicicleta", "operador", "combinar", "seleccionada", false))));
        assertThat(errores).containsKey("ideas[0].operador");
    }

    @Test
    void un_campo_con_tilde_existe_solo_si_el_conjunto_contiene_el_valor() {
        Campo fecundidad = new Campo("fecundidad", Campo.Tipo.ENUMERACION, "Fecundidad", null, true, null, null, null, List.of(), null, null,
                "criterios~fecundidad", null, null, null, null, null, List.of());

        assertThat(fecundidad.visibleCon(Map.of("criterios", List.of("alcance", "fecundidad")))).isTrue();
        assertThat(fecundidad.visibleCon(Map.of("criterios", List.of("alcance")))).isFalse();
        assertThat(fecundidad.visibleCon(Map.of())).isFalse();
    }
}
