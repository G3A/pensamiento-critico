package pensamiento.contrato;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import pensamiento.nucleo.Institucion;
import pensamiento.nucleo.puertos.RepositorioInstituciones;

/** Contrato de instituciones: la ruta resuelve solo instituciones activas y con forma válida; por id se lee la propia. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class RepositorioInstitucionesContract {

    protected abstract RepositorioInstituciones crearSut();

    /** Siembra una institución con esa ruta y estado (ruta única por corrida) y devuelve su id. */
    protected abstract UUID sembrar(String nombre, String ruta, Institucion.Estado estado);

    /** Una ruta válida que no se repite entre corridas: prefijo más 8 caracteres al azar. */
    protected static String rutaNueva(String prefijo) {
        return prefijo + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void la_ruta_de_una_institucion_activa_da_su_id() {
        String ruta = rutaNueva("colegio-norte");
        UUID id = sembrar("Colegio Norte", ruta, Institucion.Estado.ACTIVA);
        assertThat(crearSut().activaPorRuta(ruta)).contains(id);
    }

    @Test
    void una_institucion_suspendida_no_se_resuelve_por_su_ruta() {
        String ruta = rutaNueva("colegio-sur");
        sembrar("Colegio Sur", ruta, Institucion.Estado.SUSPENDIDA);
        assertThat(crearSut().activaPorRuta(ruta)).isEmpty();
    }

    @Test
    void una_ruta_que_no_existe_o_no_tiene_forma_de_ruta_no_resuelve_nada() {
        RepositorioInstituciones sut = crearSut();
        assertThat(sut.activaPorRuta(rutaNueva("nadie"))).isEmpty();
        assertThat(sut.activaPorRuta("Colegio Norte")).isEmpty();
        assertThat(sut.activaPorRuta("../etc")).isEmpty();
        assertThat(sut.activaPorRuta("ab")).isEmpty();
        assertThat(sut.activaPorRuta(null)).isEmpty();
    }

    @Test
    void por_id_se_lee_la_institucion_con_su_nombre_ruta_y_estado() {
        String ruta = rutaNueva("liceo");
        UUID id = sembrar("Liceo Central", ruta, Institucion.Estado.ACTIVA);
        assertThat(crearSut().porId(id)).contains(new Institucion(id, "Liceo Central", ruta, Institucion.Estado.ACTIVA));
    }
}
