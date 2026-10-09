package pensamiento.unidad.tecnicas.f6;

import static org.assertj.core.api.Assertions.assertThat;

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
import pensamiento.tecnicas.f6.EjecutorDoubleCrux;
import pensamiento.tecnicas.f6.EjecutorEquipoRojo;
import pensamiento.tecnicas.f6.EjecutorEtico;
import pensamiento.tecnicas.f6.EjecutorSeisSombreros;
import pensamiento.tecnicas.f6.EjecutorTuring;
import pensamiento.tecnicas.f6.ResultadoDoubleCrux;
import pensamiento.tecnicas.f6.ResultadoEquipoRojo;
import pensamiento.tecnicas.f6.ResultadoEtico;
import pensamiento.tecnicas.f6.ResultadoSeisSombreros;
import pensamiento.tecnicas.f6.ResultadoTuring;
import pensamiento.testutil.builders.Contextos;
import pensamiento.testutil.fakes.FakeRepositorioEsquemas;

/**
 * Oráculo de T35 a T39 con los ejemplos de docs/ejemplos/T35.md a T39.md, leídos del catálogo en modo plantillas (banco):
 * lo que el ejecutor produce es lo escrito a mano, nunca recalculado con la fórmula de producción.
 */
class EjecutoresF6OraculoTest {

    private static final CatalogoJson CATALOGO = new CatalogoJson();

    record ResumenYPendientes(String resumen, List<String> pendientes) {
    }

    static Stream<IdTecnica> tecnicas() {
        return Stream.of(EjecutorSeisSombreros.ID, EjecutorEquipoRojo.ID, EjecutorTuring.ID, EjecutorDoubleCrux.ID, EjecutorEtico.ID);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("tecnicas")
    void cada_tecnica_tiene_tres_ejemplos_uno_por_ambito(IdTecnica id) {
        assertThat(CATALOGO.ejemplosDe(id)).extracting(Ejemplo::ambito)
                .containsExactlyInAnyOrder(Ejemplo.Ambito.PERSONAL, Ejemplo.Ambito.TRABAJO, Ejemplo.Ambito.COMUNIDAD);
    }

    static <C, E, R> Resultado<R> correr(Ejecutor<C, E, R> ejecutor, Ejemplo ejemplo) {
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
    // T35 · Seis Sombreros
    // ---------------------------------------------------------------------------------------------

    record CeldaEsperada(String id, String estado) {
    }

    record EsperadoT35(List<CeldaEsperada> celdas, List<String> notas, List<String> avisos) {
    }

    @Test
    void t35_cada_ejemplo_da_las_celdas_en_su_orden_y_los_avisos_escritos_a_mano() {
        EjecutorSeisSombreros t35 = new EjecutorSeisSombreros();
        for (Ejemplo e : CATALOGO.ejemplosDe(EjecutorSeisSombreros.ID)) {
            Resultado<ResultadoSeisSombreros> r = correr(t35, e);
            EsperadoT35 esperado = MapeadorJson.leer(e.resultado(), EsperadoT35.class);
            assertThat(r.valor().celdas()).extracting(c -> new CeldaEsperada(c.id(), c.estado())).as(e.titulo()).containsExactlyElementsOf(esperado.celdas());
            assertThat(r.valor().notas()).as(e.titulo()).containsExactlyElementsOf(esperado.notas());
            assertThat(r.valor().avisos()).as(e.titulo()).containsExactlyElementsOf(esperado.avisos());
        }
    }

    // ---------------------------------------------------------------------------------------------
    // T36 · Equipo rojo / abogado del diablo
    // ---------------------------------------------------------------------------------------------

    record AtaqueEsperado(String codigo, String razon, String esquema, int pregunta, String texto, String estado) {
    }

    record DebilidadEsperada(String razon, String esquema, String deDonde, List<Integer> preguntas) {
    }

    record EsperadoT36(List<AtaqueEsperado> ataques, List<DebilidadEsperada> debilidades) {
    }

    @Test
    void t36_cada_ejemplo_da_los_ataques_del_banco_y_las_debilidades_escritas_a_mano() {
        EjecutorEquipoRojo t36 = new EjecutorEquipoRojo(new FakeRepositorioEsquemas());
        for (Ejemplo e : CATALOGO.ejemplosDe(EjecutorEquipoRojo.ID)) {
            Resultado<ResultadoEquipoRojo> r = correr(t36, e);
            EsperadoT36 esperado = MapeadorJson.leer(e.resultado(), EsperadoT36.class);
            assertThat(r.valor().ataques()).extracting(a -> new AtaqueEsperado(a.codigo(), a.razon(), a.esquema(), a.pregunta(), a.texto(), a.estado()))
                    .as(e.titulo()).containsExactlyElementsOf(esperado.ataques());
            assertThat(r.valor().debilidades()).extracting(d -> new DebilidadEsperada(d.razon(), d.esquema(), d.deDonde(),
                    d.preguntas().stream().map(ResultadoEquipoRojo.Pregunta::numero).toList())).as(e.titulo())
                    .containsExactlyElementsOf(esperado.debilidades());
            assertThat(r.pendientes()).as("cada objeción apunta a su razón").allSatisfy(p -> {
                assertThat(p.tipo()).isEqualTo(TipoPendiente.OBJECION);
                assertThat(r.afirmaciones()).filteredOn(a -> a.rol() == RolAfirmacion.PREMISA).extracting(AfirmacionConRol::afirmacionId)
                        .contains(p.objetoId().orElseThrow());
            });
        }
    }

    // ---------------------------------------------------------------------------------------------
    // T37 · Test de Turing ideológico
    // ---------------------------------------------------------------------------------------------

    record ArgumentoEsperado(boolean cubierto, String clave) {
    }

    record EsperadoT37(int caricatura, Integer omision, int tono, int puntaje, boolean aprueba, List<ArgumentoEsperado> argumentos) {
    }

    @Test
    void t37_cada_ejemplo_da_el_puntaje_de_la_rubrica_calculado_a_mano() {
        EjecutorTuring t37 = new EjecutorTuring();
        for (Ejemplo e : CATALOGO.ejemplosDe(EjecutorTuring.ID)) {
            Resultado<ResultadoTuring> r = correr(t37, e);
            EsperadoT37 esperado = MapeadorJson.leer(e.resultado(), EsperadoT37.class);
            ResultadoTuring v = r.valor();
            assertThat(List.of(v.caricatura(), v.tono(), v.puntaje())).as(e.titulo()).containsExactly(esperado.caricatura(), esperado.tono(), esperado.puntaje());
            assertThat(v.omision()).as(e.titulo()).isEqualTo(esperado.omision());
            assertThat(v.aprueba()).as(e.titulo()).isEqualTo(esperado.aprueba());
            assertThat(v.argumentos()).extracting(a -> new ArgumentoEsperado(a.cubierto(), a.clave())).as(e.titulo())
                    .containsExactlyElementsOf(esperado.argumentos());
            assertThat(v.motivo()).as("nunca dice que esté bien").contains("no la postura real de nadie");
        }
    }

    // ---------------------------------------------------------------------------------------------
    // T38 · Double crux
    // ---------------------------------------------------------------------------------------------

    record EsperadoT38(String estado, List<String> sugerencias, List<String> avisos) {
    }

    @Test
    void t38_cada_ejemplo_da_el_estado_las_sugerencias_y_los_avisos_escritos_a_mano() {
        EjecutorDoubleCrux t38 = new EjecutorDoubleCrux();
        for (Ejemplo e : CATALOGO.ejemplosDe(EjecutorDoubleCrux.ID)) {
            Resultado<ResultadoDoubleCrux> r = correr(t38, e);
            EsperadoT38 esperado = MapeadorJson.leer(e.resultado(), EsperadoT38.class);
            assertThat(r.valor().estado()).as(e.titulo()).isEqualTo(esperado.estado());
            assertThat(r.valor().sugerencias()).as(e.titulo()).containsExactlyElementsOf(esperado.sugerencias());
            assertThat(r.valor().avisos()).as(e.titulo()).containsExactlyElementsOf(esperado.avisos());
        }
    }

    // ---------------------------------------------------------------------------------------------
    // T39 · Razonamiento ético
    // ---------------------------------------------------------------------------------------------

    record EsperadoT39(Map<String, Integer> balances, List<String> conflictos, List<String> avisos) {
    }

    @Test
    void t39_cada_ejemplo_da_los_balances_y_los_conflictos_escritos_a_mano() {
        EjecutorEtico t39 = new EjecutorEtico();
        for (Ejemplo e : CATALOGO.ejemplosDe(EjecutorEtico.ID)) {
            Resultado<ResultadoEtico> r = correr(t39, e);
            EsperadoT39 esperado = MapeadorJson.leer(e.resultado(), EsperadoT39.class);
            assertThat(r.valor().marcos()).extracting(ResultadoEtico.FilaMarco::id, ResultadoEtico.FilaMarco::balance).as(e.titulo())
                    .containsExactlyElementsOf(esperado.balances().entrySet().stream().map(x -> org.assertj.core.groups.Tuple.tuple(x.getKey(), x.getValue()))
                            .toList());
            assertThat(r.valor().conflictos()).as(e.titulo()).containsExactlyElementsOf(esperado.conflictos());
            assertThat(r.valor().avisos()).as(e.titulo()).containsExactlyElementsOf(esperado.avisos());
        }
    }
}
