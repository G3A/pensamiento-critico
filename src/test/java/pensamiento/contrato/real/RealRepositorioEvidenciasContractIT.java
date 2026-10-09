package pensamiento.contrato.real;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import pensamiento.biblioteca.BibliotecaPgvector;
import pensamiento.contrato.RepositorioEvidenciasContract;
import pensamiento.expediente.RepositorioEjecucionJdbc;
import pensamiento.expediente.RepositorioEvidenciasJdbc;
import pensamiento.nucleo.AfirmacionConRol;
import pensamiento.nucleo.Documento;
import pensamiento.nucleo.Ejecucion;
import pensamiento.nucleo.EvidenciaGuardada;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Json;
import pensamiento.nucleo.OrigenAfirmacion;
import pensamiento.nucleo.RolAfirmacion;
import pensamiento.nucleo.SentidoAfirmacion;
import pensamiento.nucleo.TipoAfirmacion;
import pensamiento.nucleo.Uuid7;
import pensamiento.nucleo.puertos.Biblioteca;
import pensamiento.nucleo.puertos.RepositorioEvidencias;
import pensamiento.testutil.BaseDatosDePrueba;

/** Contra el PostgreSQL del compose, como rol de aplicación bajo RLS, con una institución de prueba que se borra al final. */
@EnabledIfEnvironmentVariable(named = "CONTRACT_REAL", matches = "true")
class RealRepositorioEvidenciasContractIT extends RepositorioEvidenciasContract {

    private static final BaseDatosDePrueba bd = new BaseDatosDePrueba();
    private static Personas personas;

    @BeforeAll
    static void sembrar() {
        UUID institucion = bd.crearInstitucion("contrato-evidencias-" + UUID.randomUUID());
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
    protected RepositorioEvidencias comoUsuario(UUID usuarioId) {
        RepositorioEvidenciasJdbc real = new RepositorioEvidenciasJdbc(bd.jdbcApp());
        UUID inst = personas.institucion();
        return new RepositorioEvidencias() {
            @Override
            public void guardar(UUID u, UUID i, EvidenciaGuardada e) {
                bd.comoUsuario(usuarioId, inst, () -> {
                    real.guardar(u, i, e);
                    return null;
                });
            }

            @Override
            public List<EvidenciaGuardada> deAfirmacion(UUID u, UUID a) {
                return bd.comoUsuario(usuarioId, inst, () -> real.deAfirmacion(u, a));
            }

            @Override
            public Optional<EvidenciaGuardada> porId(UUID u, UUID id) {
                return bd.comoUsuario(usuarioId, inst, () -> real.porId(u, id));
            }

            @Override
            public boolean quitar(UUID u, UUID id) {
                return bd.comoUsuario(usuarioId, inst, () -> real.quitar(u, id));
            }

            @Override
            public List<EvidenciaGuardada> deUsuario(UUID u) {
                return bd.comoUsuario(usuarioId, inst, () -> real.deUsuario(u));
            }
        };
    }

    @Override
    protected UUID dadaUnaAfirmacion(UUID usuarioId, String texto) {
        Instant ahora = Instant.parse("2026-10-09T15:00:00Z");
        AfirmacionConRol a = new AfirmacionConRol(UUID.randomUUID(), texto, TipoAfirmacion.HECHO, RolAfirmacion.HIPOTESIS, SentidoAfirmacion.PRODUCIDA,
                OrigenAfirmacion.USUARIO);
        Ejecucion e = new Ejecucion(Uuid7.en(ahora), usuarioId, personas.institucion(), IdTecnica.de("T22"), 1, Optional.empty(), Json.VACIO, Json.VACIO,
                Json.VACIO, "Triangulación de prueba.", Optional.empty(), "evidencias-" + UUID.randomUUID(), ahora);
        RepositorioEjecucionJdbc ejecuciones = new RepositorioEjecucionJdbc(bd.jdbcApp());
        bd.comoUsuario(usuarioId, personas.institucion(), () -> ejecuciones.guardar(e, List.of(a), List.of()));
        return a.afirmacionId();
    }

    @Override
    protected UUID dadoUnDocumento(UUID usuarioId, String nombre) {
        BibliotecaPgvector biblioteca = new BibliotecaPgvector(bd.jdbcApp());
        return bd.comoUsuario(usuarioId, personas.institucion(), () -> biblioteca.crear(usuarioId, personas.institucion(),
                new Biblioteca.NuevoDocumento(UUID.randomUUID(), nombre, Documento.Tipo.PDF, "hash-" + UUID.randomUUID(),
                        "%PDF-1.4".getBytes(StandardCharsets.ISO_8859_1))).id());
    }

    @Override
    protected void borrarDocumento(UUID usuarioId, UUID documentoId) {
        BibliotecaPgvector biblioteca = new BibliotecaPgvector(bd.jdbcApp());
        bd.comoUsuario(usuarioId, personas.institucion(), () -> biblioteca.borrar(usuarioId, documentoId));
    }
}
