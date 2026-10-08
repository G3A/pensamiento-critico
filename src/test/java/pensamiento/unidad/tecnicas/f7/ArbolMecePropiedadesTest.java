package pensamiento.unidad.tecnicas.f7;

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

import pensamiento.nucleo.Resultado;
import pensamiento.tecnicas.f7.EjecutorArbolMece;
import pensamiento.tecnicas.f7.ResultadoArbolMece;
import pensamiento.testutil.builders.Contextos;

/**
 * Propiedades de T42 · Árbol de hipótesis MECE: para cualquier árbol válido, ningún nodo queda huérfano (todo padre existe
 * y está antes), cada nodo es rama, intermedio u hoja según tenga hijos, y cada nodo tiene una afirmación distinta.
 */
class ArbolMecePropiedadesTest {

    private final EjecutorArbolMece t42 = new EjecutorArbolMece();

    @Property
    void ningun_nodo_queda_huerfano_y_cada_clase_corresponde_a_sus_hijos(@ForAll("arboles") List<EjecutorArbolMece.Nodo> nodos) {
        EjecutorArbolMece.Config config = new EjecutorArbolMece.Config(4, 1, true);
        EjecutorArbolMece.Entrada entrada = new EjecutorArbolMece.Entrada("¿Por qué pasa?", nodos);
        assertThat(t42.validar(config, entrada).errores()).isEmpty();

        Resultado<ResultadoArbolMece> r = t42.ejecutar(config, entrada, Contextos.sinIa());
        List<ResultadoArbolMece.NodoArbol> salida = r.valor().nodos();

        assertThat(salida).hasSize(nodos.size());
        Set<String> vistos = new HashSet<>();
        Set<String> conHijos = new HashSet<>();
        for (ResultadoArbolMece.NodoArbol n : salida) {
            if (n.padre() != null) {
                assertThat(vistos).as("el padre de " + n.codigo() + " existe y está antes").contains(n.padre());
                conHijos.add(n.padre());
            }
            vistos.add(n.codigo());
        }
        for (ResultadoArbolMece.NodoArbol n : salida) {
            boolean tieneHijos = conHijos.contains(n.codigo());
            if (n.profundidad() == 1) {
                assertThat(n.clase()).isEqualTo(tieneHijos ? ResultadoArbolMece.Clase.RAMA : ResultadoArbolMece.Clase.RAMA_VACIA);
            } else {
                assertThat(n.clase()).isEqualTo(tieneHijos ? ResultadoArbolMece.Clase.INTERMEDIO : ResultadoArbolMece.Clase.HOJA);
            }
        }
        assertThat(r.afirmaciones()).hasSize(nodos.size() + 1);
        assertThat(r.afirmaciones().stream().map(a -> a.afirmacionId()).distinct().count()).isEqualTo(nodos.size() + 1L);
        assertThat(r.valor().hojas()).hasSize((int) salida.stream().filter(n -> n.clase() == ResultadoArbolMece.Clase.HOJA).count());
    }

    /** Árboles de hasta 20 nodos: cada nodo cuelga de la raíz o de uno anterior, sin pasar la profundidad 4. */
    @Provide
    Arbitrary<List<EjecutorArbolMece.Nodo>> arboles() {
        return Arbitraries.integers().between(0, 1000).list().ofMinSize(1).ofMaxSize(20).map(elecciones -> {
            List<EjecutorArbolMece.Nodo> nodos = new ArrayList<>();
            List<Integer> profundidad = new ArrayList<>();
            for (int i = 0; i < elecciones.size(); i++) {
                List<Integer> posibles = new ArrayList<>();
                posibles.add(-1);
                for (int j = 0; j < i; j++) {
                    if (profundidad.get(j) < 4) {
                        posibles.add(j);
                    }
                }
                int padre = posibles.get(elecciones.get(i) % posibles.size());
                profundidad.add(padre < 0 ? 1 : profundidad.get(padre) + 1);
                nodos.add(new EjecutorArbolMece.Nodo("Causa número " + (i + 1), padre < 0 ? null : "N" + (padre + 1)));
            }
            return nodos;
        });
    }
}
