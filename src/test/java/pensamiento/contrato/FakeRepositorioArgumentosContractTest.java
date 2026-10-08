package pensamiento.contrato;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import pensamiento.nucleo.puertos.RepositorioArgumentos;
import pensamiento.testutil.fakes.FakeRepositorioArgumentos;

class FakeRepositorioArgumentosContractTest extends RepositorioArgumentosContract {

    private final FakeRepositorioArgumentos fake = new FakeRepositorioArgumentos();
    private final Personas personas = new Personas(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

    @Override
    protected Personas personas() {
        return personas;
    }

    @Override
    protected RepositorioArgumentos comoUsuario(UUID usuarioId) {
        return fake;
    }

    @Override
    protected EjecucionConAfirmaciones dadaUnaEjecucionConAfirmaciones(UUID usuarioId, int cuantas) {
        List<UUID> afirmaciones = IntStream.range(0, cuantas).mapToObj(i -> UUID.randomUUID()).toList();
        return new EjecucionConAfirmaciones(UUID.randomUUID(), afirmaciones);
    }
}
