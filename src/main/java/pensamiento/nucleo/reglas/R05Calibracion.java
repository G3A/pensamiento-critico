package pensamiento.nucleo.reglas;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * R05 · Confianza y calibración (sección 5b): la confianza es el porcentaje que la persona declara sobre una predicción;
 * no sale de R02 ni del modelo. Con las resueltas calcula el puntaje (Brier, el promedio de (confianza − resultado)², o
 * logarítmico) y la curva de calibración por tramos de confianza, con el aviso provisional si un tramo tiene menos
 * predicciones que el umbral (corrección 13). Las reglas y los ejemplos calculados a mano están en docs/ejemplos/T25.md.
 */
public final class R05Calibracion {

    public enum Puntaje {
        BRIER, LOGARITMICO;

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    /**
     * @param anchoTramo     10 o 20 puntos
     * @param umbral         predicciones por tramo por debajo de las cuales la curva es provisional
     * @param horizonteMeses meses hacia atrás desde hoy; 0 son todas
     */
    public record Parametros(int anchoTramo, int umbral, Puntaje puntaje, int horizonteMeses) {
        public Parametros {
            if (anchoTramo != 10 && anchoTramo != 20) {
                throw new IllegalArgumentException("Los tramos son de 10 o de 20 puntos");
            }
        }

        /** Lo que usa el tablero del Diario: tramos de 10, umbral 10, Brier, todas. */
        public static Parametros diario() {
            return new Parametros(10, 10, Puntaje.BRIER, 0);
        }
    }

    /** Una predicción: la confianza declarada, si se cumplió (vacío si sigue sin resolver) y la fecha en que se resolvió. */
    public record Prediccion(int confianza, Optional<Boolean> seCumplio, Optional<LocalDate> fecha) {
        public Prediccion {
            if (confianza < 0 || confianza > 100) {
                throw new IllegalArgumentException("La confianza va de 0 a 100: " + confianza);
            }
        }

        public static Prediccion resuelta(int confianza, boolean seCumplio) {
            return new Prediccion(confianza, Optional.of(seCumplio), Optional.empty());
        }
    }

    /**
     * Un tramo de la curva con al menos una resuelta.
     *
     * @param hasta             el último valor del tramo, incluido (79 para [60, 80); 100 en el último)
     * @param confianzaMedia    promedio declarado, entero, la mitad hacia arriba
     * @param porcentajeCumplido de las resueltas del tramo, cuántas se cumplieron, en porcentaje entero
     */
    public record Tramo(int desde, int hasta, int n, int confianzaMedia, int porcentajeCumplido, boolean provisional, Optional<String> aviso) {
    }

    /**
     * @param puntaje           con dos decimales; vacío si no hay ninguna resuelta
     * @param resueltas         las que cuentan, dentro del horizonte
     * @param sinResolver       las que todavía no tienen resultado
     * @param fueraDeHorizonte  resueltas que el horizonte dejó afuera
     */
    public record Calibracion(Puntaje tipo, Optional<BigDecimal> puntaje, int resueltas, int sinResolver, int fueraDeHorizonte,
                              List<Tramo> tramos, List<String> avisos) {
        public Calibracion {
            tramos = List.copyOf(tramos);
            avisos = List.copyOf(avisos);
        }

        /** "0,17"; vacío si no hay resueltas. */
        public Optional<String> puntajeTexto() {
            return puntaje.map(R05Calibracion::decimal);
        }

        /** El tramo donde cae una confianza, si tiene resueltas: para el aviso por historial del Diario. */
        public Optional<Tramo> tramoDe(int confianza) {
            return tramos.stream().filter(t -> confianza >= t.desde() && confianza <= t.hasta()).findFirst();
        }
    }

    /** Diferencia, en puntos, a partir de la cual un tramo lleva aviso (más de 10). */
    static final int DIFERENCIA_AVISO = 10;

    private R05Calibracion() {
    }

    public static Calibracion calcular(List<Prediccion> predicciones, Parametros p, LocalDate hoy) {
        LocalDate limite = p.horizonteMeses() == 0 ? null : hoy.minusMonths(p.horizonteMeses());
        List<Prediccion> cuentan = new ArrayList<>();
        int sinResolver = 0;
        int fuera = 0;
        for (Prediccion x : predicciones) {
            if (x.seCumplio().isEmpty()) {
                sinResolver++;
            } else if (limite != null && x.fecha().isPresent() && x.fecha().get().isBefore(limite)) {
                fuera++;
            } else {
                cuentan.add(x);
            }
        }
        Optional<BigDecimal> puntaje = cuentan.isEmpty() ? Optional.empty()
                : Optional.of(p.puntaje() == Puntaje.BRIER ? brier(cuentan) : logaritmico(cuentan));
        List<Tramo> tramos = new ArrayList<>();
        List<String> avisos = new ArrayList<>();
        int cuantosTramos = 100 / p.anchoTramo();
        for (int t = 0; t < cuantosTramos; t++) {
            int desde = t * p.anchoTramo();
            int hasta = t == cuantosTramos - 1 ? 100 : desde + p.anchoTramo() - 1;
            List<Prediccion> enTramo = cuentan.stream().filter(x -> x.confianza() >= desde && x.confianza() <= hasta).toList();
            if (enTramo.isEmpty()) {
                continue;
            }
            int n = enTramo.size();
            int suma = enTramo.stream().mapToInt(Prediccion::confianza).sum();
            int cumplidas = (int) enTramo.stream().filter(x -> x.seCumplio().get()).count();
            int media = redondear(suma, n);
            int porcentaje = redondear(100L * cumplidas, n);
            boolean provisional = n < p.umbral();
            Optional<String> aviso = aviso(desde, hasta, media, porcentaje, provisional, cuentan.size(), n);
            aviso.ifPresent(avisos::add);
            tramos.add(new Tramo(desde, hasta, n, media, porcentaje, provisional, aviso));
        }
        return new Calibracion(p.puntaje(), puntaje, cuentan.size(), sinResolver, fuera, tramos, avisos);
    }

    /** Promedio de (c/100 − r)²: Σ (c − 100 r)² / (10000 n), con dos decimales, la mitad hacia arriba, sin redondeos antes. */
    public static BigDecimal brier(List<Prediccion> resueltas) {
        long suma = 0;
        for (Prediccion x : resueltas) {
            long d = x.confianza() - (x.seCumplio().orElseThrow() ? 100L : 0L);
            suma += d * d;
        }
        return BigDecimal.valueOf(suma).divide(BigDecimal.valueOf(10_000L * resueltas.size()), 2, RoundingMode.HALF_UP);
    }

    /** Promedio de −ln de la probabilidad que se le dio a lo que pasó, con la confianza recortada entre 1 y 99. */
    public static BigDecimal logaritmico(List<Prediccion> resueltas) {
        double suma = 0;
        for (Prediccion x : resueltas) {
            int c = Math.max(1, Math.min(99, x.confianza()));
            double p = x.seCumplio().orElseThrow() ? c / 100.0 : (100 - c) / 100.0;
            suma += -Math.log(p);
        }
        return BigDecimal.valueOf(suma / resueltas.size()).setScale(2, RoundingMode.HALF_UP);
    }

    /** Lo que suma una sola predicción al Brier si sale al revés de lo que se dio por más probable: (c/100)² o ((100 − c)/100)². */
    public static BigDecimal costoSiFalla(int confianza) {
        long d = confianza >= 50 ? confianza : 100 - confianza;
        return BigDecimal.valueOf(d * d).divide(BigDecimal.valueOf(10_000L), 2, RoundingMode.HALF_UP);
    }

    private static Optional<String> aviso(int desde, int hasta, int media, int porcentaje, boolean provisional, int total, int n) {
        int diferencia = porcentaje - media;
        if (Math.abs(diferencia) <= DIFERENCIA_AVISO) {
            return Optional.empty();
        }
        String diagnostico = media >= 50 ? (diferencia < 0 ? "exceso de confianza" : "falta de confianza")
                : (diferencia < 0 ? "sobreestimas" : "subestimas");
        String texto = "Cuando dices entre " + desde + " y " + hasta + "%, se cumple el " + porcentaje + "% (declaras " + media
                + "% en promedio): " + diagnostico + ".";
        if (provisional) {
            texto += " Aviso provisional: " + total + (total == 1 ? " resuelta" : " resueltas") + ", solo " + n + " en ese tramo.";
        }
        return Optional.of(texto);
    }

    /** a / b redondeado a entero, la mitad hacia arriba (a y b no negativos). */
    private static int redondear(long a, long b) {
        return (int) ((2 * a + b) / (2 * b));
    }

    /** 0.17 → "0,17". */
    public static String decimal(BigDecimal valor) {
        return valor.toPlainString().replace('.', ',');
    }
}
