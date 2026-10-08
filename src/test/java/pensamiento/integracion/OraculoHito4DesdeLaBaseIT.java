package pensamiento.integracion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

import pensamiento.catalogo.MapeadorJson;
import pensamiento.catalogo.RepositorioTecnicaJdbc;
import pensamiento.nucleo.Ejecutor;
import pensamiento.nucleo.Ejemplo;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Pendiente;
import pensamiento.nucleo.Resultado;
import pensamiento.nucleo.Tecnica;
import pensamiento.tecnicas.f7.EjecutorArbolMece;
import pensamiento.tecnicas.f7.EjecutorDefinicionProblema;
import pensamiento.tecnicas.f7.EjecutorIshikawa;
import pensamiento.tecnicas.f7.EjecutorPrimerosPrincipios;
import pensamiento.tecnicas.f7.EjecutorScamper;
import pensamiento.testutil.BaseDatosDePrueba;
import pensamiento.testutil.builders.Contextos;
import pensamiento.web.seguridad.GestorTransaccionesRls;

/**
 * El oráculo de las técnicas del hito 4 contra lo que la semilla repeatable dejó en la tabla ejemplo del PostgreSQL del
 * compose: cada ejemplo leído de la base produce el resumen y los pendientes escritos a mano. También revisa que las
 * catorce estén activas en la base.
 */
class OraculoHito4DesdeLaBaseIT {

    record Esperado(String resumen, List<String> pendientes) {
    }

    static final List<Ejecutor<?, ?, ?>> EJECUTORES = List.of(new EjecutorDefinicionProblema(), new EjecutorPrimerosPrincipios(),
            new EjecutorArbolMece(), new EjecutorIshikawa(), new EjecutorScamper(), new pensamiento.tecnicas.f5.EjecutorBayes(),
            new pensamiento.tecnicas.f5.EjecutorCalibracion(), new pensamiento.tecnicas.f5.EjecutorFermi(), new pensamiento.tecnicas.f5.EjecutorValorEsperado(),
            new pensamiento.tecnicas.f5.EjecutorPremortem(), new pensamiento.tecnicas.f5.EjecutorInversion(), new pensamiento.tecnicas.f5.EjecutorMatrizPonderada(),
            new pensamiento.tecnicas.f5.EjecutorDiarioDecisiones(), new pensamiento.tecnicas.f5.EjecutorMejorExplicacion());

    static final List<String> DEL_HITO_4 = List.of("T24", "T25", "T26", "T27", "T29", "T30", "T31", "T32", "T33", "T40", "T41", "T42", "T43", "T44");

    private final BaseDatosDePrueba bd = new BaseDatosDePrueba();
    private final TransactionTemplate tx = new TransactionTemplate(new GestorTransaccionesRls(bd.dataSourceApp()));

    private List<Ejemplo> ejemplos(IdTecnica tecnica) {
        RepositorioTecnicaJdbc repo = new RepositorioTecnicaJdbc(bd.jdbcApp());
        return tx.execute(t -> repo.ejemplos(tecnica));
    }

    private <C, E, R> void oraculo(Ejecutor<C, E, R> ejecutor) {
        List<Ejemplo> ejemplos = ejemplos(ejecutor.id());
        assertThat(ejemplos).as(ejecutor.id() + " tiene tres ejemplos en la base").hasSize(3);
        for (Ejemplo e : ejemplos) {
            Resultado<R> r = ejecutor.ejecutar(MapeadorJson.leer(e.config(), ejecutor.tipos().config()),
                    MapeadorJson.leer(e.datos(), ejecutor.tipos().entrada()), Contextos.sinIa());
            Esperado esperado = MapeadorJson.leer(e.resultado(), Esperado.class);
            assertThat(r.resumen()).as(ejecutor.id() + " · " + e.titulo()).isEqualTo(esperado.resumen());
            assertThat(r.pendientes()).extracting(Pendiente::descripcion).as(ejecutor.id() + " · " + e.titulo())
                    .containsExactlyElementsOf(esperado.pendientes());
        }
    }

    @Test
    void las_tecnicas_del_hito_4_dan_su_oraculo_desde_la_tabla_ejemplo() {
        EJECUTORES.forEach(this::oraculo);
    }

    @Test
    void las_del_hito_4_estan_activas_en_la_base() {
        RepositorioTecnicaJdbc repo = new RepositorioTecnicaJdbc(bd.jdbcApp());
        List<Tecnica> todas = tx.execute(t -> repo.todas());
        Function<String, Tecnica> por = id -> todas.stream().filter(t -> t.id().valor().equals(id)).findFirst().orElseThrow();
        for (String id : DEL_HITO_4) {
            assertThat(por.apply(id).estaPendiente()).as(id).isFalse();
        }
    }
}
