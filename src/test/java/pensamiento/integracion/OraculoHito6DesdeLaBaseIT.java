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
import pensamiento.testutil.BaseDatosDePrueba;
import pensamiento.testutil.builders.Contextos;
import pensamiento.web.seguridad.GestorTransaccionesRls;

/**
 * El oráculo de las técnicas del hito 6 contra lo que la semilla repeatable dejó en la tabla ejemplo del PostgreSQL del
 * compose: cada ejemplo leído de la base produce el resumen y los pendientes escritos a mano. También revisa que las
 * cuatro estén activas en la base.
 */
class OraculoHito6DesdeLaBaseIT {

    record Esperado(String resumen, List<String> pendientes) {
    }

    static final List<Ejecutor<?, ?, ?>> EJECUTORES = List.of(new pensamiento.tecnicas.f4.EjecutorSift(), new pensamiento.tecnicas.f4.EjecutorLecturaLateral(),
            new pensamiento.tecnicas.f4.EjecutorCraap(), new pensamiento.tecnicas.f4.EjecutorJerarquia());

    static final List<String> DEL_HITO_6 = List.of("T19", "T20", "T21", "T23");

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
    void las_tecnicas_del_hito_6_dan_su_oraculo_desde_la_tabla_ejemplo() {
        EJECUTORES.forEach(this::oraculo);
    }

    @Test
    void las_del_hito_6_estan_activas_en_la_base() {
        RepositorioTecnicaJdbc repo = new RepositorioTecnicaJdbc(bd.jdbcApp());
        List<Tecnica> todas = tx.execute(t -> repo.todas());
        Function<String, Tecnica> por = id -> todas.stream().filter(t -> t.id().valor().equals(id)).findFirst().orElseThrow();
        for (String id : DEL_HITO_6) {
            assertThat(por.apply(id).estaPendiente()).as(id).isFalse();
        }
    }
}
