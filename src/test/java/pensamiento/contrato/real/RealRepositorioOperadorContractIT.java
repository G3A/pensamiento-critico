package pensamiento.contrato.real;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import pensamiento.contrato.RepositorioOperadorContract;
import pensamiento.nucleo.Institucion;
import pensamiento.nucleo.puertos.RepositorioInstituciones;
import pensamiento.nucleo.puertos.RepositorioOperador;
import pensamiento.testutil.BaseDatosDePrueba;
import pensamiento.web.operador.RepositorioOperadorJdbc;
import pensamiento.web.usuarios.RepositorioInstitucionesJdbc;

/**
 * Contra PostgreSQL como rol de aplicación y sin usuario ni institución fijados, como corre el operador: todo pasa por las
 * funciones SECURITY DEFINER de V12. El operador es el que app-prueba creó al arrancar con su secreto. Las instituciones
 * creadas se borran al final; la auditoría del operador queda, como en producción.
 */
@EnabledIfEnvironmentVariable(named = "CONTRACT_REAL", matches = "true")
class RealRepositorioOperadorContractIT extends RepositorioOperadorContract {

    private final BaseDatosDePrueba bd = new BaseDatosDePrueba();
    private final List<UUID> creadas = new ArrayList<>();

    @AfterAll
    void limpiar() {
        creadas.forEach(bd::borrarInstitucion);
    }

    @Override
    protected RepositorioOperador crearSut() {
        RepositorioOperadorJdbc real = new RepositorioOperadorJdbc(bd.jdbcApp());
        return new RepositorioOperador() {
            @Override public Optional<Operador> porNombre(String nombre) { return bd.sinContexto(() -> real.porNombre(nombre)); }
            @Override public boolean crearSiNoHay(String nombre, String hash) { return bd.sinContexto(() -> real.crearSiNoHay(nombre, hash)); }
            @Override public List<InstitucionDelOperador> instituciones() { return bd.sinContexto(real::instituciones); }
            @Override public UUID crearInstitucion(UUID operador, String nombre, String ruta) {
                UUID id = bd.sinContexto(() -> real.crearInstitucion(operador, nombre, ruta));
                creadas.add(id);
                return id;
            }
            @Override public boolean cambiarEstado(UUID operador, UUID institucion, Institucion.Estado estado) {
                return bd.sinContexto(() -> real.cambiarEstado(operador, institucion, estado));
            }
            @Override public UUID invitar(UUID operador, UUID institucion, String hash, Instant caduca) {
                return bd.sinContexto(() -> real.invitar(operador, institucion, hash, caduca));
            }
            @Override public List<InvitacionVigente> vigentes(UUID institucion) { return bd.sinContexto(() -> real.vigentes(institucion)); }
            @Override public boolean canjear(UUID invitacion) { return bd.sinContexto(() -> real.canjear(invitacion)); }
            @Override public List<Evento> auditoria(int limite) { return bd.sinContexto(() -> real.auditoria(limite)); }
        };
    }

    @Override
    protected RepositorioInstituciones instituciones() {
        RepositorioInstitucionesJdbc real = new RepositorioInstitucionesJdbc(bd.jdbcApp());
        return new RepositorioInstituciones() {
            @Override public Optional<UUID> activaPorRuta(String ruta) { return bd.sinContexto(() -> real.activaPorRuta(ruta)); }
            @Override public Optional<Institucion> porId(UUID id) { return bd.comoInstitucion(id, () -> real.porId(id)); }
        };
    }

    @Override
    protected UUID operador() {
        RepositorioOperador sut = crearSut();
        sut.crearSiNoHay("operador", "$argon2id$sin-uso");
        return sut.porNombre("operador").orElseThrow().id();
    }

    @Override
    protected void agregarCuenta(UUID institucion) {
        bd.crearUsuario(institucion, "administrador");
    }

    @Override
    protected Instant ahora() {
        return Instant.now();
    }
}
