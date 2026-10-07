package pensamiento.unidad.tecnicas.f5;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;


import pensamiento.tecnicas.f5.ConfigAch;
import pensamiento.tecnicas.f5.EjecutorAch;
import pensamiento.tecnicas.f5.EntradaAch;
import pensamiento.tecnicas.f5.ResultadoAch;
import pensamiento.testutil.builders.Contextos;

/** Propiedades de T28 · Análisis de hipótesis en competencia (ACH) sobre matrices generadas al azar. */
class EjecutorAchPropiedadesTest {

    /** Una matriz válida con su configuración. */
    record Caso(ConfigAch config, EntradaAch entrada) {
    }

    private final EjecutorAch ach = new EjecutorAch();

    @Property
    void agregar_una_evidencia_neutral_no_cambia_el_ranking(@ForAll("casos") Caso caso) {
        int n = caso.entrada().hipotesis().size();
        String neutral = caso.config().escala() == ConfigAch.Escala.CIN ? "N" : "0";
        List<EntradaAch.Evidencia> mas = new ArrayList<>(caso.entrada().evidencias());
        mas.add(new EntradaAch.Evidencia("Dato que no distingue", EntradaAch.Peso.ALTO, Collections.nCopies(n, neutral)));
        EntradaAch conNeutral = new EntradaAch(caso.entrada().pregunta(), caso.entrada().hipotesis(), mas);

        ResultadoAch antes = ach.ejecutar(caso.config(), caso.entrada(), Contextos.sinIa()).valor();
        ResultadoAch despues = ach.ejecutar(caso.config(), conNeutral, Contextos.sinIa()).valor();

        assertThat(inconsistencias(despues)).isEqualTo(inconsistencias(antes));
        assertThat(despues.menosRefutadas()).isEqualTo(antes.menosRefutadas());
        assertThat(despues.masRefutadas()).isEqualTo(antes.masRefutadas());
    }

    @Property
    void las_inconsistencias_ponderadas_nunca_son_negativas(@ForAll("casos") Caso caso) {
        ResultadoAch valor = ach.ejecutar(caso.config(), caso.entrada(), Contextos.sinIa()).valor();
        assertThat(inconsistencias(valor)).allMatch(i -> i >= 0);
    }

    @Property
    void permutar_las_hipotesis_no_cambia_cual_es_la_menos_refutada(@ForAll("casos") Caso caso, @ForAll long semilla) {
        int n = caso.entrada().hipotesis().size();
        List<Integer> orden = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            orden.add(i);
        }
        Collections.shuffle(orden, new java.util.Random(semilla));
        List<EntradaAch.Hipotesis> hipotesis = orden.stream().map(i -> caso.entrada().hipotesis().get(i)).toList();
        List<EntradaAch.Evidencia> evidencias = caso.entrada().evidencias().stream()
                .map(e -> new EntradaAch.Evidencia(e.texto(), e.peso(), orden.stream().map(i -> e.celdas().get(i)).toList()))
                .toList();
        EntradaAch permutada = new EntradaAch(caso.entrada().pregunta(), hipotesis, evidencias);

        ResultadoAch original = ach.ejecutar(caso.config(), caso.entrada(), Contextos.sinIa()).valor();
        ResultadoAch otra = ach.ejecutar(caso.config(), permutada, Contextos.sinIa()).valor();

        assertThat(textosMenosRefutadas(otra)).isEqualTo(textosMenosRefutadas(original));
    }

    @Property
    void siempre_hay_al_menos_una_menos_refutada_y_un_pendiente_por_cada_una(@ForAll("casos") Caso caso) {
        var resultado = ach.ejecutar(caso.config(), caso.entrada(), Contextos.sinIa());
        assertThat(resultado.valor().menosRefutadas()).isNotEmpty();
        assertThat(resultado.pendientes()).hasSameSizeAs(resultado.valor().menosRefutadas());
        assertThat(resultado.valor().empate()).isEqualTo(resultado.valor().menosRefutadas().size() > 1);
    }

    private static List<Integer> inconsistencias(ResultadoAch valor) {
        return valor.hipotesis().stream().map(ResultadoAch.HipotesisEvaluada::inconsistencias).toList();
    }

    private static Set<String> textosMenosRefutadas(ResultadoAch valor) {
        return valor.menosRefutadas().stream().map(c -> valor.hipotesis(c).texto()).collect(Collectors.toSet());
    }

    @Provide
    Arbitrary<Caso> casos() {
        Arbitrary<ConfigAch.Escala> escalas = Arbitraries.of(ConfigAch.Escala.class);
        Arbitrary<Boolean> pesos = Arbitraries.of(true, false);
        Arbitrary<Integer> hipotesis = Arbitraries.integers().between(2, 8);
        Arbitrary<Integer> evidencias = Arbitraries.integers().between(1, 11);
        return Combinators.combine(escalas, pesos, hipotesis, evidencias).flatAs((escala, conPesos, n, m) -> {
            Arbitrary<String> celda = escala == ConfigAch.Escala.CIN ? Arbitraries.of("C", "I", "N") : Arbitraries.of("-2", "-1", "0", "1", "2");
            Arbitrary<EntradaAch.Evidencia> evidencia = Combinators.combine(
                    Arbitraries.of(EntradaAch.Peso.class), celda.list().ofSize(n))
                    .as((peso, celdas) -> new EntradaAch.Evidencia("Evidencia", conPesos ? peso : null, celdas));
            return evidencia.list().ofSize(m).map(lista -> {
                List<EntradaAch.Hipotesis> hs = new ArrayList<>();
                for (int i = 0; i < n; i++) {
                    hs.add(new EntradaAch.Hipotesis("Hipótesis número " + (i + 1)));
                }
                return new Caso(new ConfigAch(8, escala, conPesos), new EntradaAch("¿Por qué pasó?", hs, lista));
            });
        });
    }
}
