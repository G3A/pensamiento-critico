package pensamiento.contrato.real;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import pensamiento.contrato.RepositorioSesionesContract;
import pensamiento.expediente.RepositorioExpedienteJdbc;
import pensamiento.expediente.RepositorioSesionesJdbc;
import pensamiento.nucleo.Ejecucion;
import pensamiento.nucleo.Expediente;
import pensamiento.nucleo.SesionConsejero;
import pensamiento.nucleo.TurnoConsejero;
import pensamiento.nucleo.Uuid7;
import pensamiento.nucleo.puertos.RepositorioSesiones;
import pensamiento.testutil.BaseDatosDePrueba;

/** Contra el PostgreSQL del compose, como rol de aplicación bajo RLS, con una institución de prueba que se borra al final. */
@EnabledIfEnvironmentVariable(named = "CONTRACT_REAL", matches = "true")
class RealRepositorioSesionesContractIT extends RepositorioSesionesContract {

    private static final BaseDatosDePrueba bd = new BaseDatosDePrueba();
    private static Personas personas;

    @BeforeAll
    static void sembrar() {
        UUID institucion = bd.crearInstitucion("contrato-sesiones-" + UUID.randomUUID());
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
    protected UUID nuevoId(Instant cuando) {
        return Uuid7.en(cuando);
    }

    @Override
    protected UUID dadoUnExpediente(UUID usuarioId) {
        RepositorioExpedienteJdbc expedientes = new RepositorioExpedienteJdbc(bd.jdbcApp());
        Expediente e = new Expediente(Uuid7.en(AHORA), usuarioId, personas.institucion(), "Consejero: prueba", Optional.empty(), Expediente.Estado.ABIERTO, AHORA);
        return bd.comoUsuario(usuarioId, personas.institucion(), () -> expedientes.guardar(e)).id();
    }

    @Override
    protected RepositorioSesiones comoUsuario(UUID usuarioId) {
        RepositorioSesionesJdbc real = new RepositorioSesionesJdbc(bd.jdbcApp());
        UUID inst = personas.institucion();
        return new RepositorioSesiones() {
            @Override
            public void crear(SesionConsejero s) {
                bd.comoUsuario(usuarioId, inst, () -> {
                    real.crear(s);
                    return null;
                });
            }

            @Override
            public Optional<SesionConsejero> porId(UUID u, UUID id) {
                return bd.comoUsuario(usuarioId, inst, () -> real.porId(u, id));
            }

            @Override
            public List<SesionConsejero> deUsuario(UUID u) {
                return bd.comoUsuario(usuarioId, inst, () -> real.deUsuario(u));
            }

            @Override
            public List<TurnoConsejero> turnos(UUID u, UUID s) {
                return bd.comoUsuario(usuarioId, inst, () -> real.turnos(u, s));
            }

            @Override
            public void agregarTurno(UUID u, UUID i, TurnoConsejero t) {
                bd.comoUsuario(usuarioId, inst, () -> {
                    real.agregarTurno(u, i, t);
                    return null;
                });
            }

            @Override
            public void completarTurno(UUID u, UUID t, String texto, TurnoConsejero.Origen origen, int intentos, Optional<Ejecucion.RegistroModelo> modelo) {
                bd.comoUsuario(usuarioId, inst, () -> {
                    real.completarTurno(u, t, texto, origen, intentos, modelo);
                    return null;
                });
            }

            @Override
            public void proponerElemento(UUID u, UUID t, String elemento, String porque) {
                bd.comoUsuario(usuarioId, inst, () -> {
                    real.proponerElemento(u, t, elemento, porque);
                    return null;
                });
            }

            @Override
            public void adoptarElemento(UUID u, UUID t) {
                bd.comoUsuario(usuarioId, inst, () -> {
                    real.adoptarElemento(u, t);
                    return null;
                });
            }

            @Override
            public void pedirCierre(UUID u, UUID s) {
                bd.comoUsuario(usuarioId, inst, () -> {
                    real.pedirCierre(u, s);
                    return null;
                });
            }

            @Override
            public void asociar(UUID u, UUID s, Optional<UUID> e) {
                bd.comoUsuario(usuarioId, inst, () -> {
                    real.asociar(u, s, e);
                    return null;
                });
            }

            @Override
            public void cerrar(UUID u, UUID s, Optional<String> r, Optional<Integer> c, Optional<UUID> e, Instant cuando) {
                bd.comoUsuario(usuarioId, inst, () -> {
                    real.cerrar(u, s, r, c, e, cuando);
                    return null;
                });
            }

            @Override
            public void restaurar(UUID u, UUID i, SesionConsejero s, List<TurnoConsejero> t) {
                bd.comoUsuario(usuarioId, inst, () -> {
                    real.restaurar(u, i, s, t);
                    return null;
                });
            }
        };
    }
}
