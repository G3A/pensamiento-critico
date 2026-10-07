package pensamiento.contrato.real;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import pensamiento.contrato.RepositorioUsuariosContract;
import pensamiento.nucleo.Usuario;
import pensamiento.nucleo.puertos.RepositorioUsuarios;
import pensamiento.testutil.BaseDatosDePrueba;
import pensamiento.web.usuarios.RepositorioUsuariosJdbc;

/**
 * Contra PostgreSQL como rol de aplicación bajo RLS. La "institución única" real es la de la instalación,
 * que ya existe: la prueba de idempotencia la respeta. Lo escrito va a una institución de prueba que se borra.
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
        bd.borrarInstitucion(institucion);
    }

    @Override
    protected UUID institucionDePrueba() {
        return institucion;
    }

    @Override
    protected RepositorioUsuarios crearSut() {
        RepositorioUsuariosJdbc real = new RepositorioUsuariosJdbc(bd.jdbcApp());
        return new RepositorioUsuarios() {
            @Override public Optional<UUID> institucionUnica() { return bd.comoInstitucion(institucion, real::institucionUnica); }
            @Override public UUID crearInstitucion(String nombre) { return bd.comoInstitucion(institucion, () -> real.crearInstitucion(nombre)); }
            @Override public Usuario guardar(Usuario u) { return bd.comoInstitucion(institucion, () -> real.guardar(u)); }
            @Override public Optional<Usuario> porId(UUID i, UUID id) { return bd.comoInstitucion(institucion, () -> real.porId(i, id)); }
            @Override public Optional<Usuario> porNombre(UUID i, String n) { return bd.comoInstitucion(institucion, () -> real.porNombre(i, n)); }
            @Override public List<Usuario> todos(UUID i) { return bd.comoInstitucion(institucion, () -> real.todos(i)); }
            @Override public long contar(UUID i) { return bd.comoInstitucion(institucion, () -> real.contar(i)); }
        };
    }
}
