package pensamiento.contrato.real;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import pensamiento.contrato.RepositorioArgumentosContract;
import pensamiento.expediente.RepositorioArgumentosJdbc;
import pensamiento.expediente.RepositorioEjecucionJdbc;
import pensamiento.nucleo.AfirmacionConRol;
import pensamiento.nucleo.ArgumentoProducido;
import pensamiento.nucleo.Ejecucion;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Json;
import pensamiento.nucleo.OrigenAfirmacion;
import pensamiento.nucleo.RolAfirmacion;
import pensamiento.nucleo.SentidoAfirmacion;
import pensamiento.nucleo.TipoAfirmacion;
import pensamiento.nucleo.Uuid7;
import pensamiento.nucleo.puertos.RepositorioArgumentos;
import pensamiento.testutil.BaseDatosDePrueba;

/** Contra el PostgreSQL del compose, como rol de aplicación bajo RLS, con una institución de prueba que se borra al final. */
@EnabledIfEnvironmentVariable(named = "CONTRACT_REAL", matches = "true")
class RealRepositorioArgumentosContractIT extends RepositorioArgumentosContract {

    private static final BaseDatosDePrueba bd = new BaseDatosDePrueba();
    private static Personas personas;

    @BeforeAll
    static void sembrar() {
        UUID institucion = bd.crearInstitucion("contrato-argumentos-" + UUID.randomUUID());
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
    protected RepositorioArgumentos comoUsuario(UUID usuarioId) {
        RepositorioArgumentosJdbc real = new RepositorioArgumentosJdbc(bd.jdbcApp());
        UUID inst = personas.institucion();
        return new RepositorioArgumentos() {
            @Override
            public void guardar(UUID u, UUID i, UUID e, List<ArgumentoProducido> a) {
                bd.comoUsuario(usuarioId, inst, () -> {
                    real.guardar(u, i, e, a);
                    return null;
                });
            }

            @Override
            public List<ArgumentoGuardado> deEjecucion(UUID u, UUID e) {
                return bd.comoUsuario(usuarioId, inst, () -> real.deEjecucion(u, e));
            }

            @Override
            public Optional<ArgumentoGuardado> porId(UUID u, UUID id) {
                return bd.comoUsuario(usuarioId, inst, () -> real.porId(u, id));
            }
        };
    }

    @Override
    protected EjecucionConAfirmaciones dadaUnaEjecucionConAfirmaciones(UUID usuarioId, int cuantas) {
        Instant ahora = Instant.parse("2026-10-07T15:00:00Z");
        Ejecucion e = new Ejecucion(Uuid7.en(ahora), usuarioId, personas.institucion(), IdTecnica.de("T01"), 1, Optional.empty(),
                Json.VACIO, Json.VACIO, Json.VACIO, "Mapa de prueba.", Optional.empty(), "argumentos-" + UUID.randomUUID(), ahora);
        List<AfirmacionConRol> afirmaciones = new ArrayList<>();
        for (int i = 0; i < cuantas; i++) {
            afirmaciones.add(new AfirmacionConRol(UUID.randomUUID(), "Afirmación " + (i + 1) + " del mapa", TipoAfirmacion.HECHO,
                    i == 0 ? RolAfirmacion.CONCLUSION : RolAfirmacion.PREMISA, SentidoAfirmacion.PRODUCIDA, OrigenAfirmacion.USUARIO));
        }
        RepositorioEjecucionJdbc ejecuciones = new RepositorioEjecucionJdbc(bd.jdbcApp());
        bd.comoUsuario(usuarioId, personas.institucion(), () -> ejecuciones.guardar(e, afirmaciones, List.of()));
        return new EjecucionConAfirmaciones(e.id(), afirmaciones.stream().map(AfirmacionConRol::afirmacionId).toList());
    }
}
