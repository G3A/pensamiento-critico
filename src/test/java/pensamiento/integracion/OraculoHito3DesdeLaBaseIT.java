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
import pensamiento.tecnicas.f1.EjecutorAnalogia;
import pensamiento.tecnicas.f1.EjecutorCer;
import pensamiento.tecnicas.f1.EjecutorPaulElder;
import pensamiento.tecnicas.f1.EjecutorValidez;
import pensamiento.tecnicas.f3.EjecutorHechoInferencia;
import pensamiento.tecnicas.f3.EjecutorListaDecision;
import pensamiento.tecnicas.f3.EjecutorOpuesto;
import pensamiento.tecnicas.f3.EjecutorSesgos;
import pensamiento.tecnicas.f3.EjecutorTasasBase;
import pensamiento.tecnicas.f4.EjecutorTriangulacion;
import pensamiento.tecnicas.f6.EjecutorSteelman;
import pensamiento.testutil.BaseDatosDePrueba;
import pensamiento.testutil.builders.Contextos;
import pensamiento.web.seguridad.GestorTransaccionesRls;

/**
 * El oráculo de las técnicas nuevas del hito 3 contra lo que la semilla repeatable dejó en la tabla ejemplo del
 * PostgreSQL del compose: cada ejemplo leído de la base, en modo plantillas, produce el resumen y los pendientes
 * escritos a mano. T13 se cubre en OraculoHito2DesdeLaBaseIT. También revisa que las doce estén activas en la base.
 */
class OraculoHito3DesdeLaBaseIT {

    record Esperado(String resumen, List<String> pendientes) {
    }

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
            assertThat(r.modelo()).as("en modo plantillas no hay registro del modelo").isEmpty();
        }
    }

    @Test
    void las_once_tecnicas_nuevas_dan_su_oraculo_desde_la_tabla_ejemplo() {
        List<Ejecutor<?, ?, ?>> ejecutores = List.of(new EjecutorCer(), new EjecutorPaulElder(), new EjecutorValidez(), new EjecutorAnalogia(),
                new EjecutorSesgos(), new EjecutorOpuesto(), new EjecutorListaDecision(), new EjecutorHechoInferencia(), new EjecutorTasasBase(),
                new EjecutorTriangulacion(), new EjecutorSteelman());
        ejecutores.forEach(this::oraculo);
    }

    @Test
    void las_doce_del_hito_3_estan_activas_en_la_base() {
        RepositorioTecnicaJdbc repo = new RepositorioTecnicaJdbc(bd.jdbcApp());
        List<Tecnica> todas = tx.execute(t -> repo.todas());
        Function<String, Tecnica> por = id -> todas.stream().filter(t -> t.id().valor().equals(id)).findFirst().orElseThrow();
        for (String id : List.of("T03", "T04", "T05", "T07", "T13", "T14", "T15", "T16", "T17", "T18", "T22", "T34")) {
            assertThat(por.apply(id).estaPendiente()).as(id).isFalse();
        }
    }
}
