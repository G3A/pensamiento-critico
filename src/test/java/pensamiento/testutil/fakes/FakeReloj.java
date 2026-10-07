package pensamiento.testutil.fakes;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import pensamiento.nucleo.puertos.Reloj;

/** Reloj fijo y avanzable. Certificado por FakeRelojContractTest. */
public final class FakeReloj implements Reloj {

    public static final Instant INSTANTE_FIJO = Instant.parse("2026-10-07T15:00:00Z");
    public static final ZoneId ZONA = ZoneId.of("America/Bogota");

    private Instant ahora;
    private final ZoneId zona;

    public FakeReloj() {
        this(INSTANTE_FIJO, ZONA);
    }

    public FakeReloj(Instant ahora, ZoneId zona) {
        this.ahora = ahora;
        this.zona = zona;
    }

    public void avanzar(Duration cuanto) {
        ahora = ahora.plus(cuanto);
    }

    @Override
    public Instant ahora() {
        return ahora;
    }

    @Override
    public ZoneId zona() {
        return zona;
    }
}
