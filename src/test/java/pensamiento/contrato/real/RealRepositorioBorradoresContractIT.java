package pensamiento.contrato.real;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import pensamiento.contrato.RepositorioBorradoresContract;
import pensamiento.expediente.RepositorioBorradoresJdbc;
import pensamiento.nucleo.puertos.RepositorioBorradores;
import pensamiento.testutil.BaseDatosDePrueba;

/** Contra el PostgreSQL del compose, como rol de aplicación bajo RLS por usuario: la otra persona es el docente de la estudiante. */
@EnabledIfEnvironmentVariable(named = "CONTRACT_REAL", matches = "true")
class RealRepositorioBorradoresContractIT extends RepositorioBorradoresContract {

    private static final BaseDatosDePrueba bd = new BaseDatosDePrueba();
    private static Personas personas;

    @BeforeAll
    static void sembrar() {
        UUID institucion = bd.crearInstitucion("contrato-borradores-" + UUID.randomUUID());
        UUID estudiante = bd.crearUsuario(institucion, "estudiante", "estudiante");
        UUID docente = bd.crearUsuario(institucion, "docente", "docente");
        UUID grupo = bd.jdbcAdmin().sql("INSERT INTO grupo (institucion_id, nombre, docente_id) VALUES (:i, 'grupo', :d) RETURNING id")
                .param("i", institucion).param("d", docente).query(UUID.class).single();
        bd.jdbcAdmin().sql("INSERT INTO miembro_grupo (grupo_id, usuario_id, rol) VALUES (:g, :u, 'estudiante')").param("g", grupo).param("u", estudiante).update();
        personas = new Personas(institucion, estudiante, docente);
    }

    @AfterAll
    static void limpiar() {
        bd.jdbcAdmin().sql("DELETE FROM borrador WHERE institucion_id = :i").param("i", personas.institucion()).update();
        bd.borrarInstitucion(personas.institucion());
    }

    @Override
    protected Personas personas() {
        return personas;
    }

    @Override
    protected RepositorioBorradores comoUsuario(UUID usuarioId) {
        RepositorioBorradoresJdbc real = new RepositorioBorradoresJdbc(bd.jdbcApp());
        UUID inst = personas.institucion();
        return new RepositorioBorradores() {
            @Override
            public void guardar(UUID u, UUID i, String clave, Map<String, String> campos, Instant ahora) {
                bd.comoUsuario(usuarioId, inst, () -> {
                    real.guardar(u, i, clave, campos, ahora);
                    return null;
                });
            }

            @Override
            public Optional<Borrador> leer(UUID u, String clave) {
                return bd.comoUsuario(usuarioId, inst, () -> real.leer(u, clave));
            }

            @Override
            public boolean borrar(UUID u, String clave) {
                return bd.comoUsuario(usuarioId, inst, () -> real.borrar(u, clave));
            }
        };
    }
}
