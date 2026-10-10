package pensamiento.tecnicas.f8;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * SM-2 (Wozniak 1987), la regla de T49 · Repetición espaciada para un concepto. Un acierto es calidad 5 y un error, 2. Con
 * acierto, el intervalo es 1 día, luego 6 y luego el anterior por la facilidad que había, redondeado (la mitad hacia arriba);
 * con error, vuelve a 1 día y a 0 repeticiones. La facilidad cambia en 0,1 − (5 − q) × (0,08 + (5 − q) × 0,02) y nunca
 * baja de 1,3. Reglas en docs/ejemplos/T49.md.
 */
public final class Sm2 {

    public static final BigDecimal FACILIDAD_MINIMA = new BigDecimal("1.30");
    static final int CALIDAD_ACIERTO = 5;
    static final int CALIDAD_ERROR = 2;

    /**
     * El estado de un concepto después de sus repasos.
     *
     * @param intervalo días hasta el próximo repaso; 0 si nunca se repasó
     * @param proximo   la fecha del próximo repaso; nula si nunca se repasó
     * @param repasos   cuántos repasos lleva
     */
    public record Estado(int repeticiones, BigDecimal facilidad, int intervalo, LocalDate proximo, int repasos) {
    }

    private Sm2() {
    }

    public static Estado inicial(BigDecimal facilidad) {
        return new Estado(0, facilidad.setScale(2, RoundingMode.HALF_UP), 0, null, 0);
    }

    /** Aplica un repaso hecho en esa fecha. */
    public static Estado responder(Estado e, boolean acierto, LocalDate fecha) {
        int q = acierto ? CALIDAD_ACIERTO : CALIDAD_ERROR;
        int repeticiones;
        int intervalo;
        if (q >= 3) {
            if (e.repeticiones() == 0) {
                intervalo = 1;
            } else if (e.repeticiones() == 1) {
                intervalo = 6;
            } else {
                intervalo = new BigDecimal(e.intervalo()).multiply(e.facilidad()).setScale(0, RoundingMode.HALF_UP).intValueExact();
            }
            repeticiones = e.repeticiones() + 1;
        } else {
            repeticiones = 0;
            intervalo = 1;
        }
        BigDecimal falta = new BigDecimal(5 - q);
        BigDecimal cambio = new BigDecimal("0.1").subtract(falta.multiply(new BigDecimal("0.08").add(falta.multiply(new BigDecimal("0.02")))));
        BigDecimal facilidad = e.facilidad().add(cambio).max(FACILIDAD_MINIMA).setScale(2, RoundingMode.HALF_UP);
        return new Estado(repeticiones, facilidad, intervalo, fecha.plusDays(intervalo), e.repasos() + 1);
    }
}
