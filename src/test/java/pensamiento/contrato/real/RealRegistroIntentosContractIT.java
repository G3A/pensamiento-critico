package pensamiento.contrato.real;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import pensamiento.contrato.RegistroIntentosContract;
import pensamiento.nucleo.puertos.RegistroIntentos;
import pensamiento.testutil.BaseDatosDePrueba;
import pensamiento.web.seguridad.RegistroIntentosJdbc;

/** Contra PostgreSQL como rol de aplicación sin contexto (como antes de entrar): solo por las funciones de V14. */
@EnabledIfEnvironmentVariable(named = "CONTRACT_REAL", matches = "true")
class RealRegistroIntentosContractIT extends RegistroIntentosContract {

    private final BaseDatosDePrueba bd = new BaseDatosDePrueba();
    private UUID institucion;
    private UUID otra;

    @BeforeEach
    void sembrar() {
        institucion = bd.crearInstitucion("contrato-intentos-" + UUID.randomUUID());
        otra = bd.crearInstitucion("contrato-intentos-otra-" + UUID.randomUUID());
    }

    @AfterEach
    void limpiar() {
        bd.borrarInstitucion(institucion);
        bd.borrarInstitucion(otra);
    }

    @Override
    protected UUID institucion() {
        return institucion;
    }

    @Override
    protected UUID otraInstitucion() {
        return otra;
    }

    @Override
    protected RegistroIntentos crearSut() {
        RegistroIntentosJdbc real = new RegistroIntentosJdbc(bd.jdbcApp());
        return new RegistroIntentos() {
            @Override public Optional<Estado> leer(Optional<UUID> i, String c) { return bd.sinContexto(() -> real.leer(i, c)); }
            @Override public void guardar(Optional<UUID> i, String c, Estado e) { bd.sinContexto(() -> { real.guardar(i, c, e); return null; }); }
            @Override public void borrar(Optional<UUID> i, String c) { bd.sinContexto(() -> { real.borrar(i, c); return null; }); }
            @Override public List<Espera> esperas(UUID i, Instant a) { return bd.sinContexto(() -> real.esperas(i, a)); }
        };
    }
}
