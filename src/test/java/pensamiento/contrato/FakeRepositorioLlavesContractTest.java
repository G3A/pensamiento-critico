package pensamiento.contrato;

import java.util.UUID;

import pensamiento.nucleo.Titular;
import pensamiento.nucleo.puertos.RepositorioLlaves;
import pensamiento.testutil.fakes.FakeRepositorioLlaves;

class FakeRepositorioLlavesContractTest extends RepositorioLlavesContract {

    private static final UUID INSTITUCION = UUID.fromString("0192f000-0000-7000-8000-000000000001");
    private final Titular a = Titular.cuenta(UUID.randomUUID(), INSTITUCION);
    private final Titular b = Titular.cuenta(UUID.randomUUID(), INSTITUCION);
    private final Titular operador = Titular.operador(UUID.randomUUID());

    @Override
    protected RepositorioLlaves crearSut() {
        return new FakeRepositorioLlaves().conTitular(a).conTitular(b).conTitular(operador);
    }

    @Override
    protected Titular cuentaA() {
        return a;
    }

    @Override
    protected Titular cuentaB() {
        return b;
    }

    @Override
    protected Titular operador() {
        return operador;
    }
}
