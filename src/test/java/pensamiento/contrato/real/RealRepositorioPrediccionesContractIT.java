package pensamiento.contrato.real;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import pensamiento.contrato.RepositorioPrediccionesContract;
import pensamiento.expediente.RepositorioEjecucionJdbc;
import pensamiento.expediente.RepositorioPrediccionesJdbc;
import pensamiento.nucleo.AfirmacionConRol;
import pensamiento.nucleo.Ejecucion;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Json;
import pensamiento.nucleo.OrigenAfirmacion;
import pensamiento.nucleo.Prediccion;
import pensamiento.nucleo.PrediccionDeclarada;
import pensamiento.nucleo.RolAfirmacion;
import pensamiento.nucleo.SentidoAfirmacion;
import pensamiento.nucleo.TipoAfirmacion;
import pensamiento.nucleo.Uuid7;
import pensamiento.nucleo.puertos.RepositorioPredicciones;
import pensamiento.testutil.BaseDatosDePrueba;

/** Contra el PostgreSQL del compose, como rol de aplicación bajo RLS, con una institución de prueba que se borra al final. */
@EnabledIfEnvironmentVariable(named = "CONTRACT_REAL", matches = "true")
class RealRepositorioPrediccionesContractIT extends RepositorioPrediccionesContract {

    private static final BaseDatosDePrueba bd = new BaseDatosDePrueba();
    private static Personas personas;

    @BeforeAll
    static void sembrar() {
        UUID institucion = bd.crearInstitucion("contrato-predicciones-" + UUID.randomUUID());
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
    protected RepositorioPredicciones comoUsuario(UUID usuarioId) {
        RepositorioPrediccionesJdbc real = new RepositorioPrediccionesJdbc(bd.jdbcApp());
        UUID inst = personas.institucion();
        return new RepositorioPredicciones() {
            @Override
            public void guardar(UUID u, UUID i, UUID e, List<PrediccionDeclarada> p) {
                bd.comoUsuario(usuarioId, inst, () -> {
                    real.guardar(u, i, e, p);
                    return null;
                });
            }

            @Override
            public Optional<Prediccion> porId(UUID u, UUID id) {
                return bd.comoUsuario(usuarioId, inst, () -> real.porId(u, id));
            }

            @Override
            public List<Prediccion> deUsuario(UUID u) {
                return bd.comoUsuario(usuarioId, inst, () -> real.deUsuario(u));
            }

            @Override
            public List<Prediccion> deEjecucion(UUID u, UUID e) {
                return bd.comoUsuario(usuarioId, inst, () -> real.deEjecucion(u, e));
            }

            @Override
            public Optional<Prediccion> resolver(UUID u, UUID id, boolean seCumplio, Instant cuando) {
                return bd.comoUsuario(usuarioId, inst, () -> real.resolver(u, id, seCumplio, cuando));
            }
        };
    }

    @Override
    protected EjecucionConAfirmaciones dadaUnaEjecucionConAfirmaciones(UUID usuarioId, List<String> textos) {
        Instant ahora = Instant.parse("2026-10-07T15:00:00Z");
        Ejecucion e = new Ejecucion(Uuid7.en(ahora), usuarioId, personas.institucion(), IdTecnica.de("T32"), 1, Optional.empty(),
                Json.VACIO, Json.VACIO, Json.VACIO, "Decisión de prueba.", Optional.empty(), "predicciones-" + UUID.randomUUID(), ahora);
        List<AfirmacionConRol> afirmaciones = new ArrayList<>();
        for (String t : textos) {
            afirmaciones.add(new AfirmacionConRol(UUID.randomUUID(), t, TipoAfirmacion.PREDICCION, RolAfirmacion.PREDICCION,
                    SentidoAfirmacion.PRODUCIDA, OrigenAfirmacion.USUARIO));
        }
        RepositorioEjecucionJdbc ejecuciones = new RepositorioEjecucionJdbc(bd.jdbcApp());
        bd.comoUsuario(usuarioId, personas.institucion(), () -> ejecuciones.guardar(e, afirmaciones, List.of()));
        return new EjecucionConAfirmaciones(e.id(), afirmaciones.stream().map(AfirmacionConRol::afirmacionId).toList());
    }
}
