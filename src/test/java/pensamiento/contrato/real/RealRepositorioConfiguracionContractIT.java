package pensamiento.contrato.real;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import pensamiento.catalogo.RepositorioConfiguracionJdbc;
import pensamiento.contrato.RepositorioConfiguracionContract;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Json;
import pensamiento.nucleo.puertos.RepositorioConfiguracion;
import pensamiento.testutil.BaseDatosDePrueba;

/** Contra el PostgreSQL del compose, como rol de aplicación bajo RLS, con una institución de prueba que se borra al final. */
@EnabledIfEnvironmentVariable(named = "CONTRACT_REAL", matches = "true")
class RealRepositorioConfiguracionContractIT extends RepositorioConfiguracionContract {

    private static final BaseDatosDePrueba bd = new BaseDatosDePrueba();
    private static Personas personas;

    @BeforeAll
    static void sembrar() {
        UUID institucion = bd.crearInstitucion("contrato-configuracion-" + UUID.randomUUID());
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
    protected RepositorioConfiguracion comoUsuario(UUID usuarioId) {
        RepositorioConfiguracionJdbc real = new RepositorioConfiguracionJdbc(bd.jdbcApp());
        UUID inst = personas.institucion();
        return new RepositorioConfiguracion() {
            @Override public Optional<Guardada> de(UUID u, IdTecnica t) { return bd.comoUsuario(usuarioId, inst, () -> real.de(u, t)); }
            @Override public void guardar(UUID u, UUID i, IdTecnica t, int v, Json j) { bd.comoUsuario(usuarioId, inst, () -> { real.guardar(u, i, t, v, j); return null; }); }
            @Override public void restablecer(UUID u, IdTecnica t) { bd.comoUsuario(usuarioId, inst, () -> { real.restablecer(u, t); return null; }); }
            @Override public Map<IdTecnica, Guardada> todas(UUID u) { return bd.comoUsuario(usuarioId, inst, () -> real.todas(u)); }
        };
    }
}
