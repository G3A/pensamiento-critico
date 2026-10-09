package pensamiento.unidad.tecnicas.f6;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

import pensamiento.catalogo.CatalogoJson;
import pensamiento.nucleo.Esquema;
import pensamiento.tecnicas.f6.EjecutorEquipoRojo;
import pensamiento.tecnicas.f6.EjecutorTuring;
import pensamiento.tecnicas.f6.ResultadoTuring;
import pensamiento.testutil.builders.Contextos;
import pensamiento.testutil.fakes.FakeRepositorioEsquemas;

/**
 * Propiedades de F6: el puntaje de la rúbrica de T37 · Test de Turing ideológico queda entre 0 y 100 y no baja si se
 * quita una marca de burla; el equipo rojo de T36 nunca pasa del número de ataques, numera A1, A2… sin saltos, no repite
 * una pregunta crítica sobre la misma razón y solo usa preguntas que existen en su esquema.
 */
class EjecutoresF6PropiedadesTest {

    private static final List<String> TROZOS = List.of("solo quieren ganar", "es absurdo", "graban a todos", "cuesta mucho", "obviamente",
            "la privacidad importa", "no entienden nada", "jaja", "el costo mensual", "mueven los robos", "la cuadra de al lado");

    @Provide
    Arbitrary<String> textos() {
        return Arbitraries.of(TROZOS).list().ofMinSize(1).ofMaxSize(6).map(l -> String.join(", ", l) + ".");
    }

    @Provide
    Arbitrary<List<EjecutorTuring.Argumento>> argumentos() {
        return Arbitraries.of(new EjecutorTuring.Argumento("El costo.", "costo"), new EjecutorTuring.Argumento("La privacidad.", "privacidad, grab"),
                new EjecutorTuring.Argumento("Los robos se mueven.", "muev, mover"), new EjecutorTuring.Argumento("El barrio.", "barrio")).list().ofMaxSize(4);
    }

    @Property
    void el_puntaje_de_la_rubrica_queda_entre_0_y_100(@ForAll("textos") String texto, @ForAll("argumentos") List<EjecutorTuring.Argumento> argumentos,
                                                       @ForAll @IntRange(min = 0, max = 100) int pesoCaricatura, @ForAll @IntRange(min = 0, max = 100) int pesoOmision,
                                                       @ForAll @IntRange(min = 0, max = 100) int umbral) {
        int pesoTono = 100 - pesoCaricatura - pesoOmision;
        EjecutorTuring t37 = new EjecutorTuring();
        EjecutorTuring.Config config = new EjecutorTuring.Config(pesoCaricatura, pesoOmision, pesoTono, umbral);
        EjecutorTuring.Entrada entrada = new EjecutorTuring.Entrada("Quienes se oponen", texto, null, argumentos);
        if (!t37.validar(config, entrada).esValida()) {
            return;
        }
        ResultadoTuring r = t37.ejecutar(config, entrada, Contextos.sinIa()).valor();
        assertThat(r.puntaje()).isBetween(0, 100);
        assertThat(r.aprueba()).isEqualTo(r.puntaje() >= umbral);
    }

    @Property
    void quitar_una_burla_no_baja_el_puntaje(@ForAll("textos") String texto) {
        EjecutorTuring t37 = new EjecutorTuring();
        EjecutorTuring.Config config = new EjecutorTuring.Config(30, 40, 30, 70);
        int con = t37.ejecutar(config, new EjecutorTuring.Entrada("Quienes se oponen", texto + " Es absurdo.", null, List.of()), Contextos.sinIa()).valor().puntaje();
        int sin = t37.ejecutar(config, new EjecutorTuring.Entrada("Quienes se oponen", texto, null, List.of()), Contextos.sinIa()).valor().puntaje();
        assertThat(sin).isGreaterThanOrEqualTo(con);
    }

    @Provide
    Arbitrary<List<EjecutorEquipoRojo.Razon>> razones() {
        Arbitrary<String> textos = Arbitraries.of("El vendedor dice que sirven.", "Todos lo hacen.", "Mi tía lo hizo y le fue bien.",
                "Desde que llegó, todo empeoró, así que es su culpa.", "Si no lo hacemos, vamos a cerrar.", "Es como en el barrio de al lado.");
        Arbitrary<EjecutorEquipoRojo.Apoyo> apoyos = Arbitraries.of(EjecutorEquipoRojo.Apoyo.class);
        return net.jqwik.api.Combinators.combine(textos, apoyos).as(EjecutorEquipoRojo.Razon::new).list().ofMinSize(1).ofMaxSize(5);
    }

    @Property
    void el_equipo_rojo_no_pasa_del_numero_de_ataques_ni_repite_pregunta_sobre_la_misma_razon(@ForAll("razones") List<EjecutorEquipoRojo.Razon> razones,
                                                                                               @ForAll @IntRange(min = 1, max = 3) int intensidad,
                                                                                               @ForAll @IntRange(min = 1, max = 6) int numero) {
        List<Esquema> catalogo = new CatalogoJson().esquemas();
        EjecutorEquipoRojo t36 = new EjecutorEquipoRojo(new FakeRepositorioEsquemas(catalogo));
        EjecutorEquipoRojo.Config config = new EjecutorEquipoRojo.Config(intensidad, numero, catalogo.stream().map(Esquema::id).toList(),
                EjecutorEquipoRojo.Modo.BANCO);

        List<EjecutorEquipoRojo.AtaquePlaneado> plan = t36.planificar(config, razones);

        assertThat(plan.size()).isBetween(1, numero);
        Set<String> usadas = new HashSet<>();
        List<String> codigos = new ArrayList<>();
        for (EjecutorEquipoRojo.AtaquePlaneado a : plan) {
            codigos.add(a.codigo());
            assertThat(usadas.add(a.razon() + ":" + a.pregunta())).as("una pregunta por razón una sola vez").isTrue();
            assertThat(a.texto()).endsWith("?");
            if (a.esquema() != null) {
                Esquema e = catalogo.stream().filter(x -> x.id().equals(a.esquema())).findFirst().orElseThrow();
                assertThat(e.pregunta(a.pregunta())).as("la pregunta existe en su esquema").isPresent();
            }
        }
        assertThat(codigos).containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, plan.size()).mapToObj(i -> "A" + i).toList());
    }
}
