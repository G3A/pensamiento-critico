package pensamiento.contrato;

import java.util.UUID;

import pensamiento.nucleo.puertos.RegistroIdentificadores;
import pensamiento.testutil.fakes.FakeRegistroIdentificadores;

class FakeRegistroIdentificadoresContractTest extends RegistroIdentificadoresContract {

    private final FakeRegistroIdentificadores fake = new FakeRegistroIdentificadores();
    private final Personas personas = new Personas(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

    @Override
    protected Personas personas() {
        return personas;
    }

    @Override
    protected RegistroIdentificadores comoUsuario(UUID usuarioId) {
        return fake;
    }

    @Override
    protected UUID expedienteDe(UUID usuarioId) {
        UUID id = UUID.randomUUID();
        fake.existe(id, usuarioId);
        return id;
    }

    @Override
    protected UUID prediccionDe(UUID usuarioId) {
        UUID id = UUID.randomUUID();
        fake.existe(id, usuarioId);
        return id;
    }

    @Override
    protected UUID ejecucionDe(UUID usuarioId) {
        UUID id = UUID.randomUUID();
        fake.existe(id, usuarioId);
        return id;
    }

    @Override
    protected java.util.List<UUID> documentoFragmentoFuenteYEvidenciaDe(UUID usuarioId) {
        java.util.List<UUID> ids = java.util.List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        ids.forEach(id -> fake.existe(id, usuarioId));
        return ids;
    }

    @Override
    protected java.util.List<UUID> sesionTurnoYCambioDe(UUID usuarioId) {
        java.util.List<UUID> ids = java.util.List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        ids.forEach(id -> fake.existe(id, usuarioId));
        return ids;
    }
}
