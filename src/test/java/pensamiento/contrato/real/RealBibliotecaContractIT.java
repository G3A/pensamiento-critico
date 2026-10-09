package pensamiento.contrato.real;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import pensamiento.biblioteca.BibliotecaPgvector;
import pensamiento.contrato.BibliotecaContract;
import pensamiento.nucleo.Documento;
import pensamiento.nucleo.Fragmento;
import pensamiento.nucleo.Pasaje;
import pensamiento.nucleo.puertos.Biblioteca;
import pensamiento.testutil.BaseDatosDePrueba;

/**
 * Contra el PostgreSQL del compose con pgvector, como rol de aplicación bajo RLS (cada llamada en su transacción como la
 * persona), con una institución de prueba que se borra al final.
 */
@EnabledIfEnvironmentVariable(named = "CONTRACT_REAL", matches = "true")
class RealBibliotecaContractIT extends BibliotecaContract {

    private static final BaseDatosDePrueba bd = new BaseDatosDePrueba();
    private static Personas personas;

    @BeforeAll
    static void sembrar() {
        UUID institucion = bd.crearInstitucion("contrato-biblioteca-" + UUID.randomUUID());
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
    protected Biblioteca comoUsuario(UUID usuarioId) {
        BibliotecaPgvector real = new BibliotecaPgvector(bd.jdbcApp());
        UUID inst = personas.institucion();
        return new Biblioteca() {
            @Override
            public Documento crear(UUID u, UUID i, NuevoDocumento n) {
                return bd.comoUsuario(usuarioId, inst, () -> real.crear(u, i, n));
            }

            @Override
            public Optional<Documento> propioPorHash(UUID u, String hash) {
                return bd.comoUsuario(usuarioId, inst, () -> real.propioPorHash(u, hash));
            }

            @Override
            public Optional<Documento> porId(UUID u, UUID id) {
                return bd.comoUsuario(usuarioId, inst, () -> real.porId(u, id));
            }

            @Override
            public List<Documento> visibles(UUID u) {
                return bd.comoUsuario(usuarioId, inst, () -> real.visibles(u));
            }

            @Override
            public Optional<byte[]> contenido(UUID u, UUID id) {
                return bd.comoUsuario(usuarioId, inst, () -> real.contenido(u, id));
            }

            @Override
            public void indexar(UUID u, UUID d, List<Fragmento.Nuevo> f, Optional<Integer> p) {
                bd.comoUsuario(usuarioId, inst, () -> {
                    real.indexar(u, d, f, p);
                    return null;
                });
            }

            @Override
            public void marcarError(UUID u, UUID d, String motivo) {
                bd.comoUsuario(usuarioId, inst, () -> {
                    real.marcarError(u, d, motivo);
                    return null;
                });
            }

            @Override
            public List<Fragmento> sinVector(UUID u, UUID d, int limite) {
                return bd.comoUsuario(usuarioId, inst, () -> real.sinVector(u, d, limite));
            }

            @Override
            public void guardarVectores(UUID u, Map<UUID, float[]> v) {
                bd.comoUsuario(usuarioId, inst, () -> {
                    real.guardarVectores(u, v);
                    return null;
                });
            }

            @Override
            public List<Fragmento> fragmentos(UUID u, UUID d) {
                return bd.comoUsuario(usuarioId, inst, () -> real.fragmentos(u, d));
            }

            @Override
            public List<Pasaje> buscarPorTexto(UUID u, String consulta, int limite) {
                return bd.comoUsuario(usuarioId, inst, () -> real.buscarPorTexto(u, consulta, limite));
            }

            @Override
            public List<Pasaje> buscarPorVector(UUID u, float[] consulta, int limite) {
                return bd.comoUsuario(usuarioId, inst, () -> real.buscarPorVector(u, consulta, limite));
            }

            @Override
            public boolean compartir(UUID u, UUID d, boolean c) {
                return bd.comoUsuario(usuarioId, inst, () -> real.compartir(u, d, c));
            }

            @Override
            public boolean borrar(UUID u, UUID d) {
                return bd.comoUsuario(usuarioId, inst, () -> real.borrar(u, d));
            }
        };
    }
}
