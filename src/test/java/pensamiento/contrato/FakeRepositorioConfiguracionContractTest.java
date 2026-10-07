package pensamiento.contrato;

import java.util.UUID;

import pensamiento.nucleo.puertos.RepositorioConfiguracion;
import pensamiento.testutil.fakes.FakeRepositorioConfiguracion;

class FakeRepositorioConfiguracionContractTest extends RepositorioConfiguracionContract {

    private final FakeRepositorioConfiguracion fake = new FakeRepositorioConfiguracion();
    private final Personas personas = new Personas(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

    @Override
    protected Personas personas() {
        return personas;
    }

    @Override
    protected RepositorioConfiguracion comoUsuario(UUID usuarioId) {
        return fake;
    }
}
