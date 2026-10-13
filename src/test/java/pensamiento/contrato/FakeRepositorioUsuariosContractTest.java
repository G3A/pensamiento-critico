package pensamiento.contrato;

import java.util.UUID;

import pensamiento.nucleo.puertos.RepositorioUsuarios;
import pensamiento.testutil.fakes.FakeRepositorioUsuarios;

class FakeRepositorioUsuariosContractTest extends RepositorioUsuariosContract {

    private static final UUID INSTITUCION = UUID.fromString("0192f000-0000-7000-8000-000000000001");

    @Override
    protected RepositorioUsuarios crearSut() {
        return new FakeRepositorioUsuarios();
    }

    @Override
    protected UUID institucionDePrueba() {
        return INSTITUCION;
    }
}
