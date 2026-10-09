package pensamiento.contrato;

import java.time.Instant;
import java.util.UUID;

import pensamiento.nucleo.puertos.RepositorioSesiones;
import pensamiento.testutil.fakes.FakeRepositorioSesiones;

class FakeRepositorioSesionesContractTest extends RepositorioSesionesContract {

    private final FakeRepositorioSesiones fake = new FakeRepositorioSesiones();
    private final Personas personas = new Personas(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

    @Override
    protected Personas personas() {
        return personas;
    }

    @Override
    protected RepositorioSesiones comoUsuario(UUID usuarioId) {
        return fake;
    }

    @Override
    protected UUID dadoUnExpediente(UUID usuarioId) {
        return UUID.randomUUID();
    }

    @Override
    protected UUID nuevoId(Instant cuando) {
        return UUID.randomUUID();
    }
}
