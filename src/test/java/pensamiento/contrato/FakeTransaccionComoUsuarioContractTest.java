package pensamiento.contrato;

import java.util.UUID;

import pensamiento.nucleo.puertos.TransaccionComoUsuario;
import pensamiento.testutil.fakes.FakeTransaccionComoUsuario;

class FakeTransaccionComoUsuarioContractTest extends TransaccionComoUsuarioContract {

    private final FakeTransaccionComoUsuario fake = new FakeTransaccionComoUsuario();
    private final Persona persona = new Persona(UUID.randomUUID(), UUID.randomUUID());

    @Override
    protected Persona persona() {
        return persona;
    }

    @Override
    protected TransaccionComoUsuario transaccion() {
        return fake;
    }

    @Override
    protected String usuarioQueVeLaBase() {
        return fake.usuarioActual().map(UUID::toString).orElse("");
    }
}
