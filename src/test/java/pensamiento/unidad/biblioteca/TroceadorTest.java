package pensamiento.unidad.biblioteca;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;

import pensamiento.biblioteca.DetectorTipo;
import pensamiento.biblioteca.Troceador;
import pensamiento.nucleo.Fragmento;

/**
 * El troceado de la biblioteca (docs/verificacion.md): párrafos juntados hasta unos 800 caracteres, páginas que no se mezclan,
 * filas de CSV con su encabezado. Propiedad: nunca pierde ni repite palabras, en el mismo orden.
 */
class TroceadorTest {

    private static List<String> palabras(String texto) {
        return Arrays.stream(texto.split("\\s+")).filter(p -> !p.isEmpty()).toList();
    }

    @Test
    void el_conteo_del_municipio_queda_en_fragmentos_de_hasta_800_caracteres_con_el_pasaje_literal() {
        String md = new String(DetectorTipoTest.recurso("biblioteca/movilidad-centro-barrio.md"), StandardCharsets.UTF_8);

        List<Fragmento.Nuevo> fragmentos = Troceador.trocear(List.of(new Troceador.Pagina(Optional.empty(), md)));

        assertThat(fragmentos).hasSizeGreaterThan(1);
        assertThat(fragmentos).extracting(Fragmento.Nuevo::orden).containsExactlyElementsOf(java.util.stream.IntStream.range(0, fragmentos.size()).boxed().toList());
        assertThat(fragmentos).allSatisfy(f -> assertThat(f.texto().length()).isLessThanOrEqualTo(Troceador.OBJETIVO));
        assertThat(fragmentos).anySatisfy(f -> assertThat(f.texto()).contains("En el centro pasan en promedio 1.200 personas por hora; en el barrio, 300."));
    }

    @Test
    void las_lineas_partidas_de_un_parrafo_se_unen_y_las_paginas_no_se_mezclan() {
        List<Fragmento.Nuevo> fragmentos = Troceador.trocear(List.of(
                new Troceador.Pagina(Optional.of(1), "Conteo peatonal\ndel municipio."),
                new Troceador.Pagina(Optional.of(2), "En el centro pasan\n1.200 personas por hora.\n\nEn el barrio, 300.")));

        assertThat(fragmentos).extracting(Fragmento.Nuevo::texto, Fragmento.Nuevo::pagina).containsExactly(
                org.assertj.core.groups.Tuple.tuple("Conteo peatonal del municipio.", Optional.of(1)),
                org.assertj.core.groups.Tuple.tuple("En el centro pasan 1.200 personas por hora.\n\nEn el barrio, 300.", Optional.of(2)));
    }

    @Test
    void un_parrafo_de_mas_de_1600_caracteres_se_parte_por_oraciones() {
        String oracion = "La harina se guarda en un lugar seco y fresco para que no se humedezca. ";
        String largo = oracion.repeat(30).strip();

        List<Fragmento.Nuevo> fragmentos = Troceador.trocear(List.of(new Troceador.Pagina(Optional.empty(), largo)));

        assertThat(largo.length()).isGreaterThan(Troceador.MAXIMO);
        assertThat(fragmentos).hasSizeGreaterThan(1).allSatisfy(f -> {
            assertThat(f.texto().length()).isLessThanOrEqualTo(Troceador.OBJETIVO);
            assertThat(f.texto()).endsWith("humedezca.");
        });
    }

    @Test
    void cada_fila_de_un_csv_es_un_fragmento_con_su_encabezado() {
        String csv = new String(DetectorTipoTest.recurso("biblioteca/colegios-resultados.csv"), StandardCharsets.UTF_8);

        List<Fragmento.Nuevo> fragmentos = Troceador.trocearCsv(csv, DetectorTipo.separador(csv).orElseThrow());

        assertThat(fragmentos).hasSize(6);
        assertThat(fragmentos.get(1).texto()).isEqualTo("colegio: Colegio nuevo · area: matemáticas · anio: 2025 · puntaje: 72 · estudiantes: 52");
        assertThat(fragmentos).allSatisfy(f -> assertThat(f.pagina()).isEmpty());
    }

    @Test
    void un_campo_entre_comillas_puede_traer_el_separador_y_comillas_dobles() {
        List<Fragmento.Nuevo> fragmentos = Troceador.trocearCsv("mes;nota\nmayo;\"pan; \"\"integral\"\"\"\n", ';');

        assertThat(fragmentos).singleElement().satisfies(f -> assertThat(f.texto()).isEqualTo("mes: mayo · nota: pan; \"integral\""));
    }

    @Property
    void el_troceado_nunca_pierde_ni_repite_palabras(@ForAll("paginas") List<String> paginas) {
        List<Troceador.Pagina> entrada = new ArrayList<>();
        for (int i = 0; i < paginas.size(); i++) {
            entrada.add(new Troceador.Pagina(Optional.of(i + 1), paginas.get(i)));
        }

        List<Fragmento.Nuevo> fragmentos = Troceador.trocear(entrada);

        List<String> esperadas = paginas.stream().flatMap(p -> palabras(p).stream()).toList();
        List<String> obtenidas = fragmentos.stream().flatMap(f -> palabras(f.texto()).stream()).toList();
        assertThat(obtenidas).containsExactlyElementsOf(esperadas);
        for (int i = 0; i < fragmentos.size(); i++) {
            Fragmento.Nuevo f = fragmentos.get(i);
            assertThat(f.orden()).isEqualTo(i);
            assertThat(f.texto().length() <= Troceador.MAXIMO || palabras(f.texto()).size() == 1).as("largo del fragmento %d", i).isTrue();
        }
    }

    @Provide
    Arbitrary<List<String>> paginas() {
        Arbitrary<String> palabra = Arbitraries.frequencyOf(
                net.jqwik.api.Tuple.of(50, Arbitraries.strings().withCharRange('a', 'z').withChars("áéíóúñ").ofMinLength(1).ofMaxLength(12)),
                net.jqwik.api.Tuple.of(10, Arbitraries.of("1.200", "personas.", "barrio,", "«centro»", "300.", "¿por", "qué?")),
                net.jqwik.api.Tuple.of(1, Arbitraries.strings().withCharRange('a', 'z').ofMinLength(1700).ofMaxLength(1800)));
        Arbitrary<String> separador = Arbitraries.of(" ", " ", " ", "\n", "  ", "\n\n", "\n \n", "\t", "\r\n");
        Arbitrary<String> texto = Combinators.combine(palabra.list().ofMinSize(0).ofMaxSize(400), separador.list().ofSize(400)).as((ps, ss) -> {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < ps.size(); i++) {
                sb.append(ps.get(i)).append(ss.get(i));
            }
            return sb.toString();
        });
        return texto.list().ofMinSize(1).ofMaxSize(4);
    }
}
