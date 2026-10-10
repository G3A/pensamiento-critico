package pensamiento.contrato.real;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import pensamiento.contrato.RegistroDeRazonamientoContract;
import pensamiento.expediente.RegistroDeRazonamientoJdbc;
import pensamiento.expediente.RepositorioCambiosOpinionJdbc;
import pensamiento.expediente.RepositorioEjecucionJdbc;
import pensamiento.expediente.RepositorioExpedienteJdbc;
import pensamiento.nucleo.AfirmacionConRol;
import pensamiento.nucleo.CambioOpinion;
import pensamiento.nucleo.Ejecucion;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Json;
import pensamiento.nucleo.Uuid7;
import pensamiento.nucleo.puertos.RegistroDeRazonamiento;
import pensamiento.testutil.BaseDatosDePrueba;

/** Contra el PostgreSQL del compose, como rol de aplicación bajo RLS, con una institución de prueba que se borra al final. */
@EnabledIfEnvironmentVariable(named = "CONTRACT_REAL", matches = "true")
class RealRegistroDeRazonamientoContractIT extends RegistroDeRazonamientoContract {

    private static final BaseDatosDePrueba bd = new BaseDatosDePrueba();
    private static Personas personas;

    @BeforeAll
    static void sembrar() {
        UUID institucion = bd.crearInstitucion("contrato-registro-" + UUID.randomUUID());
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
    protected RegistroDeRazonamiento comoUsuario(UUID usuarioId) {
        RegistroDeRazonamientoJdbc real = new RegistroDeRazonamientoJdbc(bd.jdbcApp());
        UUID inst = personas.institucion();
        return new RegistroDeRazonamiento() {
            @Override
            public List<EjecucionEnDiario> ejecucionesDesde(UUID u, Instant desde) {
                return bd.comoUsuario(usuarioId, inst, () -> real.ejecucionesDesde(u, desde));
            }

            @Override
            public List<CambioRegistrado> cambios(UUID u) {
                return bd.comoUsuario(usuarioId, inst, () -> real.cambios(u));
            }

            @Override
            public List<PosturaRegistrada> posturas(UUID u) {
                return bd.comoUsuario(usuarioId, inst, () -> real.posturas(u));
            }
        };
    }

    @Override
    protected UUID dadaUnaEjecucion(UUID usuarioId, IdTecnica tecnica, String resumen, Optional<UUID> expediente, List<AfirmacionConRol> afirmaciones,
                                    Instant cuando) {
        Ejecucion e = new Ejecucion(Uuid7.en(cuando), usuarioId, personas.institucion(), tecnica, 1, expediente, Json.VACIO, Json.VACIO, Json.VACIO,
                resumen, Optional.empty(), "registro-" + UUID.randomUUID(), cuando);
        RepositorioEjecucionJdbc ejecuciones = new RepositorioEjecucionJdbc(bd.jdbcApp());
        bd.comoUsuario(usuarioId, personas.institucion(), () -> ejecuciones.guardar(e, afirmaciones, List.of()));
        return e.id();
    }

    @Override
    protected UUID dadoUnExpediente(UUID usuarioId, String nombre) {
        return bd.crearExpediente(personas.institucion(), usuarioId, nombre);
    }

    @Override
    protected void borrarExpediente(UUID usuarioId, UUID expedienteId) {
        RepositorioExpedienteJdbc expedientes = new RepositorioExpedienteJdbc(bd.jdbcApp());
        bd.comoUsuario(usuarioId, personas.institucion(), () -> expedientes.borrar(usuarioId, expedienteId, Instant.parse("2031-12-31T00:00:00Z")));
    }

    @Override
    protected void dadoUnCambio(UUID usuarioId, UUID ejecucionId, CambioOpinion.Declarado cambio, Instant cuando) {
        RepositorioCambiosOpinionJdbc cambios = new RepositorioCambiosOpinionJdbc(bd.jdbcApp());
        bd.comoUsuario(usuarioId, personas.institucion(), () -> {
            cambios.guardar(usuarioId, personas.institucion(), ejecucionId, List.of(cambio), cuando);
            return null;
        });
    }
}
