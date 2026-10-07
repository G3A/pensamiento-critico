package pensamiento.contrato.real;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import pensamiento.contrato.RepositorioEjecucionContract;
import pensamiento.expediente.RepositorioEjecucionJdbc;
import pensamiento.nucleo.AfirmacionConRol;
import pensamiento.nucleo.Ejecucion;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Pendiente;
import pensamiento.nucleo.PendienteGuardado;
import pensamiento.nucleo.puertos.RepositorioEjecucion;
import pensamiento.testutil.BaseDatosDePrueba;

/** Contra el PostgreSQL del compose, como rol de aplicación bajo RLS, con una institución de prueba que se borra al final. */
@EnabledIfEnvironmentVariable(named = "CONTRACT_REAL", matches = "true")
class RealRepositorioEjecucionContractIT extends RepositorioEjecucionContract {

    private static final BaseDatosDePrueba bd = new BaseDatosDePrueba();
    private static Personas personas;

    @BeforeAll
    static void sembrar() {
        UUID institucion = bd.crearInstitucion("contrato-ejecucion-" + UUID.randomUUID());
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
    protected UUID expedienteDe(UUID usuarioId) {
        return bd.crearExpediente(personas.institucion(), usuarioId, "La segunda sucursal de la panadería");
    }

    @Override
    protected RepositorioEjecucion comoUsuario(UUID usuarioId) {
        RepositorioEjecucionJdbc real = new RepositorioEjecucionJdbc(bd.jdbcApp());
        UUID inst = personas.institucion();
        return new RepositorioEjecucion() {
            @Override public Ejecucion guardar(Ejecucion e, List<AfirmacionConRol> a, List<Pendiente> p) { return bd.comoUsuario(usuarioId, inst, () -> real.guardar(e, a, p)); }
            @Override public Optional<Ejecucion> porId(UUID u, UUID id) { return bd.comoUsuario(usuarioId, inst, () -> real.porId(u, id)); }
            @Override public List<Ejecucion> porTecnica(UUID u, IdTecnica t) { return bd.comoUsuario(usuarioId, inst, () -> real.porTecnica(u, t)); }
            @Override public List<AfirmacionConRol> afirmacionesDe(UUID u, UUID e) { return bd.comoUsuario(usuarioId, inst, () -> real.afirmacionesDe(u, e)); }
            @Override public List<PendienteGuardado> pendientes(UUID u) { return bd.comoUsuario(usuarioId, inst, () -> real.pendientes(u)); }
            @Override public List<Ejecucion> porExpediente(UUID u, UUID x) { return bd.comoUsuario(usuarioId, inst, () -> real.porExpediente(u, x)); }
            @Override public List<Ejecucion> recientes(UUID u, int l) { return bd.comoUsuario(usuarioId, inst, () -> real.recientes(u, l)); }
            @Override public boolean asociar(UUID u, UUID e, Optional<UUID> x) { return bd.comoUsuario(usuarioId, inst, () -> real.asociar(u, e, x)); }
        };
    }
}
