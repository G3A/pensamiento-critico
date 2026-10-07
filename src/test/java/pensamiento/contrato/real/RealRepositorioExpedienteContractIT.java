package pensamiento.contrato.real;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import pensamiento.contrato.RepositorioExpedienteContract;
import pensamiento.expediente.RepositorioExpedienteJdbc;
import pensamiento.nucleo.Expediente;
import pensamiento.nucleo.puertos.RepositorioExpediente;
import pensamiento.testutil.BaseDatosDePrueba;

@EnabledIfEnvironmentVariable(named = "CONTRACT_REAL", matches = "true")
class RealRepositorioExpedienteContractIT extends RepositorioExpedienteContract {

    private static final BaseDatosDePrueba bd = new BaseDatosDePrueba();
    private static Personas personas;

    @BeforeAll
    static void sembrar() {
        UUID institucion = bd.crearInstitucion("contrato-expediente-" + UUID.randomUUID());
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
    protected RepositorioExpediente comoUsuario(UUID usuarioId) {
        RepositorioExpedienteJdbc real = new RepositorioExpedienteJdbc(bd.jdbcApp());
        return new RepositorioExpediente() {
            @Override public Expediente guardar(Expediente e) { return bd.comoUsuario(usuarioId, personas.institucion(), () -> real.guardar(e)); }
            @Override public Optional<Expediente> porId(UUID u, UUID id) { return bd.comoUsuario(usuarioId, personas.institucion(), () -> real.porId(u, id)); }
            @Override public List<Expediente> deUsuario(UUID u) { return bd.comoUsuario(usuarioId, personas.institucion(), () -> real.deUsuario(u)); }
            @Override public boolean borrar(UUID u, UUID id, java.time.Instant c) { return bd.comoUsuario(usuarioId, personas.institucion(), () -> real.borrar(u, id, c)); }
        };
    }
}
