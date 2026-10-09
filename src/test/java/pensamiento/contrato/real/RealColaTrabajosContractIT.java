package pensamiento.contrato.real;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import pensamiento.contrato.ColaTrabajosContract;
import pensamiento.nucleo.puertos.ColaTrabajos;
import pensamiento.testutil.BaseDatosDePrueba;
import pensamiento.trabajos.ColaTrabajosJdbc;

/**
 * Contra la tabla trabajo del PostgreSQL del compose, como rol de aplicación. Cada prueba usa un tipo propio: la app del
 * compose solo toma los tipos que conoce, así que nunca toca estos trabajos.
 */
@EnabledIfEnvironmentVariable(named = "CONTRACT_REAL", matches = "true")
class RealColaTrabajosContractIT extends ColaTrabajosContract {

    private static final BaseDatosDePrueba bd = new BaseDatosDePrueba();

    @Override
    protected ColaTrabajos cola() {
        return new ColaTrabajosJdbc(bd.jdbcApp());
    }
}
