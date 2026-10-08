package pensamiento.contrato;

import pensamiento.nucleo.puertos.RepositorioEsquemas;
import pensamiento.testutil.fakes.FakeRepositorioEsquemas;

class FakeRepositorioEsquemasContractTest extends RepositorioEsquemasContract {

    @Override
    protected RepositorioEsquemas crearSut() {
        return new FakeRepositorioEsquemas();
    }
}
