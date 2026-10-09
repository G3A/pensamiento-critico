package pensamiento.contrato;

import java.util.UUID;

import pensamiento.nucleo.puertos.Biblioteca;
import pensamiento.testutil.fakes.FakeBiblioteca;

class FakeBibliotecaContractTest extends BibliotecaContract {

    private final FakeBiblioteca fake = new FakeBiblioteca();
    private final Personas personas = new Personas(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

    @Override
    protected Personas personas() {
        return personas;
    }

    @Override
    protected Biblioteca comoUsuario(UUID usuarioId) {
        return fake;
    }
}
