package pensamiento.contrato.real;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import pensamiento.contrato.RepositorioVerificacionesContract;
import pensamiento.expediente.RepositorioEjecucionJdbc;
import pensamiento.expediente.RepositorioVerificacionesJdbc;
import pensamiento.nucleo.Afirmacion;
import pensamiento.nucleo.AfirmacionConRol;
import pensamiento.nucleo.Ejecucion;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Json;
import pensamiento.nucleo.OrigenAfirmacion;
import pensamiento.nucleo.RolAfirmacion;
import pensamiento.nucleo.SentidoAfirmacion;
import pensamiento.nucleo.TipoAfirmacion;
import pensamiento.nucleo.Uuid7;
import pensamiento.nucleo.Verificacion;
import pensamiento.nucleo.puertos.RepositorioVerificaciones;
import pensamiento.testutil.BaseDatosDePrueba;

/** Contra el PostgreSQL del compose, como rol de aplicación bajo RLS, con una institución de prueba que se borra al final. */
@EnabledIfEnvironmentVariable(named = "CONTRACT_REAL", matches = "true")
class RealRepositorioVerificacionesContractIT extends RepositorioVerificacionesContract {

    private static final BaseDatosDePrueba bd = new BaseDatosDePrueba();
    private static Personas personas;

    @BeforeAll
    static void sembrar() {
        UUID institucion = bd.crearInstitucion("contrato-verificaciones-" + UUID.randomUUID());
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
    protected RepositorioVerificaciones comoUsuario(UUID usuarioId) {
        RepositorioVerificacionesJdbc real = new RepositorioVerificacionesJdbc(bd.jdbcApp());
        UUID inst = personas.institucion();
        return new RepositorioVerificaciones() {
            @Override
            public Optional<Afirmacion> afirmacion(UUID u, UUID a) {
                return bd.comoUsuario(usuarioId, inst, () -> real.afirmacion(u, a));
            }

            @Override
            public boolean cambiarTipo(UUID u, UUID a, TipoAfirmacion t) {
                return bd.comoUsuario(usuarioId, inst, () -> real.cambiarTipo(u, a, t));
            }

            @Override
            public Verificacion verificacion(UUID u, UUID a) {
                return bd.comoUsuario(usuarioId, inst, () -> real.verificacion(u, a));
            }

            @Override
            public void marcarPreguntas(UUID u, UUID i, UUID a, List<String> r, Instant c) {
                bd.comoUsuario(usuarioId, inst, () -> {
                    real.marcarPreguntas(u, i, a, r, c);
                    return null;
                });
            }

            @Override
            public boolean guardarVeredicto(UUID u, UUID a, Verificacion.Veredicto v) {
                return bd.comoUsuario(usuarioId, inst, () -> real.guardarVeredicto(u, a, v));
            }

            @Override
            public Optional<Verificacion.Origen> origen(UUID u, UUID a) {
                return bd.comoUsuario(usuarioId, inst, () -> real.origen(u, a));
            }

            @Override
            public List<Verificacion> deUsuario(UUID u) {
                return bd.comoUsuario(usuarioId, inst, () -> real.deUsuario(u));
            }
        };
    }

    @Override
    protected AfirmacionDeEjecucion dadaUnaAfirmacion(UUID usuarioId, String texto, TipoAfirmacion tipo, boolean enExpediente) {
        Instant ahora = Instant.parse("2026-10-09T15:00:00Z");
        Optional<UUID> expediente = enExpediente ? Optional.of(bd.crearExpediente(personas.institucion(), usuarioId, "Expediente de prueba"))
                : Optional.empty();
        AfirmacionConRol a = new AfirmacionConRol(UUID.randomUUID(), texto, tipo, RolAfirmacion.PREMISA, SentidoAfirmacion.PRODUCIDA, OrigenAfirmacion.USUARIO);
        Ejecucion e = new Ejecucion(Uuid7.en(ahora), usuarioId, personas.institucion(), IdTecnica.de("T01"), 1, expediente, Json.VACIO, Json.VACIO,
                Json.VACIO, "Mapa de prueba.", Optional.empty(), "verificaciones-" + UUID.randomUUID(), ahora);
        RepositorioEjecucionJdbc ejecuciones = new RepositorioEjecucionJdbc(bd.jdbcApp());
        bd.comoUsuario(usuarioId, personas.institucion(), () -> ejecuciones.guardar(e, List.of(a), List.of()));
        return new AfirmacionDeEjecucion(a.afirmacionId(), e.id(), expediente);
    }
}
