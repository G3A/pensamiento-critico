package pensamiento.contrato;

import java.util.UUID;

import pensamiento.nucleo.puertos.RepositorioBorradores;
import pensamiento.testutil.fakes.FakeRepositorioBorradores;

class FakeRepositorioBorradoresContractTest extends RepositorioBorradoresContract {

    private final FakeRepositorioBorradores fake = new FakeRepositorioBorradores();
    private final Personas personas = new Personas(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

    @Override
    protected Personas personas() {
        return personas;
    }

    @Override
    protected RepositorioBorradores comoUsuario(UUID usuarioId) {
        return fake;
    }
}
