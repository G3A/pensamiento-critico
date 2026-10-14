package pensamiento.contrato.real;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import pensamiento.contrato.RepositorioLlavesContract;
import pensamiento.nucleo.LlaveDeAcceso;
import pensamiento.nucleo.Titular;
import pensamiento.nucleo.puertos.RepositorioLlaves;
import pensamiento.testutil.BaseDatosDePrueba;
import pensamiento.web.seguridad.RepositorioLlavesJdbc;

/**
 * Contra PostgreSQL como rol de aplicación: una cuenta con su institución fijada en la transacción (como en su sesión) y el
 * operador sin contexto. Siembra una institución con dos cuentas y un operador de prueba, y los borra al final.
 */
@EnabledIfEnvironmentVariable(named = "CONTRACT_REAL", matches = "true")
class RealRepositorioLlavesContractIT extends RepositorioLlavesContract {

    private final BaseDatosDePrueba bd = new BaseDatosDePrueba();
    private UUID institucion;
    private Titular a;
    private Titular b;
    private Titular operador;

    @BeforeEach
    void sembrar() {
        institucion = bd.crearInstitucion("contrato-llaves-" + UUID.randomUUID());
        a = Titular.cuenta(bd.crearUsuario(institucion, "rectora"), institucion);
        b = Titular.cuenta(bd.crearUsuario(institucion, "coordinador"), institucion);
        operador = Titular.operador(bd.jdbcAdmin().sql("INSERT INTO operador (nombre, clave_hash) VALUES (:n, 'x') RETURNING id")
                .param("n", "operador-contrato-" + UUID.randomUUID()).query(UUID.class).single());
    }

    @AfterEach
    void limpiar() {
        bd.jdbcAdmin().sql("DELETE FROM operador WHERE id = :o").param("o", operador.id()).update();
        bd.borrarInstitucion(institucion);
    }

    @Override
    protected Titular cuentaA() {
        return a;
    }

    @Override
    protected Titular cuentaB() {
        return b;
    }

    @Override
    protected Titular operador() {
        return operador;
    }

    @Override
    protected RepositorioLlaves crearSut() {
        RepositorioLlavesJdbc real = new RepositorioLlavesJdbc(bd.jdbcApp());
        return new RepositorioLlaves() {
            @Override public boolean guardar(Titular t, LlaveDeAcceso l) { return como(t, () -> real.guardar(t, l)); }
            @Override public Optional<LlaveDeAcceso> porCredencial(Titular t, byte[] c) { return como(t, () -> real.porCredencial(t, c)); }
            @Override public List<LlaveDeAcceso> de(Titular t) { return como(t, () -> real.de(t)); }
            @Override public boolean borrar(Titular t, byte[] c) { return como(t, () -> real.borrar(t, c)); }
            @Override public boolean reemplazarRecuperacion(Titular t, List<String> h) { return como(t, () -> real.reemplazarRecuperacion(t, h)); }
            @Override public List<CodigoDeRecuperacion> recuperacionVigente(Titular t) { return como(t, () -> real.recuperacionVigente(t)); }
            @Override public boolean usarRecuperacion(Titular t, UUID id, Instant a) { return como(t, () -> real.usarRecuperacion(t, id, a)); }
        };
    }

    /** Una cuenta, con su institución fijada (como en su sesión); el operador, sin contexto. */
    private <T> T como(Titular t, Supplier<T> accion) {
        return t.institucion().isPresent() ? bd.comoInstitucion(t.institucion().get(), accion) : bd.sinContexto(accion);
    }
}
