package pensamiento.contrato;

import java.util.UUID;

import pensamiento.nucleo.puertos.RepositorioUsuarios;
import pensamiento.testutil.fakes.FakeRepositorioUsuarios;

class FakeRepositorioUsuariosContractTest extends RepositorioUsuariosContract {

    private FakeRepositorioUsuarios fake;

    @Override
    protected RepositorioUsuarios crearSut() {
        fake = new FakeRepositorioUsuarios();
        return fake;
    }

    @Override
    protected UUID institucionDePrueba() {
        if (fake == null) {
            fake = new FakeRepositorioUsuarios();
        }
        return fake.crearInstitucion("prueba");
    }
}
