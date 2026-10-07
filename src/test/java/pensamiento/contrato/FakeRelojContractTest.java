package pensamiento.contrato;

import pensamiento.nucleo.puertos.Reloj;
import pensamiento.testutil.fakes.FakeReloj;

class FakeRelojContractTest extends RelojContract {

    @Override
    protected Reloj crearSut() {
        return new FakeReloj();
    }
}
