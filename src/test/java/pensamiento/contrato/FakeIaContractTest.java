package pensamiento.contrato;

import pensamiento.nucleo.puertos.Ia;
import pensamiento.testutil.fakes.FakeIa;

class FakeIaContractTest extends IaContract {

    @Override
    protected Ia disponible() {
        FakeIa fake = new FakeIa();
        fake.programarRespuesta("Azul.");
        fake.programarRespuesta("Hola.");
        fake.programarRespuesta("Hola otra vez.");
        return fake;
    }

    @Override
    protected Ia noDisponible() {
        FakeIa fake = new FakeIa();
        fake.apagar();
        return fake;
    }

    @Override
    protected Ia lenta() {
        FakeIa fake = new FakeIa();
        fake.hacerLento();
        return fake;
    }

    @Override
    protected Ia conJsonInvalido() {
        FakeIa fake = new FakeIa();
        fake.programarClasificacionCruda("esto no es json {");
        return fake;
    }
}
