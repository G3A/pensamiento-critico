package pensamiento.unidad.catalogo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import pensamiento.catalogo.CatalogoJson;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Tecnica;

/** El JSON del repo es la fuente de verdad del catálogo: 8 familias, 49 técnicas, conteos y patrones de la sección 5b y 7b. */
class CatalogoJsonTest {

    private final CatalogoJson catalogo = new CatalogoJson();

    @Test
    void hay_ocho_familias_en_orden() {
        assertThat(catalogo.familias()).hasSize(8)
                .extracting(f -> f.codigo()).containsExactly("F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8");
    }

    @Test
    void hay_49_tecnicas_con_identificadores_t01_a_t49_sin_repetir() {
        List<Tecnica> tecnicas = catalogo.tecnicas();
        assertThat(tecnicas).hasSize(IdTecnica.TOTAL);
        assertThat(tecnicas.stream().map(t -> t.id().valor()).collect(Collectors.toSet())).hasSize(49);
        for (int i = 1; i <= 49; i++) {
            String id = String.format("T%02d", i);
            assertThat(tecnicas.stream().anyMatch(t -> t.id().valor().equals(id))).as(id).isTrue();
        }
    }

    @Test
    void el_conteo_por_familia_es_el_del_documento() {
        // Sección 5b: F1 7, F2 5, F3 6, F4 5, F5 10, F6 6, F7 5, F8 5
        Map<String, Long> porFamilia = catalogo.tecnicas().stream().collect(Collectors.groupingBy(Tecnica::familia, Collectors.counting()));
        assertThat(porFamilia).containsExactlyInAnyOrderEntriesOf(Map.of(
                "F1", 7L, "F2", 5L, "F3", 6L, "F4", 5L, "F5", 10L, "F6", 6L, "F7", 5L, "F8", 5L));
    }

    @Test
    void el_conteo_por_tipo_es_el_del_documento() {
        // Sección 5b: 5 marcos, 3 representaciones, 10 criterios, 24 procedimientos, 5 prácticas, 2 métodos de aprendizaje
        Map<Tecnica.Tipo, Long> porTipo = catalogo.tecnicas().stream().collect(Collectors.groupingBy(Tecnica::tipo, Collectors.counting()));
        assertThat(porTipo).containsExactlyInAnyOrderEntriesOf(Map.of(
                Tecnica.Tipo.MARCO, 5L, Tecnica.Tipo.REPRESENTACION, 3L, Tecnica.Tipo.CRITERIO, 10L,
                Tecnica.Tipo.PROCEDIMIENTO, 24L, Tecnica.Tipo.PRACTICA, 5L, Tecnica.Tipo.METODO_DE_APRENDIZAJE, 2L));
    }

    @Test
    void solo_las_tecnicas_construidas_estan_activas_y_todas_tienen_nombre_llano_y_usala_cuando() {
        assertThat(catalogo.tecnicas().stream().filter(t -> !t.estaPendiente()).map(t -> t.id().valor())).containsExactly("T01", "T02", "T03", "T05", "T06", "T07", "T13", "T14", "T15", "T16", "T18", "T28", "T34");
        for (Tecnica t : catalogo.tecnicas()) {
            assertThat(t.nombreLlano()).as(t.cita()).isNotBlank();
            assertThat(t.usalaCuando()).as(t.cita()).isNotBlank();
            assertThat(t.definicion()).as(t.cita()).isNotBlank();
            assertThat(t.origen()).as(t.cita()).isNotBlank();
        }
    }

    @Test
    void los_patrones_son_los_17_fragmentos_de_la_seccion_7b() {
        Set<String> validos = Set.of("V01", "V02", "V03a", "V03b", "V03c", "V04", "V05", "V06", "V07", "V08", "V09", "V10", "V11", "V12", "V13a", "V13b", "V13c");
        for (Tecnica t : catalogo.tecnicas()) {
            assertThat(validos).as(t.cita()).contains(t.patron());
        }
        // Sección 7b: V03a solo T28; V01 T01 y T06; V07 solo T43; V12 solo T27
        Map<String, List<String>> porPatron = catalogo.tecnicas().stream()
                .collect(Collectors.groupingBy(Tecnica::patron, Collectors.mapping(t -> t.id().valor(), Collectors.toList())));
        assertThat(porPatron.get("V03a")).containsExactly("T28");
        assertThat(porPatron.get("V01")).containsExactly("T01", "T06");
        assertThat(porPatron.get("V07")).containsExactly("T43");
        assertThat(porPatron.get("V12")).containsExactly("T27");
    }

    @Test
    void solo_el_equipo_rojo_necesita_ollama_de_forma_obligatoria() {
        List<String> obligatorias = catalogo.tecnicas().stream().filter(t -> t.requiereIa() == Tecnica.RequiereIa.SI).map(t -> t.id().valor()).toList();
        assertThat(obligatorias).containsExactly("T36");
    }

    @Test
    void las_relaciones_apuntan_a_tecnicas_existentes_y_el_documento_esta_representado() {
        Set<String> ids = catalogo.tecnicas().stream().map(t -> t.id().valor()).collect(Collectors.toSet());
        for (CatalogoJson.Relacion r : catalogo.relaciones()) {
            assertThat(ids).contains(r.origen(), r.destino());
            assertThat(r.origen()).isNotEqualTo(r.destino());
        }
        // Sección 5b: T11 produce entrada para T19 a T23; T28 complementa T33; T42 y T43 son variantes; T13 contrasta con T34
        assertThat(catalogo.relaciones()).contains(
                new CatalogoJson.Relacion("T11", "T19", "produce_entrada"),
                new CatalogoJson.Relacion("T11", "T23", "produce_entrada"),
                new CatalogoJson.Relacion("T28", "T33", "complementa"),
                new CatalogoJson.Relacion("T42", "T43", "variante"),
                new CatalogoJson.Relacion("T13", "T34", "contrasta"));
    }

    @Test
    void las_reglas_r01_a_r06_tienen_version_1_con_los_parametros_de_la_seccion_5b() {
        List<CatalogoJson.ReglaVersion> reglas = catalogo.reglas();
        assertThat(reglas).extracting(CatalogoJson.ReglaVersion::regla).containsExactly("R01", "R02", "R03", "R04", "R05", "R06");
        assertThat(reglas).allMatch(r -> r.version() == 1);
        CatalogoJson.ReglaVersion r01 = reglas.getFirst();
        assertThat(r01.parametros().get("umbralCraap")).isEqualTo(18);
        assertThat(r01.parametros().get("maximo")).isEqualTo(8);
    }

    @Test
    void toda_tecnica_activa_tiene_esquemas_configuracion_por_defecto_y_tres_ejemplos_de_ambito_distinto() {
        for (Tecnica t : catalogo.tecnicas().stream().filter(t -> !t.estaPendiente()).toList()) {
            assertThat(t.esquemaConfig().texto()).as(t.cita()).contains("\"campos\"");
            assertThat(t.esquemaEntrada().texto()).as(t.cita()).contains("\"campos\"");
            assertThat(t.configDefault().texto()).as(t.cita()).isNotEqualTo("{}");
            assertThat(catalogo.ejemplosDe(t.id()).stream().map(e -> e.ambito()).collect(Collectors.toSet())).as(t.cita())
                    .containsExactlyInAnyOrder(pensamiento.nucleo.Ejemplo.Ambito.values());
        }
    }

    @Test
    void los_ejemplos_solo_existen_para_tecnicas_del_catalogo_y_con_titulo_unico() {
        Set<String> ids = catalogo.tecnicas().stream().map(t -> t.id().valor()).collect(Collectors.toSet());
        List<CatalogoJson.EjemploJson> ejemplos = catalogo.ejemplos();
        assertThat(ejemplos).isNotEmpty();
        assertThat(ejemplos).allSatisfy(e -> assertThat(ids).contains(e.tecnica()));
        assertThat(ejemplos.stream().map(e -> e.tecnica() + "/" + e.titulo()).distinct()).hasSize(ejemplos.size());
    }

    @Test
    void la_huella_del_contenido_es_estable_entre_lecturas() {
        assertThat(catalogo.huella()).isEqualTo(new CatalogoJson().huella());
    }
}
