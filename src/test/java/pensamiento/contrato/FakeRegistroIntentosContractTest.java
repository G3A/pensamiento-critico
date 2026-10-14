package pensamiento.contrato;

import java.util.UUID;

import pensamiento.nucleo.puertos.RegistroIntentos;
import pensamiento.testutil.fakes.FakeRegistroIntentos;

class FakeRegistroIntentosContractTest extends RegistroIntentosContract {

    @Override
    protected RegistroIntentos crearSut() {
        return new FakeRegistroIntentos();
    }

    @Override
    protected UUID institucion() {
        return UUID.fromString("0192f000-0000-7000-8000-000000000001");
    }

    @Override
    protected UUID otraInstitucion() {
        return UUID.fromString("0192f000-0000-7000-8000-000000000002");
    }
}
