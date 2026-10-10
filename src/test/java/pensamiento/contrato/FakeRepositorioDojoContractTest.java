package pensamiento.contrato;

import java.util.UUID;

import pensamiento.nucleo.puertos.RepositorioDojo;
import pensamiento.testutil.fakes.FakeRepositorioDojo;

class FakeRepositorioDojoContractTest extends RepositorioDojoContract {

    private final FakeRepositorioDojo fake = new FakeRepositorioDojo();
    private final Personas personas = new Personas(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

    @Override
    protected Personas personas() {
        return personas;
    }

    @Override
    protected RepositorioDojo comoUsuario(UUID usuarioId) {
        return fake;
    }
}
