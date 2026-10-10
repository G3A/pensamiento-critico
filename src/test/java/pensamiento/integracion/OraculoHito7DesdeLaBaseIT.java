package pensamiento.integracion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

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
 * El oráculo de T45 a T49 contra lo que la semilla repeatable dejó en la tabla ejemplo del PostgreSQL del compose: cada
 * ejemplo leído de la base produce el resumen y los pendientes escritos a mano. También revisa la definición de hecho del
 * hito 7 en la base: las 49 técnicas activas y la cobertura de ejemplos completa (tres por técnica, uno por ámbito).
 */
class OraculoHito7DesdeLaBaseIT {

    record Esperado(String resumen, List<String> pendientes) {
    }

    static final List<Ejecutor<?, ?, ?>> EJECUTORES = List.of(new pensamiento.tecnicas.f8.EjecutorDiarioRazonamiento(),
            new pensamiento.tecnicas.f8.EjecutorCambiosOpinion(), new pensamiento.tecnicas.f8.EjecutorReflexion(), new pensamiento.tecnicas.f8.EjecutorBloom(),
            new pensamiento.tecnicas.f8.EjecutorRepeticion());

    private final BaseDatosDePrueba bd = new BaseDatosDePrueba();
    private final TransactionTemplate tx = new TransactionTemplate(new GestorTransaccionesRls(bd.dataSourceApp()));
    private final RepositorioTecnicaJdbc repo = new RepositorioTecnicaJdbc(bd.jdbcApp());

    private List<Ejemplo> ejemplos(IdTecnica tecnica) {
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
    void las_tecnicas_del_hito_7_dan_su_oraculo_desde_la_tabla_ejemplo() {
        EJECUTORES.forEach(this::oraculo);
    }

    @Test
    void las_49_tecnicas_estan_activas_y_cada_una_tiene_tres_ejemplos_uno_por_ambito() {
        List<Tecnica> todas = tx.execute(t -> repo.todas());
        assertThat(todas).hasSize(49).noneMatch(Tecnica::estaPendiente);
        int total = 0;
        for (Tecnica t : todas) {
            List<Ejemplo> suyos = ejemplos(t.id());
            assertThat(suyos.stream().map(Ejemplo::ambito).distinct()).as(t.cita()).containsExactlyInAnyOrder(Ejemplo.Ambito.values());
            assertThat(suyos.size()).as(t.cita()).isGreaterThanOrEqualTo(3);
            total += suyos.size();
        }
        // 147 = 49 × 3, más el caso adicional de T28 (la tarjeta de la sección 7b).
        assertThat(total).isEqualTo(148);
    }
}
