package pensamiento.web;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

import pensamiento.nucleo.puertos.Reloj;

/**
 * Reloj real del sistema en la zona configurada (APP_ZONA). Si la instalación lo permite (APP_RELOJ_AJUSTABLE=true, solo
 * para pruebas de aceptación y demostraciones), el administrador puede adelantarlo para ver vencer una revisión del
 * Diario sin esperar meses; por defecto no se puede mover.
 */
public final class RelojDelSistema implements Reloj {

    private final Clock base;
    private final boolean ajustable;
    private final AtomicReference<Duration> adelanto = new AtomicReference<>(Duration.ZERO);

    public RelojDelSistema(ZoneId zona, boolean ajustable) {
        this.base = Clock.system(zona);
        this.ajustable = ajustable;
    }

    @Override
    public Instant ahora() {
        return base.instant().plus(adelanto.get());
    }

    @Override
    public ZoneId zona() {
        return base.getZone();
    }

    public boolean ajustable() {
        return ajustable;
    }

    public Duration adelanto() {
        return adelanto.get();
    }

    /** Fija cuánto va adelantado respecto del reloj real (0 lo devuelve a la hora real). */
    public void adelantar(Duration cuanto) {
        if (!ajustable) {
            throw new IllegalStateException("Este reloj no se puede ajustar: APP_RELOJ_AJUSTABLE no está activo.");
        }
        if (cuanto.isNegative()) {
            throw new IllegalArgumentException("El reloj solo se adelanta.");
        }
        adelanto.set(cuanto);
    }
}
