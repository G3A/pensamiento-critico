package pensamiento.contrato.real;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import pensamiento.contrato.RepositorioDojoContract;
import pensamiento.expediente.RepositorioDojoJdbc;
import pensamiento.nucleo.Competencia;
import pensamiento.nucleo.IntentoDojo;
import pensamiento.nucleo.puertos.RepositorioDojo;
import pensamiento.testutil.BaseDatosDePrueba;

/** Contra el PostgreSQL del compose, como rol de aplicación bajo RLS, con una institución de prueba que se borra al final. */
@EnabledIfEnvironmentVariable(named = "CONTRACT_REAL", matches = "true")
class RealRepositorioDojoContractIT extends RepositorioDojoContract {

    private static final BaseDatosDePrueba bd = new BaseDatosDePrueba();
    private static Personas personas;

    @BeforeAll
    static void sembrar() {
        UUID institucion = bd.crearInstitucion("contrato-dojo-" + UUID.randomUUID());
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
    protected RepositorioDojo comoUsuario(UUID usuarioId) {
        RepositorioDojoJdbc real = new RepositorioDojoJdbc(bd.jdbcApp());
        UUID inst = personas.institucion();
        return new RepositorioDojo() {
            @Override
            public boolean guardar(UUID u, UUID i, IntentoDojo intento, Competencia c) {
                return bd.comoUsuario(usuarioId, inst, () -> real.guardar(u, i, intento, c));
            }

            @Override
            public List<IntentoDojo> intentos(UUID u) {
                return bd.comoUsuario(usuarioId, inst, () -> real.intentos(u));
            }

            @Override
            public List<Competencia> competencias(UUID u) {
                return bd.comoUsuario(usuarioId, inst, () -> real.competencias(u));
            }

            @Override
            public void restaurar(UUID u, UUID i, IntentoDojo intento) {
                bd.comoUsuario(usuarioId, inst, () -> {
                    real.restaurar(u, i, intento);
                    return null;
                });
            }

            @Override
            public void restaurar(UUID u, UUID i, Competencia c) {
                bd.comoUsuario(usuarioId, inst, () -> {
                    real.restaurar(u, i, c);
                    return null;
                });
            }
        };
    }
}
