package pensamiento.testutil.builders;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import pensamiento.nucleo.Contexto;
import pensamiento.testutil.fakes.FakeReloj;

/** Contextos de ejecución para pruebas: reloj fijo, sin IA e identificadores en secuencia legible. */
public final class Contextos {

    public static final UUID INSTITUCION = UUID.fromString("00000000-0000-7000-8000-000000000001");
    public static final UUID DUENA_DE_LA_PANADERIA = UUID.fromString("00000000-0000-7000-8000-0000000000a1");

    private Contextos() {
    }

    public static Contexto sinIa() {
        return sinIa(DUENA_DE_LA_PANADERIA);
    }

    public static Contexto sinIa(UUID usuario) {
        AtomicLong siguiente = new AtomicLong(1);
        return new Contexto(usuario, INSTITUCION, Optional.empty(), new FakeReloj(), Optional.empty(),
                () -> new UUID(0x0000000000007000L, 0x8000000000000000L | siguiente.getAndIncrement()));
    }
}
