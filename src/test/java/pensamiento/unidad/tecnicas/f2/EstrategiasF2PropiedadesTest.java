package pensamiento.unidad.tecnicas.f2;

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
import net.jqwik.api.constraints.Size;

import pensamiento.nucleo.Resultado;
import pensamiento.tecnicas.f1.EjecutorPaulElder.Elemento;
import pensamiento.tecnicas.f2.EjecutorCincoPorques;
import pensamiento.tecnicas.f2.EjecutorEscalera;
import pensamiento.tecnicas.f2.EstrategiaSocratica;
import pensamiento.tecnicas.f2.EstrategiaSocratica.ModoSesion;
import pensamiento.tecnicas.f2.EstrategiaSocratica.Orden;
import pensamiento.tecnicas.f2.EstrategiaSocratica.TipoSocratico;
import pensamiento.tecnicas.f2.ResultadoCincoPorques;
import pensamiento.testutil.builders.Contextos;

/**
 * Propiedades de las estrategias de F2: el motor socrático nunca repite un tipo dos turnos seguidos si queda otro pendiente
 * de otro tipo, nunca pregunta dos veces el mismo elemento ni pasa de los turnos máximos; la escalera avanza un peldaño por
 * turno y nunca retrocede; los 5 porqués nunca pasan del número de niveles configurado.
 */
class EstrategiasF2PropiedadesTest {

    private final EstrategiaSocratica estrategia = EstrategiaSocratica.delCatalogo();

    /** Frases con y sin marcas (términos difusos, absolutas, cifras, causas) para mover las ramas y los saltos. */
    private static final List<String> FRASES = List.of("Creo que es mejor así.", "Nunca pasa nada.", "Pasan 1.000 personas.", "Me dijo una vecina.",
            "Lo hice porque no había tiempo.", "Todos lo saben.", "Es importante y rápido.", "No sé.", "Sí, a veces.", "La seguridad del barrio.");

    @Provide
    Arbitrary<List<String>> respuestas() {
        return Arbitraries.of(FRASES).list().ofMinSize(0).ofMaxSize(12);
    }

    @Provide
    Arbitrary<Set<TipoSocratico>> tipos() {
        return Arbitraries.of(TipoSocratico.class).set().ofMinSize(2).ofMaxSize(6);
    }

    @Property
    void el_motor_no_repite_tipo_si_queda_otro_pendiente_y_no_pregunta_dos_veces_lo_mismo(@ForAll("tipos") Set<TipoSocratico> tipos,
                                                                                            @ForAll Orden orden, @ForAll ModoSesion modo,
                                                                                            @ForAll @IntRange(min = 3, max = 12) int turnosMaximos,
                                                                                            @ForAll("respuestas") List<String> respuestas) {
        EstrategiaSocratica.Parametros p = new EstrategiaSocratica.Parametros(tipos, orden, turnosMaximos, modo);
        EstrategiaSocratica.Recorrido r = estrategia.recorrer(p, "Hay que abrir los domingos.", respuestas, false);

        List<EstrategiaSocratica.Movimiento> movimientos = new ArrayList<>(r.pasos().stream().map(EstrategiaSocratica.Paso::movimiento).toList());
        r.siguiente().ifPresent(movimientos::add);
        assertThat(movimientos.size()).isLessThanOrEqualTo(turnosMaximos);
        Set<Elemento> preguntados = new HashSet<>();
        for (int i = 0; i < movimientos.size(); i++) {
            EstrategiaSocratica.Movimiento m = movimientos.get(i);
            assertThat(m.numero()).isEqualTo(i + 1);
            assertThat(tipos).as("solo tipos activos").contains(m.tipo());
            assertThat(preguntados.add(m.elemento())).as("cada elemento se pregunta una vez").isTrue();
            assertThat(m.pregunta()).as("toda pregunta del banco termina en pregunta").endsWith("?");
            if (i > 0 && m.tipo() == movimientos.get(i - 1).tipo()) {
                Set<Elemento> pendientesAntes = pendientes(p, preguntados, m.elemento());
                assertThat(pendientesAntes).as("repite tipo solo si no quedaba otro pendiente de otro tipo")
                        .allSatisfy(e -> assertThat(estrategia.tipoDe(e)).isEqualTo(m.tipo()));
            }
        }
        assertThat(r.tocaCierre()).isEqualTo(r.siguiente().isEmpty());
    }

    /** Los elementos con tipo activo que seguían sin preguntar cuando se eligió m (sin contar m). */
    private Set<Elemento> pendientes(EstrategiaSocratica.Parametros p, Set<Elemento> preguntadosHastaM, Elemento m) {
        Set<Elemento> pendientes = new HashSet<>();
        for (Elemento e : Elemento.values()) {
            if (p.tipos().contains(estrategia.tipoDe(e)) && !preguntadosHastaM.contains(e)) {
                pendientes.add(e);
            }
        }
        pendientes.remove(m);
        return pendientes;
    }

    @Property
    void la_escalera_avanza_un_peldano_por_turno_y_nunca_retrocede(@ForAll("peldanos") List<EjecutorEscalera.Peldano> activos,
                                                                    @ForAll EjecutorEscalera.Sentido sentido) {
        EjecutorEscalera.Config config = new EjecutorEscalera.Config(activos, sentido);
        List<EjecutorEscalera.Peldano> recorridos = new ArrayList<>();
        for (int n = 0; ; n++) {
            var siguiente = EjecutorEscalera.siguiente(config, n);
            if (siguiente.isEmpty()) {
                break;
            }
            recorridos.add(siguiente.get());
        }
        assertThat(recorridos).as("un peldaño por turno, cada activo una vez").containsExactlyInAnyOrderElementsOf(Set.copyOf(activos));
        for (int i = 1; i < recorridos.size(); i++) {
            int antes = recorridos.get(i - 1).ordinal();
            int ahora = recorridos.get(i).ordinal();
            assertThat(sentido == EjecutorEscalera.Sentido.SUBIR ? ahora > antes : ahora < antes).as("nunca retrocede").isTrue();
        }
    }

    @Provide
    Arbitrary<List<EjecutorEscalera.Peldano>> peldanos() {
        return Arbitraries.of(EjecutorEscalera.Peldano.class).set().ofMinSize(3).ofMaxSize(6).map(List::copyOf);
    }

    @Property
    void los_porques_nunca_pasan_del_numero_de_niveles(@ForAll @IntRange(min = 3, max = 7) int niveles, @ForAll boolean ramas,
                                                       @ForAll("cadenas") List<Integer> respondeA) {
        List<EjecutorCincoPorques.Porque> porques = new ArrayList<>();
        for (int i = 0; i < respondeA.size(); i++) {
            int a = respondeA.get(i);
            String responde = !ramas || a < 0 || a >= i ? null : a == 0 ? "problema" : "P" + a;
            porques.add(new EjecutorCincoPorques.Porque("Porque " + i, i % 2 == 0 ? "dato" : null, responde, i % 3 == 0));
        }
        EjecutorCincoPorques t09 = new EjecutorCincoPorques();
        EjecutorCincoPorques.Config config = new EjecutorCincoPorques.Config(niveles, true, ramas);
        EjecutorCincoPorques.Entrada entrada = new EjecutorCincoPorques.Entrada("Se quemó el pan.", porques);
        if (!t09.validar(config, entrada).esValida()) {
            return;
        }
        Resultado<ResultadoCincoPorques> r = t09.ejecutar(config, entrada, Contextos.sinIa());
        assertThat(r.valor().porques()).allSatisfy(p -> assertThat(p.nivel()).isBetween(1, niveles));
        assertThat(r.valor().causasRaiz() + r.valor().sinTerminar()).as("toda hoja es causa raíz o queda sin terminar")
                .isEqualTo((int) r.valor().porques().stream().filter(p -> p.estado() != ResultadoCincoPorques.Estado.INTERMEDIO).count());
    }

    @Provide
    Arbitrary<List<Integer>> cadenas() {
        return Arbitraries.integers().between(-1, 10).list().ofMinSize(1).ofMaxSize(12);
    }

    @Property
    void con_una_cadena_mas_larga_que_los_niveles_la_validacion_lo_rechaza(@ForAll @IntRange(min = 3, max = 7) int niveles,
                                                                           @ForAll @Size(min = 1, max = 4) List<@IntRange(min = 1, max = 3) Integer> extra) {
        List<EjecutorCincoPorques.Porque> porques = new ArrayList<>();
        for (int i = 0; i < niveles + extra.size(); i++) {
            porques.add(new EjecutorCincoPorques.Porque("Porque " + i, null, null, false));
        }
        assertThat(new EjecutorCincoPorques().validar(new EjecutorCincoPorques.Config(niveles, false, false),
                new EjecutorCincoPorques.Entrada("Se quemó el pan.", porques)).esValida()).isFalse();
    }
}
