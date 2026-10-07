package pensamiento.contrato;

import java.util.UUID;

import pensamiento.nucleo.puertos.RepositorioEjecucion;
import pensamiento.testutil.fakes.FakeRepositorioEjecucion;

class FakeRepositorioEjecucionContractTest extends RepositorioEjecucionContract {

    private final FakeRepositorioEjecucion fake = new FakeRepositorioEjecucion();
    private final Personas personas = new Personas(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

    @Override
    protected Personas personas() {
        return personas;
    }

    @Override
    protected RepositorioEjecucion comoUsuario(UUID usuarioId) {
        return fake;
    }

    @Override
    protected UUID expedienteDe(UUID usuarioId) {
        return UUID.randomUUID();
    }
}
