package pensamiento.nucleo.puertos;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** Puerto del tiempo: se inyecta para que las reglas con fechas sean deterministas en las pruebas. */
public interface Reloj {

    Instant ahora();

    ZoneId zona();

    default LocalDate hoy() {
        return ahora().atZone(zona()).toLocalDate();
    }
}
