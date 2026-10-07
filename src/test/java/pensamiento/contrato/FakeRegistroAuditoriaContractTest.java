package pensamiento.contrato;

import java.util.UUID;

import pensamiento.nucleo.puertos.RegistroAuditoria;
import pensamiento.testutil.fakes.FakeRegistroAuditoria;

class FakeRegistroAuditoriaContractTest extends RegistroAuditoriaContract {

    private final FakeRegistroAuditoria fake = new FakeRegistroAuditoria();
    private final Personas personas = new Personas(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

    @Override
    protected Personas personas() {
        return personas;
    }

    @Override
    protected RegistroAuditoria comoUsuario(UUID usuarioId) {
        return fake.vistaDe(usuarioId);
    }
}
