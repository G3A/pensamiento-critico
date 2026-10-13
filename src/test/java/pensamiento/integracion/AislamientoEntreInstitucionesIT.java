package pensamiento.integracion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import pensamiento.testutil.BaseDatosDePrueba;
import pensamiento.testutil.SemillaDeInstitucion;

/**
 * Hito 8, entregable 6 (condición de salida): dos instituciones conviven en la misma base y ninguna ve a la otra.
 * La institución A recibe una fila en cada tabla con RLS, con un texto centinela donde la tabla tiene texto libre; consultando
 * como una persona de B (y sin contexto, como el operador) ninguna fila de ninguna tabla trae el centinela ni un id de A.
 * La lista de tablas sale de pg_class: una tabla nueva con RLS sin su fila sembrada hace fallar la prueba.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AislamientoEntreInstitucionesIT {

    private final BaseDatosDePrueba bd = new BaseDatosDePrueba();
    private SemillaDeInstitucion a;
    private UUID institucionB;
    private UUID usuarioB;

    @BeforeAll
    void sembrar() {
        a = new SemillaDeInstitucion(bd);
        institucionB = bd.crearInstitucion("B sin centinela");
        usuarioB = bd.crearUsuario(institucionB, "persona de B");
    }

    @AfterAll
    void limpiar() {
        a.borrar();
        bd.borrarInstitucion(institucionB);
    }

    @Test
    void cada_tabla_con_rls_de_una_institucion_tiene_su_fila_centinela_sembrada() {
        assertThat(a.sembradas().keySet()).containsExactlyInAnyOrderElementsOf(tablasConRlsDeInstitucion());
    }

    @Test
    void consultando_como_una_persona_de_b_ninguna_tabla_devuelve_filas_de_a() {
        Map<String, Integer> vistas = bd.comoUsuario(usuarioB, institucionB, () -> a.contarMarcas(bd.jdbcApp()));
        assertThat(vistas).allSatisfy((tabla, filas) -> assertThat(filas).as("filas de A en %s vistas desde B", tabla).isZero());
    }

    @Test
    void sin_usuario_ni_institucion_fijados_como_el_operador_no_se_ve_ninguna_fila_de_a() {
        Map<String, Integer> vistas = bd.sinContexto(() -> a.contarMarcas(bd.jdbcApp()));
        assertThat(vistas).allSatisfy((tabla, filas) -> assertThat(filas).as("filas de A en %s vistas sin contexto", tabla).isZero());
    }

    @Test
    void la_persona_de_a_si_ve_sus_filas_asi_que_la_prueba_no_pasa_en_vacio() {
        Map<String, Integer> vistas = bd.comoUsuario(a.usuario(), a.institucion(), () -> a.contarMarcas(bd.jdbcApp()));
        assertThat(vistas).isEqualTo(a.sembradas());
    }

    @Test
    void el_rol_de_la_app_no_puede_leer_las_tablas_del_operador() {
        for (String tabla : java.util.List.of("operador", "invitacion", "auditoria_operador")) {
            Boolean puede = bd.jdbcApp().sql("SELECT has_table_privilege('app', :t, 'SELECT')").param("t", tabla).query(Boolean.class).single();
            assertThat(puede).as("SELECT de app sobre %s", tabla).isFalse();
        }
        Boolean bypass = bd.jdbcAdmin().sql("SELECT rolbypassrls FROM pg_roles WHERE rolname = 'app'").query(Boolean.class).single();
        assertThat(bypass).as("el rol de la app no tiene BYPASSRLS").isFalse();
    }

    /** Las tablas con RLS forzada, menos las del operador (sin políticas: nadie las lee por RLS, solo por sus funciones). */
    private Set<String> tablasConRlsDeInstitucion() {
        Set<String> tablas = new TreeSet<>(bd.jdbcAdmin().sql("""
                SELECT c.relname FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = 'public' AND c.relkind = 'r' AND c.relrowsecurity AND c.relforcerowsecurity""")
                .query(String.class).list());
        tablas.removeAll(Set.of("operador", "invitacion", "auditoria_operador"));
        return tablas;
    }
}
