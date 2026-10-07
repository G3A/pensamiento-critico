package pensamiento.integracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;

import pensamiento.testutil.BaseDatosDePrueba;

/**
 * IT de RLS (RF-03, segunda cerradura): el rol de aplicación, con app.usuario fijado al perfil B,
 * no ve ni puede escribir filas del perfil A, aunque el SQL no filtre por usuario.
 */
class RlsIT {

    private static final BaseDatosDePrueba bd = new BaseDatosDePrueba();
    private static UUID institucion;
    private static UUID perfilA;
    private static UUID perfilB;
    private static UUID expedienteDeA;

    @BeforeAll
    static void sembrar() {
        institucion = bd.crearInstitucion("rls-" + UUID.randomUUID());
        perfilA = bd.crearUsuario(institucion, "perfil A");
        perfilB = bd.crearUsuario(institucion, "perfil B");
        expedienteDeA = bd.jdbcAdmin().sql("INSERT INTO expediente (usuario_id, institucion_id, nombre) VALUES (:u, :i, 'privado de A') RETURNING id")
                .param("u", perfilA).param("i", institucion).query(UUID.class).single();
    }

    @AfterAll
    static void limpiar() {
        bd.borrarInstitucion(institucion);
    }

    @Test
    void el_perfil_b_no_ve_el_expediente_de_a_aunque_lo_pida_por_id_sin_filtro_de_usuario() {
        JdbcClient jdbc = bd.jdbcApp();
        Long vistasPorB = bd.comoUsuario(perfilB, institucion,
                () -> jdbc.sql("SELECT count(*) FROM expediente WHERE id = :id").param("id", expedienteDeA).query(Long.class).single());
        Long vistasPorA = bd.comoUsuario(perfilA, institucion,
                () -> jdbc.sql("SELECT count(*) FROM expediente WHERE id = :id").param("id", expedienteDeA).query(Long.class).single());
        assertThat(vistasPorB).isZero();
        assertThat(vistasPorA).isEqualTo(1L);
    }

    @Test
    void sin_contexto_de_sesion_el_rol_de_aplicacion_no_ve_ninguna_fila_de_usuario() {
        JdbcClient jdbc = bd.jdbcApp();
        Long sinContexto = jdbc.sql("SELECT count(*) FROM expediente").query(Long.class).single();
        assertThat(sinContexto).isZero();
    }

    @Test
    void el_perfil_b_no_puede_insertar_filas_a_nombre_de_a() {
        JdbcClient jdbc = bd.jdbcApp();
        assertThatThrownBy(() -> bd.comoUsuario(perfilB, institucion,
                () -> jdbc.sql("INSERT INTO expediente (usuario_id, institucion_id, nombre) VALUES (:u, :i, 'suplantado')")
                        .param("u", perfilA).param("i", institucion).update()))
                .isInstanceOf(DataAccessException.class)
                .rootCause().hasMessageContaining("row-level security");
    }

    @Test
    void el_rol_de_aplicacion_no_puede_borrar_ni_modificar_la_auditoria() {
        JdbcClient jdbc = bd.jdbcApp();
        assertThatThrownBy(() -> bd.comoUsuario(perfilA, institucion, () -> jdbc.sql("DELETE FROM auditoria").update()))
                .isInstanceOf(DataAccessException.class)
                .rootCause().hasMessageContaining("permission denied");
    }
}
