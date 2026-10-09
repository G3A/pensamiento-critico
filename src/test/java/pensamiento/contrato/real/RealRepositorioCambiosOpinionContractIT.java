package pensamiento.contrato.real;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import pensamiento.contrato.RepositorioCambiosOpinionContract;
import pensamiento.expediente.RepositorioCambiosOpinionJdbc;
import pensamiento.expediente.RepositorioEjecucionJdbc;
import pensamiento.nucleo.AfirmacionConRol;
import pensamiento.nucleo.CambioOpinion;
import pensamiento.nucleo.Ejecucion;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Json;
import pensamiento.nucleo.OrigenAfirmacion;
import pensamiento.nucleo.RolAfirmacion;
import pensamiento.nucleo.SentidoAfirmacion;
import pensamiento.nucleo.TipoAfirmacion;
import pensamiento.nucleo.Uuid7;
import pensamiento.nucleo.puertos.RepositorioCambiosOpinion;
import pensamiento.testutil.BaseDatosDePrueba;

/** Contra el PostgreSQL del compose, como rol de aplicación bajo RLS, con una institución de prueba que se borra al final. */
@EnabledIfEnvironmentVariable(named = "CONTRACT_REAL", matches = "true")
class RealRepositorioCambiosOpinionContractIT extends RepositorioCambiosOpinionContract {

    private static final BaseDatosDePrueba bd = new BaseDatosDePrueba();
    private static Personas personas;

    @BeforeAll
    static void sembrar() {
        UUID institucion = bd.crearInstitucion("contrato-cambios-" + UUID.randomUUID());
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
    protected RepositorioCambiosOpinion comoUsuario(UUID usuarioId) {
        RepositorioCambiosOpinionJdbc real = new RepositorioCambiosOpinionJdbc(bd.jdbcApp());
        UUID inst = personas.institucion();
        return new RepositorioCambiosOpinion() {
            @Override
            public void guardar(UUID u, UUID i, UUID e, List<CambioOpinion.Declarado> c, Instant cuando) {
                bd.comoUsuario(usuarioId, inst, () -> {
                    real.guardar(u, i, e, c, cuando);
                    return null;
                });
            }

            @Override
            public void restaurar(UUID u, UUID i, CambioOpinion c) {
                bd.comoUsuario(usuarioId, inst, () -> {
                    real.restaurar(u, i, c);
                    return null;
                });
            }

            @Override
            public List<CambioOpinion> deUsuario(UUID u) {
                return bd.comoUsuario(usuarioId, inst, () -> real.deUsuario(u));
            }

            @Override
            public List<CambioOpinion> deEjecucion(UUID u, UUID e) {
                return bd.comoUsuario(usuarioId, inst, () -> real.deEjecucion(u, e));
            }
        };
    }

    @Override
    protected EjecucionConAfirmaciones dadaUnaEjecucionConAfirmaciones(UUID usuarioId, List<String> textos) {
        Instant ahora = Instant.parse("2026-10-09T15:00:00Z");
        Ejecucion e = new Ejecucion(Uuid7.en(ahora), usuarioId, personas.institucion(), IdTecnica.de("T08"), 1, Optional.empty(),
                Json.VACIO, Json.VACIO, Json.VACIO, "Sesión de prueba.", Optional.empty(), "cambios-" + UUID.randomUUID(), ahora);
        List<AfirmacionConRol> afirmaciones = new ArrayList<>();
        for (String t : textos) {
            afirmaciones.add(new AfirmacionConRol(UUID.randomUUID(), t, TipoAfirmacion.JUICIO_DE_VALOR, RolAfirmacion.POSTURA,
                    SentidoAfirmacion.PRODUCIDA, OrigenAfirmacion.USUARIO));
        }
        RepositorioEjecucionJdbc ejecuciones = new RepositorioEjecucionJdbc(bd.jdbcApp());
        bd.comoUsuario(usuarioId, personas.institucion(), () -> ejecuciones.guardar(e, afirmaciones, List.of()));
        return new EjecucionConAfirmaciones(e.id(), afirmaciones.stream().map(AfirmacionConRol::afirmacionId).toList());
    }
}
