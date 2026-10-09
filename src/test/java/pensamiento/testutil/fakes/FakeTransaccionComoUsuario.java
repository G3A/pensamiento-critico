package pensamiento.testutil.fakes;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import pensamiento.nucleo.puertos.TransaccionComoUsuario;

/**
 * Fake de la transacción como persona: corre la acción en el mismo hilo y recuerda quién está fijado mientras dura.
 * Certificado por FakeTransaccionComoUsuarioContractTest.
 */
public final class FakeTransaccionComoUsuario implements TransaccionComoUsuario {

    private final ThreadLocal<UUID> actual = new ThreadLocal<>();

    @Override
    public <T> T ejecutar(UUID usuarioId, UUID institucionId, Supplier<T> accion) {
        UUID previo = actual.get();
        actual.set(usuarioId);
        try {
            return accion.get();
        } finally {
            if (previo == null) {
                actual.remove();
            } else {
                actual.set(previo);
            }
        }
    }

    /** El usuario fijado en este hilo, si hay una transacción abierta. */
    public Optional<UUID> usuarioActual() {
        return Optional.ofNullable(actual.get());
    }
}
