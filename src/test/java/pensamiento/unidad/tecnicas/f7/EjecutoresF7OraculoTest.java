package pensamiento.unidad.tecnicas.f7;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import pensamiento.catalogo.CatalogoJson;
import pensamiento.catalogo.MapeadorJson;
import pensamiento.nucleo.AfirmacionConRol;
import pensamiento.nucleo.Ejecutor;
import pensamiento.nucleo.Ejemplo;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Pendiente;
import pensamiento.nucleo.Resultado;
import pensamiento.nucleo.RolAfirmacion;
import pensamiento.nucleo.TipoPendiente;
import pensamiento.tecnicas.f7.EjecutorArbolMece;
import pensamiento.tecnicas.f7.EjecutorDefinicionProblema;
import pensamiento.tecnicas.f7.EjecutorIshikawa;
import pensamiento.tecnicas.f7.EjecutorPrimerosPrincipios;
import pensamiento.tecnicas.f7.EjecutorScamper;
import pensamiento.tecnicas.f7.ResultadoArbolMece;
import pensamiento.tecnicas.f7.ResultadoDefinicionProblema;
import pensamiento.tecnicas.f7.ResultadoIshikawa;
import pensamiento.tecnicas.f7.ResultadoPrimerosPrincipios;
import pensamiento.tecnicas.f7.ResultadoScamper;
import pensamiento.testutil.builders.Contextos;

/**
 * Oráculo de T40 a T44 con los ejemplos de docs/ejemplos/T40.md a T44.md, leídos del catálogo: lo que el ejecutor produce
 * es lo escrito a mano, nunca recalculado con la fórmula de producción.
 */
class EjecutoresF7OraculoTest {

    private static final CatalogoJson CATALOGO = new CatalogoJson();

    record ResumenYPendientes(String resumen, List<String> pendientes) {
    }

    static Stream<IdTecnica> tecnicas() {
        return Stream.of(EjecutorDefinicionProblema.ID, EjecutorPrimerosPrincipios.ID, EjecutorArbolMece.ID, EjecutorIshikawa.ID, EjecutorScamper.ID);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("tecnicas")
    void cada_tecnica_tiene_tres_ejemplos_uno_por_ambito(IdTecnica id) {
        assertThat(CATALOGO.ejemplosDe(id)).extracting(Ejemplo::ambito)
                .containsExactlyInAnyOrder(Ejemplo.Ambito.PERSONAL, Ejemplo.Ambito.TRABAJO, Ejemplo.Ambito.COMUNIDAD);
    }

    private static <C, E, R> Resultado<R> correr(Ejecutor<C, E, R> ejecutor, Ejemplo ejemplo) {
        C config = MapeadorJson.leer(ejemplo.config(), ejecutor.tipos().config());
        E entrada = MapeadorJson.leer(ejemplo.datos(), ejecutor.tipos().entrada());
        assertThat(ejecutor.validar(config, entrada).errores()).as(ejemplo.titulo()).isEmpty();
        Resultado<R> r = ejecutor.ejecutar(config, entrada, Contextos.sinIa());
        ResumenYPendientes esperado = MapeadorJson.leer(ejemplo.resultado(), ResumenYPendientes.class);
        assertThat(r.resumen()).as(ejemplo.titulo()).isEqualTo(esperado.resumen());
        assertThat(r.pendientes()).extracting(Pendiente::descripcion).as(ejemplo.titulo()).containsExactlyElementsOf(esperado.pendientes());
        assertThat(MapeadorJson.leer(MapeadorJson.escribir(r.valor()), ejecutor.tipos().resultado())).as("ida y vuelta por JSON").isEqualTo(r.valor());
        return r;
    }

    // ---------------------------------------------------------------------------------------------
    // T40 · Definición del problema
    // ---------------------------------------------------------------------------------------------

    record EsperadoT40(List<ResultadoDefinicionProblema.Item> items, String comparacion, String plantillas, List<String> avisos) {
    }

    @Test
    void t40_cada_ejemplo_da_la_lista_la_comparacion_y_los_avisos_escritos_a_mano() {
        EjecutorDefinicionProblema t40 = new EjecutorDefinicionProblema();
        for (Ejemplo e : CATALOGO.ejemplosDe(EjecutorDefinicionProblema.ID)) {
            Resultado<ResultadoDefinicionProblema> r = correr(t40, e);
            EsperadoT40 esperado = MapeadorJson.leer(e.resultado(), EsperadoT40.class);
            assertThat(r.valor().items()).as(e.titulo()).containsExactlyElementsOf(esperado.items());
            assertThat(r.valor().comparacion()).as(e.titulo()).isEqualTo(esperado.comparacion());
            assertThat(r.valor().plantillas()).as(e.titulo()).isEqualTo(esperado.plantillas());
            assertThat(r.valor().avisos()).as(e.titulo()).containsExactlyElementsOf(esperado.avisos());
            assertThat(r.afirmaciones()).as(e.titulo()).singleElement().satisfies(a -> {
                assertThat(a.rol()).isEqualTo(RolAfirmacion.POSTURA);
                assertThat(a.texto()).isEqualTo(esperado.items().getFirst().texto());
            });
        }
    }

    @Test
    void t40_sin_reformulacion_elegida_no_se_puede_avanzar() {
        EjecutorDefinicionProblema t40 = new EjecutorDefinicionProblema();
        var config = new EjecutorDefinicionProblema.Config(List.of(EjecutorDefinicionProblema.Plantilla.values()), 2);
        var entrada = new EjecutorDefinicionProblema.Entrada("¿Ponemos cámaras?", null, List.of(
                new EjecutorDefinicionProblema.Reformulacion("¿Cómo bajamos los robos?", null, null, false),
                new EjecutorDefinicionProblema.Reformulacion("¿Cómo se siente segura la gente?", null, null, false)));

        assertThat(t40.validar(config, entrada).errores()).extracting(pensamiento.nucleo.Validacion.Error::mensaje)
                .containsExactly("Elige una reformulación: es la que vas a resolver.");
    }

    // ---------------------------------------------------------------------------------------------
    // T41 · Primeros principios
    // ---------------------------------------------------------------------------------------------

    record EsperadoT41(int certezas, int supuestosSinComo, List<String> avisos) {
    }

    @Test
    void t41_cada_ejemplo_separa_certezas_y_supuestos_y_deja_un_pendiente_por_supuesto() {
        EjecutorPrimerosPrincipios t41 = new EjecutorPrimerosPrincipios();
        for (Ejemplo e : CATALOGO.ejemplosDe(EjecutorPrimerosPrincipios.ID)) {
            Resultado<ResultadoPrimerosPrincipios> r = correr(t41, e);
            EsperadoT41 esperado = MapeadorJson.leer(e.resultado(), EsperadoT41.class);
            assertThat(r.valor().certezas()).as(e.titulo()).hasSize(esperado.certezas());
            assertThat(r.valor().supuestos().stream().filter(ResultadoPrimerosPrincipios.Item::faltaComo).count()).as(e.titulo())
                    .isEqualTo(esperado.supuestosSinComo());
            assertThat(r.valor().avisos()).as(e.titulo()).containsExactlyElementsOf(esperado.avisos());
            assertThat(r.pendientes()).as("cada pendiente apunta a su supuesto").allSatisfy(p -> {
                assertThat(p.tipo()).isEqualTo(TipoPendiente.VERIFICACION);
                assertThat(r.afirmaciones()).filteredOn(a -> a.rol() == RolAfirmacion.SUPUESTO).extracting(AfirmacionConRol::afirmacionId)
                        .contains(p.objetoId().orElseThrow());
            });
        }
    }

    // ---------------------------------------------------------------------------------------------
    // T42 · Árbol de hipótesis MECE
    // ---------------------------------------------------------------------------------------------

    record EsperadoT42(List<ResultadoArbolMece.Clase> clases, List<String> hojas, List<String> huecos, List<ResultadoArbolMece.Solape> solapes) {
    }

    @Test
    void t42_cada_ejemplo_da_las_clases_hojas_huecos_y_solapes_escritos_a_mano() {
        EjecutorArbolMece t42 = new EjecutorArbolMece();
        for (Ejemplo e : CATALOGO.ejemplosDe(EjecutorArbolMece.ID)) {
            Resultado<ResultadoArbolMece> r = correr(t42, e);
            EsperadoT42 esperado = MapeadorJson.leer(e.resultado(), EsperadoT42.class);
            assertThat(r.valor().nodos()).extracting(ResultadoArbolMece.NodoArbol::clase).as(e.titulo()).containsExactlyElementsOf(esperado.clases());
            assertThat(r.valor().hojas()).as(e.titulo()).containsExactlyElementsOf(esperado.hojas());
            assertThat(r.valor().huecos()).as(e.titulo()).containsExactlyElementsOf(esperado.huecos());
            assertThat(r.valor().solapes()).as(e.titulo()).containsExactlyElementsOf(esperado.solapes());
        }
    }

    // ---------------------------------------------------------------------------------------------
    // T43 · Diagrama de Ishikawa
    // ---------------------------------------------------------------------------------------------

    record PorCategoria(String nombre, int causas, int faltan) {
    }

    record EsperadoT43(List<PorCategoria> porCategoria) {
    }

    @Test
    void t43_cada_ejemplo_reparte_las_causas_por_categoria_como_se_escribio_a_mano() {
        EjecutorIshikawa t43 = new EjecutorIshikawa();
        for (Ejemplo e : CATALOGO.ejemplosDe(EjecutorIshikawa.ID)) {
            Resultado<ResultadoIshikawa> r = correr(t43, e);
            EsperadoT43 esperado = MapeadorJson.leer(e.resultado(), EsperadoT43.class);
            assertThat(r.valor().categorias()).extracting(c -> new PorCategoria(c.nombre(), c.causas().size(), c.faltan())).as(e.titulo())
                    .containsExactlyElementsOf(esperado.porCategoria());
        }
    }

    // ---------------------------------------------------------------------------------------------
    // T44 · SCAMPER y pensamiento lateral
    // ---------------------------------------------------------------------------------------------

    record EsperadoT44(LinkedHashMap<String, Integer> cuentas, List<String> seleccionadas, List<String> avisos) {
    }

    @Test
    void t44_cada_ejemplo_cuenta_las_ideas_por_operador_y_deja_las_seleccionadas() {
        EjecutorScamper t44 = new EjecutorScamper();
        for (Ejemplo e : CATALOGO.ejemplosDe(EjecutorScamper.ID)) {
            Resultado<ResultadoScamper> r = correr(t44, e);
            EsperadoT44 esperado = MapeadorJson.leer(e.resultado(), EsperadoT44.class);
            Map<String, Integer> cuentas = new LinkedHashMap<>();
            r.valor().celdas().forEach(c -> cuentas.put(c.operador(), c.ideas().size()));
            assertThat(cuentas).as(e.titulo()).containsExactlyEntriesOf(esperado.cuentas());
            assertThat(r.valor().seleccionadas()).as(e.titulo()).containsExactlyElementsOf(esperado.seleccionadas());
            assertThat(r.valor().avisos()).as(e.titulo()).containsExactlyElementsOf(esperado.avisos());
            assertThat(r.afirmaciones()).as(e.titulo()).hasSize(esperado.seleccionadas().size()).allMatch(a -> a.rol() == RolAfirmacion.OPCION);
        }
    }
}
