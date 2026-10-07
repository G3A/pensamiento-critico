package pensamiento.web.tecnicas;

import pensamiento.nucleo.IdTecnica;

/**
 * En qué hito llega la pestaña Usar de cada técnica, según la tabla "Por hito" de la sección 9. Las técnicas
 * sin ejecutor muestran su ficha "Qué es" y dicen cuándo llegan.
 */
public final class HitosDeTecnicas {

    private HitosDeTecnicas() {
    }

    public static int hito(IdTecnica id) {
        int n = id.numero();
        if (n == 28) {
            return 1;
        }
        if (n == 1 || n == 2 || n == 6 || n == 13) {
            return 2;
        }
        if (n == 22 || n == 34 || n == 3 || n == 4 || n == 5 || n == 7 || (n >= 14 && n <= 18)) {
            return 3;
        }
        if ((n >= 24 && n <= 33) || (n >= 40 && n <= 44)) {
            return 4;
        }
        if ((n >= 8 && n <= 12) || (n >= 35 && n <= 39)) {
            return 5;
        }
        if (n == 19 || n == 20 || n == 21 || n == 23) {
            return 6;
        }
        return 7;
    }
}
