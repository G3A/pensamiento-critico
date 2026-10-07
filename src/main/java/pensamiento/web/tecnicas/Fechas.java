package pensamiento.web.tecnicas;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Fechas para leer en pantalla, en español y en la zona de la instalación. */
public final class Fechas {

    private static final Locale ES = Locale.forLanguageTag("es-419");
    private static final DateTimeFormatter CORTA = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", ES);
    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("d MMM yyyy", ES);

    private Fechas() {
    }

    public static String corta(Instant instante, ZoneId zona) {
        return CORTA.format(instante.atZone(zona));
    }

    public static String dia(Instant instante, ZoneId zona) {
        return DIA.format(instante.atZone(zona));
    }
}
