package pensamiento.contrato;

import java.util.UUID;

import pensamiento.nucleo.puertos.RepositorioExpediente;
import pensamiento.testutil.fakes.FakeRepositorioExpediente;

class FakeRepositorioExpedienteContractTest extends RepositorioExpedienteContract {

    private final FakeRepositorioExpediente fake = new FakeRepositorioExpediente();
    private final Personas personas = new Personas(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

    @Override
    protected Personas personas() {
        return personas;
    }

    @Override
    protected RepositorioExpediente comoUsuario(UUID usuarioId) {
        return fake;
    }
}
