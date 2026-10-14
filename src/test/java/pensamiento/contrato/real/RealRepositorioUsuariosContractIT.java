package pensamiento.contrato.real;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import pensamiento.contrato.RepositorioUsuariosContract;
import pensamiento.nucleo.Usuario;
import pensamiento.nucleo.puertos.RepositorioUsuarios;
import pensamiento.testutil.BaseDatosDePrueba;
import pensamiento.web.usuarios.RepositorioUsuariosJdbc;

/**
 * Contra PostgreSQL como rol de aplicación bajo RLS. Lo escrito va a una institución de prueba que se borra. Cada llamada
 * abre su transacción con la institución fijada, salvo dentro de enUnaTransaccion, donde todas comparten una.
 */
@EnabledIfEnvironmentVariable(named = "CONTRACT_REAL", matches = "true")
class RealRepositorioUsuariosContractIT extends RepositorioUsuariosContract {

    private final BaseDatosDePrueba bd = new BaseDatosDePrueba();
    private UUID institucion;

    @BeforeEach
    void sembrar() {
        institucion = bd.crearInstitucion("contrato-usuarios-" + UUID.randomUUID());
    }

    @AfterEach
    void limpiar() {
        bd.jdbcAdmin().sql("DELETE FROM codigo_cuenta WHERE institucion_id = :i").param("i", institucion).update();
        bd.borrarInstitucion(institucion);
    }

    @Override
    protected UUID institucionDePrueba() {
        return institucion;
    }

    @Override
    protected <T> T enUnaTransaccion(Supplier<T> accion) {
        return bd.comoInstitucion(institucion, accion);
    }

    @Override
    protected RepositorioUsuarios crearSut() {
        RepositorioUsuariosJdbc real = new RepositorioUsuariosJdbc(bd.jdbcApp());
        return new RepositorioUsuarios() {
            @Override public Optional<Usuario> crear(Usuario u) { return tx(() -> real.crear(u)); }
            @Override public Optional<Usuario> porId(UUID i, UUID id) { return tx(() -> real.porId(i, id)); }
            @Override public Optional<Usuario> porNombre(UUID i, String n) { return tx(() -> real.porNombre(i, n)); }
            @Override public PaginaDeCuentas buscar(UUID i, String t, int p, int n) { return tx(() -> real.buscar(i, t, p, n)); }
            @Override public long contar(UUID i) { return tx(() -> real.contar(i)); }
            @Override public java.util.List<Usuario> porRol(UUID i, Usuario.RolGlobal r) { return tx(() -> real.porRol(i, r)); }
            @Override public boolean cambiarClave(UUID i, UUID id, String h, Usuario.TipoClave t) { return tx(() -> real.cambiarClave(i, id, h, t)); }
            @Override public boolean cambiarActivo(UUID i, UUID id, boolean a) { return tx(() -> real.cambiarActivo(i, id, a)); }
            @Override public void guardarCodigo(UUID i, UUID u, TipoCodigo t, String h, Instant c, Optional<UUID> p) {
                tx(() -> { real.guardarCodigo(i, u, t, h, c, p); return null; });
            }
            @Override public Optional<CodigoCanjeado> codigoVigente(UUID i, String h, Instant a) { return tx(() -> real.codigoVigente(i, h, a)); }
            @Override public Optional<CodigoCanjeado> canjearCodigo(UUID i, String h, Instant a) { return tx(() -> real.canjearCodigo(i, h, a)); }
            @Override public Optional<TipoCodigo> codigoPendiente(UUID i, UUID u, Instant a) { return tx(() -> real.codigoPendiente(i, u, a)); }
        };
    }

    /** Si ya hay una transacción abierta (enUnaTransaccion), la usa; si no, abre una con la institución de prueba. */
    private <T> T tx(Supplier<T> accion) {
        return org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()
                ? accion.get() : bd.comoInstitucion(institucion, accion);
    }
}
