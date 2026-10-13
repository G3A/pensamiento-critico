package pensamiento.contrato;

import java.util.UUID;

import pensamiento.nucleo.Institucion;
import pensamiento.nucleo.puertos.RepositorioInstituciones;
import pensamiento.testutil.fakes.FakeRepositorioInstituciones;

class FakeRepositorioInstitucionesContractTest extends RepositorioInstitucionesContract {

    private final FakeRepositorioInstituciones fake = new FakeRepositorioInstituciones();

    @Override
    protected RepositorioInstituciones crearSut() {
        return fake;
    }

    @Override
    protected UUID sembrar(String nombre, String ruta, Institucion.Estado estado) {
        return fake.agregar(new Institucion(UUID.randomUUID(), nombre, ruta, estado)).id();
    }
}
