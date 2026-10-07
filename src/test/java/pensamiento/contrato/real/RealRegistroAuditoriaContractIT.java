package pensamiento.contrato.real;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import pensamiento.contrato.RegistroAuditoriaContract;
import pensamiento.nucleo.puertos.RegistroAuditoria;
import pensamiento.testutil.BaseDatosDePrueba;
import pensamiento.web.usuarios.RegistroAuditoriaJdbc;

@EnabledIfEnvironmentVariable(named = "CONTRACT_REAL", matches = "true")
class RealRegistroAuditoriaContractIT extends RegistroAuditoriaContract {

    private static final BaseDatosDePrueba bd = new BaseDatosDePrueba();
    private static Personas personas;

    @BeforeAll
    static void sembrar() {
        UUID institucion = bd.crearInstitucion("contrato-auditoria-" + UUID.randomUUID());
        personas = new Personas(institucion, bd.crearUsuario(institucion, "perfil A"), bd.crearUsuario(institucion, "perfil B"));
    }

    @AfterAll
    static void limpiar() {
        bd.borrarInstitucion(personas.institucion());
    }

    @Override
    protected Personas personas() {
        return personas;
    }

    @Override
    protected RegistroAuditoria comoUsuario(UUID usuarioId) {
        RegistroAuditoriaJdbc real = new RegistroAuditoriaJdbc(bd.jdbcApp());
        return new RegistroAuditoria() {
            @Override public void registrar(Evento e) { bd.comoUsuario(usuarioId, personas.institucion(), () -> { real.registrar(e); return null; }); }
            @Override public List<Evento> deUsuario(UUID u) { return bd.comoUsuario(usuarioId, personas.institucion(), () -> real.deUsuario(u)); }
        };
    }
}
