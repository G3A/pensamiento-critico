package pensamiento.unidad.tecnicas.f3;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import pensamiento.catalogo.MapeadorJson;
import pensamiento.nucleo.Esquema;
import pensamiento.tecnicas.f3.ConfigFalacias;
import pensamiento.tecnicas.f3.EjecutorFalacias;
import pensamiento.tecnicas.f3.EntradaFalacias;
import pensamiento.tecnicas.f3.ResultadoFalacias;
import pensamiento.testutil.builders.Contextos;
import pensamiento.testutil.fakes.FakeRepositorioEsquemas;

/**
 * RF-10: las reglas de T13 · Falacias como esquemas fallidos contra el banco de 50 fragmentos etiquetados a
 * mano (src/test/resources/banco-fragmentos.json), con el umbral que el banco declara y que se escribió antes
 * de medir. La propuesta de cada fragmento es su primera marca, o ninguna.
 */
class BancoDeFragmentosTest {

    record Fragmento(String id, String escenario, String esquema, Integer pregunta, String texto) {
        boolean ninguna() {
            return "ninguna".equals(esquema);
        }
    }

    record Umbral(String escritoAntesDeMedir, int aciertoEsquemaMinimo, int aciertoEsquemaYPreguntaMinimo, int marcasSobreNingunaMaximo) {
    }

    record Banco(String descripcion, int version, String etiquetadoEn, Umbral umbral, List<Fragmento> fragmentos) {
    }

    private final FakeRepositorioEsquemas esquemas = new FakeRepositorioEsquemas();
    private final EjecutorFalacias t13 = new EjecutorFalacias(esquemas);
    private final Banco banco = leer();

    @Test
    void el_banco_tiene_50_fragmentos_de_los_tres_escenarios_con_etiquetas_del_catalogo() {
        assertThat(banco.fragmentos()).hasSize(50);
        assertThat(banco.fragmentos()).extracting(Fragmento::id).doesNotHaveDuplicates();
        assertThat(banco.fragmentos()).extracting(Fragmento::escenario).containsOnly("familia", "panaderia", "barrio");
        for (Fragmento f : banco.fragmentos()) {
            if (f.ninguna()) {
                assertThat(f.pregunta()).as(f.id()).isNull();
            } else {
                Esquema e = esquemas.porId(f.esquema()).orElseThrow(() -> new AssertionError(f.id() + ": esquema desconocido " + f.esquema()));
                assertThat(e.pregunta(f.pregunta())).as(f.id() + ": pregunta " + f.pregunta()).isPresent();
            }
        }
        assertThat(banco.fragmentos().stream().filter(Fragmento::ninguna)).hasSize(10);
    }

    @Test
    void las_reglas_cumplen_el_umbral_escrito_antes_de_medir() {
        int aciertoEsquema = 0;
        int aciertoEsquemaYPregunta = 0;
        int marcasSobreNinguna = 0;
        List<String> fallos = new ArrayList<>();
        for (Fragmento f : banco.fragmentos()) {
            Optional<ResultadoFalacias.Marca> primera = t13.ejecutar(ConfigFalacias.porDefecto(), new EntradaFalacias(f.texto(), List.of()),
                    Contextos.sinIa()).valor().marcas().stream().findFirst();
            String esquema = primera.map(ResultadoFalacias.Marca::esquema).orElse("ninguna");
            Integer pregunta = primera.map(ResultadoFalacias.Marca::pregunta).orElse(null);
            if (f.ninguna() && primera.isPresent()) {
                marcasSobreNinguna++;
            }
            if (esquema.equals(f.esquema())) {
                aciertoEsquema++;
                if (java.util.Objects.equals(pregunta, f.pregunta())) {
                    aciertoEsquemaYPregunta++;
                } else {
                    fallos.add(f.id() + " pregunta " + pregunta + " en vez de " + f.pregunta());
                }
            } else {
                fallos.add(f.id() + " " + esquema + " en vez de " + f.esquema());
            }
        }
        System.out.printf("Banco de fragmentos (RF-10): esquema %d/50, esquema y pregunta %d/50, marcas sobre \"ninguna\" %d/10. Fallos: %s%n",
                aciertoEsquema, aciertoEsquemaYPregunta, marcasSobreNinguna, fallos);

        assertThat(banco.umbral().escritoAntesDeMedir()).isEqualTo("2026-10-07");
        assertThat(aciertoEsquema).as("acierto de esquema; fallos: " + fallos).isGreaterThanOrEqualTo(banco.umbral().aciertoEsquemaMinimo());
        assertThat(aciertoEsquemaYPregunta).as("acierto de esquema y pregunta").isGreaterThanOrEqualTo(banco.umbral().aciertoEsquemaYPreguntaMinimo());
        assertThat(marcasSobreNinguna).as("marcas sobre los fragmentos sin esquema").isLessThanOrEqualTo(banco.umbral().marcasSobreNingunaMaximo());
    }

    private static Banco leer() {
        try (InputStream in = BancoDeFragmentosTest.class.getResourceAsStream("/banco-fragmentos.json")) {
            return MapeadorJson.mapper().readValue(in, Banco.class);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer el banco de fragmentos", e);
        }
    }
}
