package pensamiento.unidad.catalogo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import pensamiento.catalogo.CatalogoJson;
import pensamiento.nucleo.BancoDojo;
import pensamiento.nucleo.NivelBloom;
import pensamiento.tecnicas.f8.TemaDojo;

/**
 * El banco del Dojo (catalogo/dojo.json) cumple lo que promete docs/dojo.md: 26 conceptos de los cuatro temas y 152 retos con
 * identificador único; cada reto de opciones con una sola correcta; en identificar, la correcta es el nombre de su concepto y
 * las demás son de su mismo tema; en analizar, cada opción es un fragmento literal del texto; en crear, la respuesta modelo
 * pasa la rúbrica y el texto original no. Así el Dojo nunca califica con una respuesta que no está escrita a mano.
 */
class BancoDojoTest {

    private static final BancoDojo BANCO = new CatalogoJson().bancoDojo();

    @Test
    void tiene_26_conceptos_de_los_cuatro_temas_y_152_retos_con_identificador_unico() {
        Map<TemaDojo, Long> porTema = BANCO.conceptos().stream().collect(Collectors.groupingBy(c -> TemaDojo.de(c.idTecnica()), Collectors.counting()));
        assertThat(porTema).containsExactlyInAnyOrderEntriesOf(Map.of(TemaDojo.FALACIAS, 11L, TemaDojo.SESGOS, 8L, TemaDojo.DATOS, 3L, TemaDojo.FUENTES, 4L));
        assertThat(BANCO.retos()).hasSize(152);
        Set<String> ids = new HashSet<>();
        assertThat(BANCO.retos()).allSatisfy(r -> assertThat(ids.add(r.id())).as(r.id()).isTrue());
    }

    @Test
    void cada_concepto_tiene_retos_en_los_cuatro_niveles() {
        for (BancoDojo.Concepto c : BANCO.conceptos()) {
            Set<NivelBloom> niveles = BANCO.retos().stream().filter(r -> r.concepto().equals(c.id())).map(BancoDojo.Reto::nivel)
                    .collect(Collectors.toSet());
            assertThat(niveles).as(c.id()).containsExactlyInAnyOrder(NivelBloom.values());
        }
    }

    @Test
    void cada_reto_de_opciones_tiene_una_sola_correcta_y_en_identificar_las_opciones_son_nombres_de_su_tema() {
        for (BancoDojo.Reto r : BANCO.retos().stream().filter(x -> !x.esDeEscribir()).toList()) {
            assertThat(r.opciones()).as(r.id()).hasSizeBetween(3, 4);
            assertThat(r.opciones()).as(r.id()).filteredOn(o -> o.id().equals(r.correcta())).hasSize(1);
            assertThat(r.rubrica()).as(r.id()).isEmpty();
            if (r.nivel() == NivelBloom.IDENTIFICAR) {
                BancoDojo.Concepto concepto = BANCO.concepto(r.concepto()).orElseThrow();
                assertThat(r.opcion(r.correcta()).orElseThrow().texto()).as(r.id()).isEqualTo(concepto.nombre());
            }
        }
    }

    @Test
    void en_analizar_cada_opcion_es_un_fragmento_literal_del_texto() {
        for (BancoDojo.Reto r : BANCO.retos().stream().filter(x -> x.nivel() == NivelBloom.ANALIZAR).toList()) {
            assertThat(r.opciones()).as(r.id()).allSatisfy(o -> assertThat(r.texto()).contains(o.texto()));
        }
    }

    @Test
    void en_crear_la_respuesta_modelo_pasa_la_rubrica_y_el_texto_original_no() {
        List<BancoDojo.Reto> deCrear = BANCO.retos().stream().filter(BancoDojo.Reto::esDeEscribir).toList();
        assertThat(deCrear).hasSize(26);
        for (BancoDojo.Reto r : deCrear) {
            assertThat(r.rubrica()).as(r.id()).hasSizeBetween(2, 4);
            assertThat(r.rubrica()).extracting(BancoDojo.Chequeo::tipo).as(r.id()).allSatisfy(t -> assertThat(t).isIn("sinMarcas", "conAlguna", "largo"));
            assertThat(r.califica(r.respuestaModelo())).as(r.id() + ": la respuesta modelo").isTrue();
            assertThat(r.califica(r.texto())).as(r.id() + ": el texto original").isFalse();
        }
    }

    @Test
    void la_rubrica_compara_sin_tildes_ni_mayusculas_y_por_palabra_completa() {
        BancoDojo.Chequeo con = new BancoDojo.Chequeo("Dice qué datos miraría", "conAlguna", List.of("cuantos", "de cada"), null);
        assertThat(con.cumple("Voy a contar CUÁNTOS se venden")).isTrue();
        assertThat(con.cumple("Uno de cada diez")).isTrue();
        assertThat(con.cumple("Los descuantos no existen")).isFalse();
        BancoDojo.Chequeo largo = new BancoDojo.Chequeo("Al menos 3 palabras", "largo", List.of(), 3);
        assertThat(largo.cumple("una dos")).isFalse();
        assertThat(largo.cumple("una dos tres")).isTrue();
    }

    @Test
    void los_textos_van_sin_exclamaciones_y_cada_explicacion_existe() {
        assertThat(BANCO.retos()).allSatisfy(r -> {
            assertThat(r.texto() + r.pregunta() + r.explicacion()).as(r.id()).doesNotContain("!", "¡");
            assertThat(r.explicacion()).as(r.id()).isNotBlank();
            assertThat(Arrays.asList("personal", "trabajo", "comunidad")).as(r.id()).contains(r.ambito());
        });
    }
}
