package pensamiento.contrato.real;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import pensamiento.contrato.RepositorioInstitucionesContract;
import pensamiento.nucleo.Institucion;
import pensamiento.nucleo.puertos.RepositorioInstituciones;
import pensamiento.testutil.BaseDatosDePrueba;
import pensamiento.web.usuarios.RepositorioInstitucionesJdbc;

/**
 * Contra PostgreSQL como rol de aplicación: la ruta se resuelve sin institución fijada (función SECURITY DEFINER) y la
 * lectura por id corre con la institución fijada, como en una sesión. Las instituciones sembradas se borran al final.
 */
@EnabledIfEnvironmentVariable(named = "CONTRACT_REAL", matches = "true")
class RealRepositorioInstitucionesContractIT extends RepositorioInstitucionesContract {

    private final BaseDatosDePrueba bd = new BaseDatosDePrueba();
    private final List<UUID> sembradas = new ArrayList<>();

    @AfterAll
    void limpiar() {
        sembradas.forEach(bd::borrarInstitucion);
    }

    @Override
    protected UUID sembrar(String nombre, String ruta, Institucion.Estado estado) {
        UUID id = bd.crearInstitucion(nombre, ruta);
        bd.jdbcAdmin().sql("UPDATE institucion SET estado = :e WHERE id = :i").param("e", estado.enBaseDeDatos()).param("i", id).update();
        sembradas.add(id);
        return id;
    }

    @Override
    protected RepositorioInstituciones crearSut() {
        RepositorioInstitucionesJdbc real = new RepositorioInstitucionesJdbc(bd.jdbcApp());
        return new RepositorioInstituciones() {
            @Override public Optional<UUID> activaPorRuta(String ruta) {
                return bd.sinContexto(() -> real.activaPorRuta(ruta));
            }
            @Override public Optional<Institucion> porId(UUID id) {
                return bd.comoInstitucion(id, () -> real.porId(id));
            }
        };
    }
}
