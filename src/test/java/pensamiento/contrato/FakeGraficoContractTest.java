package pensamiento.contrato;

import pensamiento.nucleo.puertos.Grafico;
import pensamiento.testutil.fakes.FakeGrafico;

class FakeGraficoContractTest extends GraficoContract {

    @Override
    protected Grafico crearSut() {
        return new FakeGrafico();
    }
}
