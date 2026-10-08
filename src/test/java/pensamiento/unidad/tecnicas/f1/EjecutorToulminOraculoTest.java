package pensamiento.unidad.tecnicas.f1;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import pensamiento.catalogo.CatalogoJson;
import pensamiento.catalogo.MapeadorJson;
import pensamiento.nucleo.AfirmacionConRol;
import pensamiento.nucleo.Argumento;
import pensamiento.nucleo.Ejemplo;
import pensamiento.nucleo.Pendiente;
import pensamiento.nucleo.Resultado;
import pensamiento.nucleo.RolAfirmacion;
import pensamiento.tecnicas.f1.EjecutorToulmin;
import pensamiento.tecnicas.f1.ResultadoToulmin;
import pensamiento.testutil.builders.Contextos;

/** Oráculo de T02 · Modelo de Toulmin: los ejemplos de docs/ejemplos/T02.md. */
class EjecutorToulminOraculoTest {

    record ParteEsperada(String parte, String estado, String falta) {
    }

    record Esperado(List<ParteEsperada> partes, int completas, int total, List<String> pendientes, String resumen) {
    }

    private final EjecutorToulmin t02 = new EjecutorToulmin();

    static Stream<Ejemplo> ejemplos() {
        return new CatalogoJson().ejemplosDe(EjecutorToulmin.ID).stream();
    }

    @Test
    void hay_tres_ejemplos_uno_por_ambito() {
        assertThat(ejemplos().map(Ejemplo::ambito)).containsExactlyInAnyOrder(Ejemplo.Ambito.PERSONAL, Ejemplo.Ambito.TRABAJO, Ejemplo.Ambito.COMUNIDAD);
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("ejemplos")
    void cada_ejemplo_produce_la_lista_de_verificacion_escrita_a_mano(Ejemplo ejemplo) {
        EjecutorToulmin.Config config = MapeadorJson.leer(ejemplo.config(), EjecutorToulmin.Config.class);
        EjecutorToulmin.Entrada entrada = MapeadorJson.leer(ejemplo.datos(), EjecutorToulmin.Entrada.class);
        Esperado esperado = MapeadorJson.leer(ejemplo.resultado(), Esperado.class);

        assertThat(t02.validar(config, entrada).errores()).as("el ejemplo es válido").isEmpty();
        Resultado<ResultadoToulmin> r = t02.ejecutar(config, entrada, Contextos.sinIa());

        assertThat(r.valor().partes()).extracting(p -> new ParteEsperada(p.parte().toString(), p.estado().toString(), p.falta()))
                .containsExactlyElementsOf(esperado.partes());
        assertThat(r.valor().completas()).isEqualTo(esperado.completas());
        assertThat(r.valor().total()).isEqualTo(esperado.total());
        assertThat(r.pendientes()).extracting(Pendiente::descripcion).containsExactlyElementsOf(esperado.pendientes());
        assertThat(r.resumen()).isEqualTo(esperado.resumen()).isEqualTo(r.valor().resumen());
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("ejemplos")
    void la_afirmacion_es_la_conclusion_y_la_garantia_un_supuesto_del_argumento_a_favor(Ejemplo ejemplo) {
        EjecutorToulmin.Config config = MapeadorJson.leer(ejemplo.config(), EjecutorToulmin.Config.class);
        EjecutorToulmin.Entrada entrada = MapeadorJson.leer(ejemplo.datos(), EjecutorToulmin.Entrada.class);

        Resultado<ResultadoToulmin> r = t02.ejecutar(config, entrada, Contextos.sinIa());

        assertThat(r.afirmaciones()).filteredOn(a -> a.rol() == RolAfirmacion.CONCLUSION).extracting(AfirmacionConRol::texto)
                .containsExactly(entrada.afirmacion());
        Argumento pro = r.argumentos().getFirst().argumento();
        assertThat(pro.sentido()).isEqualTo(Argumento.Sentido.PRO);
        assertThat(pro.premisas()).extracting(Argumento.Premisa::asumible).containsExactly(false, true);
        assertThat(r.argumentos()).filteredOn(a -> a.argumento().sentido() == Argumento.Sentido.CONTRA).hasSize(1);
        assertThat(MapeadorJson.leer(MapeadorJson.escribir(r.valor()), ResultadoToulmin.class)).isEqualTo(r.valor());
    }

    @Test
    void en_el_nivel_basico_solo_cuentan_las_tres_primeras_partes() {
        EjecutorToulmin.Config basico = new EjecutorToulmin.Config(ResultadoToulmin.Nivel.BASICO, List.of(ResultadoToulmin.Parte.AFIRMACION), false);
        EjecutorToulmin.Entrada entrada = new EjecutorToulmin.Entrada("Hay que pintar el salón comunal.", "Las paredes tienen humedad.",
                null, null, null, null, "Salvo que no haya plata.", null);

        Resultado<ResultadoToulmin> r = t02.ejecutar(basico, entrada, Contextos.sinIa());

        assertThat(r.valor().total()).isEqualTo(3);
        assertThat(r.resumen()).isEqualTo("Completitud 2 de 3: falta garantía.");
        assertThat(r.pendientes()).as("la refutación no se evalúa en el nivel básico").isEmpty();
    }

    @Test
    void una_parte_obligatoria_vacia_impide_evaluar_y_lo_dice_junto_al_campo() {
        EjecutorToulmin.Config config = new EjecutorToulmin.Config(ResultadoToulmin.Nivel.COMPLETO,
                List.of(ResultadoToulmin.Parte.AFIRMACION, ResultadoToulmin.Parte.GARANTIA), false);
        EjecutorToulmin.Entrada entrada = new EjecutorToulmin.Entrada("Conviene abrir los domingos.", "El domingo hay feria.",
                "  ", null, null, null, null, null);

        assertThat(t02.validar(config, entrada).errores()).extracting(e -> e.campo() + ": " + e.mensaje())
                .containsExactly("garantia: Garantía: es obligatorio en tu configuración.");
    }
}
