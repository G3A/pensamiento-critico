package pensamiento.contrato;

import pensamiento.nucleo.puertos.ColaTrabajos;
import pensamiento.testutil.fakes.FakeColaTrabajos;

class FakeColaTrabajosContractTest extends ColaTrabajosContract {

    private final FakeColaTrabajos fake = new FakeColaTrabajos();

    @Override
    protected ColaTrabajos cola() {
        return fake;
    }
}
