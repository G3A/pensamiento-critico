package pensamiento.integracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import pensamiento.catalogo.CatalogoJson;
import pensamiento.catalogo.MapeadorJson;
import pensamiento.expediente.RepositorioEjecucionJdbc;
import pensamiento.nucleo.Contexto;
import pensamiento.nucleo.Ejecucion;
import pensamiento.nucleo.Ejemplo;
import pensamiento.nucleo.Pendiente;
import pensamiento.nucleo.Resultado;
import pensamiento.nucleo.TipoPendiente;
import pensamiento.nucleo.Uuid7;
import pensamiento.tecnicas.f5.ConfigAch;
import pensamiento.tecnicas.f5.EjecutorAch;
import pensamiento.tecnicas.f5.EntradaAch;
import pensamiento.tecnicas.f5.ResultadoAch;
import pensamiento.testutil.BaseDatosDePrueba;
import pensamiento.testutil.fakes.FakeReloj;

/**
 * RF-07 contra el PostgreSQL del compose: la ejecución de T28, sus afirmaciones con rol y sentido y la
 * proyección de pendientes se escriben en la misma transacción; si una parte falla, no queda ninguna.
 */
class ProyeccionDePendientesIT {

    private static final BaseDatosDePrueba bd = new BaseDatosDePrueba();
    private static UUID institucion;
    private static UUID duena;

    private final RepositorioEjecucionJdbc repo = new RepositorioEjecucionJdbc(bd.jdbcApp());

    @BeforeAll
    static void sembrar() {
        institucion = bd.crearInstitucion("proyeccion-" + UUID.randomUUID());
        duena = bd.crearUsuario(institucion, "dueña de la panadería");
    }

    @AfterAll
    static void limpiar() {
        bd.borrarInstitucion(institucion);
    }

    private Resultado<ResultadoAch> ejecutarLasVentasDeLosSabados() {
        Ejemplo ventas = new CatalogoJson().ejemplosDe(EjecutorAch.ID).getFirst();
        Contexto ctx = new Contexto(duena, institucion, Optional.empty(), new FakeReloj(), Optional.empty(), () -> Uuid7.en(Instant.now()));
        return new EjecutorAch().ejecutar(MapeadorJson.leer(ventas.config(), ConfigAch.class), MapeadorJson.leer(ventas.datos(), EntradaAch.class), ctx);
    }

    private Ejecucion ejecucion(Resultado<ResultadoAch> r, String clave) {
        return new Ejecucion(Uuid7.en(Instant.now()), duena, institucion, EjecutorAch.ID, 1, Optional.empty(),
                MapeadorJson.escribir(ConfigAch.POR_DEFECTO), MapeadorJson.escribir(r.valor()), MapeadorJson.escribir(r.valor()),
                r.resumen(), Optional.empty(), clave, Instant.now());
    }

    @Test
    void la_ejecucion_sus_hipotesis_y_su_pendiente_quedan_juntos_en_la_base() {
        Resultado<ResultadoAch> r = ejecutarLasVentasDeLosSabados();
        Ejecucion e = ejecucion(r, "rf07-" + UUID.randomUUID());

        bd.comoUsuario(duena, institucion, () -> repo.guardar(e, r.afirmaciones(), r.pendientes()));

        var admin = bd.jdbcAdmin();
        assertThat(admin.sql("SELECT count(*) FROM ejecucion WHERE id = :e").param("e", e.id()).query(Integer.class).single()).isEqualTo(1);
        assertThat(admin.sql("SELECT rol || '/' || sentido FROM ejecucion_afirmacion WHERE ejecucion_id = :e").param("e", e.id()).query(String.class).list())
                .containsExactly("hipotesis/producida", "hipotesis/producida", "hipotesis/producida");
        assertThat(admin.sql("SELECT a.origen || '/' || a.adoptada FROM afirmacion a JOIN ejecucion_afirmacion ea ON ea.afirmacion_id = a.id WHERE ea.ejecucion_id = :e")
                .param("e", e.id()).query(String.class).list()).containsOnly("usuario/true");
        assertThat(admin.sql("SELECT tipo || ': ' || descripcion FROM pendiente WHERE ejecucion_id = :e").param("e", e.id()).query(String.class).list())
                .containsExactly("verificacion: Verificar E1: La baja es solo los sábados (hipótesis H1: Abrió una feria a dos cuadras los sábados)");
        assertThat(admin.sql("SELECT objeto_id FROM pendiente WHERE ejecucion_id = :e").param("e", e.id()).query(UUID.class).single())
                .isEqualTo(r.valor().hipotesis("H1").afirmacionId());
    }

    @Test
    void si_el_pendiente_no_se_puede_escribir_la_ejecucion_y_sus_afirmaciones_tampoco_quedan() {
        Resultado<ResultadoAch> r = ejecutarLasVentasDeLosSabados();
        Ejecucion e = ejecucion(r, "rf07-falla-" + UUID.randomUUID());
        Pendiente invalido = new Pendiente(TipoPendiente.VERIFICACION, Optional.empty(), Optional.empty(), null);   // descripcion NOT NULL

        assertThatThrownBy(() -> bd.comoUsuario(duena, institucion, () -> repo.guardar(e, r.afirmaciones(), List.of(invalido))))
                .isInstanceOf(RuntimeException.class);

        var admin = bd.jdbcAdmin();
        assertThat(admin.sql("SELECT count(*) FROM ejecucion WHERE id = :e").param("e", e.id()).query(Integer.class).single()).isZero();
        assertThat(admin.sql("SELECT count(*) FROM afirmacion WHERE id = :a").param("a", r.afirmaciones().getFirst().afirmacionId())
                .query(Integer.class).single()).isZero();
    }

    @Test
    void el_doble_clic_deja_una_sola_ejecucion_con_un_solo_juego_de_pendientes() {
        Resultado<ResultadoAch> r = ejecutarLasVentasDeLosSabados();
        String clave = "rf07-doble-" + UUID.randomUUID();
        Ejecucion primera = ejecucion(r, clave);

        bd.comoUsuario(duena, institucion, () -> repo.guardar(primera, r.afirmaciones(), r.pendientes()));
        Ejecucion devuelta = bd.comoUsuario(duena, institucion, () -> repo.guardar(ejecucion(r, clave), r.afirmaciones(), r.pendientes()));

        assertThat(devuelta.id()).isEqualTo(primera.id());
        assertThat(bd.jdbcAdmin().sql("SELECT count(*) FROM pendiente p JOIN ejecucion e ON e.id = p.ejecucion_id WHERE e.clave_idempotencia = :c")
                .param("c", clave).query(Integer.class).single()).isEqualTo(1);
    }
}
