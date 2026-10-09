package pensamiento.contrato.real;

import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import pensamiento.contrato.TransaccionComoUsuarioContract;
import pensamiento.nucleo.puertos.TransaccionComoUsuario;
import pensamiento.testutil.BaseDatosDePrueba;
import pensamiento.web.seguridad.GestorTransaccionesRls;
import pensamiento.web.seguridad.TransaccionComoUsuarioRls;

/** Contra el PostgreSQL del compose: dentro de la transacción, app.usuario es la persona pedida; fuera, nadie. */
@EnabledIfEnvironmentVariable(named = "CONTRACT_REAL", matches = "true")
class RealTransaccionComoUsuarioContractIT extends TransaccionComoUsuarioContract {

    private static final BaseDatosDePrueba bd = new BaseDatosDePrueba();
    private static Persona persona;

    @BeforeAll
    static void sembrar() {
        UUID institucion = bd.crearInstitucion("contrato-transaccion-" + UUID.randomUUID());
        persona = new Persona(institucion, bd.crearUsuario(institucion, "perfil A"));
    }

    @AfterAll
    static void limpiar() {
        bd.borrarInstitucion(persona.institucion());
    }

    @Override
    protected Persona persona() {
        return persona;
    }

    @Override
    protected TransaccionComoUsuario transaccion() {
        return new TransaccionComoUsuarioRls(new GestorTransaccionesRls(bd.dataSourceApp()));
    }

    @Override
    protected String usuarioQueVeLaBase() {
        return bd.jdbcApp().sql("SELECT coalesce(current_setting('app.usuario', true), '')").query(String.class).single();
    }
}
