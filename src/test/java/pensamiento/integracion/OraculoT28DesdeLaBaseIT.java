package pensamiento.integracion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

import pensamiento.catalogo.MapeadorJson;
import pensamiento.catalogo.RepositorioTecnicaJdbc;
import pensamiento.nucleo.Ejemplo;
import pensamiento.tecnicas.f5.ConfigAch;
import pensamiento.tecnicas.f5.EjecutorAch;
import pensamiento.tecnicas.f5.EntradaAch;
import pensamiento.tecnicas.f5.ResultadoAch;
import pensamiento.testutil.BaseDatosDePrueba;
import pensamiento.testutil.builders.Contextos;
import pensamiento.web.seguridad.GestorTransaccionesRls;

/**
 * El oráculo de T28 · Análisis de hipótesis en competencia (ACH) contra lo que la semilla repeatable dejó en la
 * tabla ejemplo del PostgreSQL del compose: la transcripción a la base no perdió ni cambió nada.
 */
class OraculoT28DesdeLaBaseIT {

    record Esperado(Map<String, Integer> inconsistencias, List<String> menosRefutadas, String resumen) {
    }

    private final BaseDatosDePrueba bd = new BaseDatosDePrueba();

    @Test
    void los_ejemplos_de_la_tabla_ejemplo_producen_su_resultado_escrito_a_mano() {
        RepositorioTecnicaJdbc repo = new RepositorioTecnicaJdbc(bd.jdbcApp());
        List<Ejemplo> ejemplos = new TransactionTemplate(new GestorTransaccionesRls(bd.dataSourceApp()))
                .execute(t -> repo.ejemplos(EjecutorAch.ID)).stream().filter(e -> e.orden() < 90).toList();

        assertThat(ejemplos).extracting(Ejemplo::titulo)
                .containsExactly("Las ventas de los sábados", "La asamblea vacía", "La factura de luz", "El pan quemado");
        EjecutorAch ach = new EjecutorAch();
        for (Ejemplo e : ejemplos) {
            Esperado esperado = MapeadorJson.leer(e.resultado(), Esperado.class);
            ResultadoAch valor = ach.ejecutar(MapeadorJson.leer(e.config(), ConfigAch.class), MapeadorJson.leer(e.datos(), EntradaAch.class),
                    Contextos.sinIa()).valor();
            Map<String, Integer> inconsistencias = new LinkedHashMap<>();
            valor.hipotesis().forEach(h -> inconsistencias.put(h.codigo(), h.inconsistencias()));
            assertThat(inconsistencias).as(e.titulo()).isEqualTo(esperado.inconsistencias());
            assertThat(valor.menosRefutadas()).as(e.titulo()).isEqualTo(esperado.menosRefutadas());
            assertThat(valor.resumen()).as(e.titulo()).isEqualTo(esperado.resumen());
        }
    }
}
