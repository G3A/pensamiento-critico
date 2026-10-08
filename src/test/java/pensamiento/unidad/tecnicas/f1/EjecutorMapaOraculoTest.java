package pensamiento.unidad.tecnicas.f1;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import pensamiento.argdown.ParserArgdown;
import pensamiento.catalogo.CatalogoJson;
import pensamiento.catalogo.MapeadorJson;
import pensamiento.nucleo.AfirmacionConRol;
import pensamiento.nucleo.ArgumentoProducido;
import pensamiento.nucleo.EstandarPrueba;
import pensamiento.nucleo.Ejemplo;
import pensamiento.nucleo.Pendiente;
import pensamiento.nucleo.Resultado;
import pensamiento.nucleo.RolAfirmacion;
import pensamiento.tecnicas.f1.EjecutorMapa;
import pensamiento.tecnicas.f1.ResultadoMapa;
import pensamiento.testutil.builders.Contextos;

/**
 * Oráculo de T01 · Mapeo de argumentos: los ejemplos de docs/ejemplos/T01.md, tal como la semilla los
 * transcribe a la tabla ejemplo. El parser es el adaptador real del puerto Argdown: puro y certificado por
 * ParserArgdownContractTest.
 */
class EjecutorMapaOraculoTest {

    record NodoEsperado(String codigo, String texto, String rol) {
    }

    record ArgumentoEsperado(String codigo, String titulo, String sentido, int peso, String conclusion, List<String> premisas,
                             boolean aplicable, String motivo) {
    }

    record Esperado(String argdown, List<NodoEsperado> nodos, List<ArgumentoEsperado> argumentos, List<ResultadoMapa.Aceptabilidad> conclusiones,
                    int apoyos, int ataques, List<String> objecionesSinResponder, List<String> pendientes, String resumen) {
    }

    private final EjecutorMapa t01 = new EjecutorMapa(new ParserArgdown());

    static Stream<Ejemplo> ejemplos() {
        return new CatalogoJson().ejemplosDe(EjecutorMapa.ID).stream();
    }

    @Test
    void hay_tres_ejemplos_uno_por_ambito() {
        assertThat(ejemplos().map(Ejemplo::ambito)).containsExactlyInAnyOrder(Ejemplo.Ambito.PERSONAL, Ejemplo.Ambito.TRABAJO, Ejemplo.Ambito.COMUNIDAD);
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("ejemplos")
    void cada_ejemplo_produce_el_mapa_escrito_a_mano(Ejemplo ejemplo) {
        EjecutorMapa.Config config = MapeadorJson.leer(ejemplo.config(), EjecutorMapa.Config.class);
        EjecutorMapa.Entrada entrada = MapeadorJson.leer(ejemplo.datos(), EjecutorMapa.Entrada.class);
        Esperado esperado = MapeadorJson.leer(ejemplo.resultado(), Esperado.class);

        assertThat(t01.validar(config, entrada).errores()).as("el ejemplo es válido").isEmpty();
        Resultado<ResultadoMapa> r = t01.ejecutar(config, entrada, Contextos.sinIa());
        ResultadoMapa v = r.valor();

        assertThat(v.argdown()).isEqualTo(esperado.argdown());
        assertThat(v.nodos()).extracting(n -> new NodoEsperado(n.codigo(), n.texto(), n.rol().toString())).containsExactlyElementsOf(esperado.nodos());
        assertThat(v.argumentos()).extracting(a -> new ArgumentoEsperado(a.codigo(), a.titulo(), a.sentido().toString(), a.peso(), a.conclusion(),
                a.premisas(), a.aplicable(), a.motivo())).containsExactlyElementsOf(esperado.argumentos());
        assertThat(v.conclusiones()).containsExactlyElementsOf(esperado.conclusiones());
        assertThat(v.apoyos()).isEqualTo(esperado.apoyos());
        assertThat(v.ataques()).isEqualTo(esperado.ataques());
        assertThat(v.objecionesSinResponder()).containsExactlyElementsOf(esperado.objecionesSinResponder());
        assertThat(r.pendientes()).extracting(Pendiente::descripcion).containsExactlyElementsOf(esperado.pendientes());
        assertThat(r.resumen()).isEqualTo(esperado.resumen()).isEqualTo(v.resumen());
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("ejemplos")
    void cada_nodo_es_una_afirmacion_y_cada_argumento_se_guarda_con_el_texto_argdown(Ejemplo ejemplo) {
        EjecutorMapa.Config config = MapeadorJson.leer(ejemplo.config(), EjecutorMapa.Config.class);
        Resultado<ResultadoMapa> r = t01.ejecutar(config, MapeadorJson.leer(ejemplo.datos(), EjecutorMapa.Entrada.class), Contextos.sinIa());

        assertThat(r.afirmaciones()).extracting(AfirmacionConRol::afirmacionId)
                .containsExactlyElementsOf(r.valor().nodos().stream().map(ResultadoMapa.Nodo::afirmacionId).toList());
        assertThat(r.afirmaciones()).filteredOn(a -> a.rol() == RolAfirmacion.CONCLUSION).hasSize(1);
        assertThat(r.argumentos()).hasSameSizeAs(r.valor().argumentos());
        assertThat(r.argumentos()).allSatisfy(a -> {
            assertThat(a.textoArgdown()).contains(r.valor().argdown());
            assertThat(a.estandar()).isEqualTo(config.estandar());
        });
        assertThat(r.argumentos()).extracting(a -> a.argumento().id())
                .containsExactlyElementsOf(r.valor().argumentos().stream().map(ResultadoMapa.ArgumentoMapa::argumentoId).toList());
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("ejemplos")
    void el_resultado_hace_ida_y_vuelta_por_el_jsonb_sin_perder_nada(Ejemplo ejemplo) {
        EjecutorMapa.Config config = MapeadorJson.leer(ejemplo.config(), EjecutorMapa.Config.class);
        ResultadoMapa v = t01.ejecutar(config, MapeadorJson.leer(ejemplo.datos(), EjecutorMapa.Entrada.class), Contextos.sinIa()).valor();

        assertThat(MapeadorJson.leer(MapeadorJson.escribir(v), ResultadoMapa.class)).isEqualTo(v);
    }

    @Test
    void la_segunda_sucursal_no_es_aceptable_mas_alla_de_duda_razonable_por_sus_supuestos() {
        // Ejemplo 2 de T01.md con el estándar más exigente: el único pro aplicable descansa en premisas asumibles (R04).
        Ejemplo sucursal = ejemplos().filter(e -> e.titulo().equals("La segunda sucursal")).findFirst().orElseThrow();
        EjecutorMapa.Config exigente = new EjecutorMapa.Config(ResultadoMapa.Direccion.ARRIBA_ABAJO, true, true, EstandarPrueba.MAS_ALLA_DE_DUDA_RAZONABLE);

        ResultadoMapa v = t01.ejecutar(exigente, MapeadorJson.leer(sucursal.datos(), EjecutorMapa.Entrada.class), Contextos.sinIa()).valor();

        assertThat(v.conclusiones()).singleElement().satisfies(c -> {
            assertThat(c.aceptable()).isFalse();
            assertThat(c.frase()).isEqualTo("No aceptable bajo más allá de duda razonable: los argumentos a favor aplicables no alcanzan el estándar.");
        });
    }

    @Test
    void un_supuesto_atacado_bloquea_el_argumento_salvo_en_escrutinio() {
        String texto = """
                [A]: Conviene vender pan integral.
                  + [B]: Los clientes lo piden. #asumible
                    - [C]: Solo lo pidieron dos personas.""";
        EjecutorMapa.Entrada entrada = new EjecutorMapa.Entrada(texto);

        ResultadoMapa preponderancia = t01.ejecutar(new EjecutorMapa.Config(ResultadoMapa.Direccion.ARRIBA_ABAJO, true, false,
                EstandarPrueba.PREPONDERANCIA), entrada, Contextos.sinIa()).valor();
        ResultadoMapa escrutinio = t01.ejecutar(new EjecutorMapa.Config(ResultadoMapa.Direccion.ARRIBA_ABAJO, true, false,
                EstandarPrueba.ESCRUTINIO), entrada, Contextos.sinIa()).valor();

        assertThat(preponderancia.argumentos().getFirst().motivo()).isEqualTo("No aplicable: el supuesto «Los clientes lo piden.» tiene una objeción.");
        assertThat(escrutinio.argumentos().getFirst().aplicable()).isTrue();
        assertThat(escrutinio.argumentos().getFirst().motivo())
                .isEqualTo("Aplicable bajo escrutinio, aunque el supuesto «Los clientes lo piden.» tiene una objeción.");
        assertThat(escrutinio.conclusiones().getFirst().aceptable()).isTrue();
    }

    @Test
    void un_texto_fuera_del_subconjunto_vuelve_como_error_del_campo_con_linea_y_columna() {
        var validacion = t01.validar(new EjecutorMapa.Config(ResultadoMapa.Direccion.ARRIBA_ABAJO, true, false, EstandarPrueba.PREPONDERANCIA),
                new EjecutorMapa.Entrada("Conclusión.\n  * viñeta"));

        assertThat(validacion.errores()).singleElement().satisfies(e -> {
            assertThat(e.campo()).isEqualTo("argdown");
            assertThat(e.mensaje()).startsWith("Línea 2, columna 3: ");
        });
    }

    @Test
    void una_referencia_repetida_es_un_solo_nodo_y_una_sola_afirmacion() {
        String texto = """
                [A]: Conviene abrir los domingos.
                  + [B]: Los domingos hay más gente en la plaza.

                [C]: Conviene contratar a alguien para el domingo.
                  + [B]""";
        Resultado<ResultadoMapa> r = t01.ejecutar(new EjecutorMapa.Config(ResultadoMapa.Direccion.ARRIBA_ABAJO, true, false,
                EstandarPrueba.PREPONDERANCIA), new EjecutorMapa.Entrada(texto), Contextos.sinIa());

        assertThat(r.afirmaciones()).hasSize(3);
        assertThat(r.valor().argumentos()).extracting(ResultadoMapa.ArgumentoMapa::premisas).containsExactly(List.of("N2"), List.of("N2"));
        assertThat(r.valor().conclusiones()).extracting(ResultadoMapa.Aceptabilidad::conclusion).containsExactly("N1", "N3");
        assertThat(r.argumentos()).extracting(ArgumentoProducido::argumento).hasSize(2);
    }
}
