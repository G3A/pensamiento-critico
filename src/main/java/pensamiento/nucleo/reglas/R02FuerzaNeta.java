package pensamiento.nucleo.reglas;

import java.util.List;

import pensamiento.nucleo.Evidencia;

/**
 * R02 · Fuerza neta de una afirmación: suma de fuerzas de evidencias adoptadas que apoyan menos las que
 * contradicen; las que matizan no suman; las no adoptadas no cuentan. Se clasifica por valor absoluto.
 */
public final class R02FuerzaNeta {

    public record Parametros(int umbralMedia, int umbralFuerte) {
        public static Parametros v1() {
            return new Parametros(3, 6);
        }
    }

    public enum Magnitud { DEBIL, MEDIA, FUERTE }

    public record FuerzaNeta(int valor, Magnitud magnitud) {
        public boolean aFavor() {
            return valor > 0;
        }

        public boolean enContra() {
            return valor < 0;
        }
    }

    private R02FuerzaNeta() {
    }

    public static FuerzaNeta neta(List<Evidencia> evidencias, Parametros p) {
        int valor = 0;
        for (Evidencia e : evidencias) {
            if (!e.cuenta()) {
                continue;
            }
            switch (e.postura()) {
                case APOYA -> valor += e.fuerza();
                case CONTRADICE -> valor -= e.fuerza();
                case MATIZA -> { }
            }
        }
        return new FuerzaNeta(valor, magnitud(Math.abs(valor), p));
    }

    static Magnitud magnitud(int absoluto, Parametros p) {
        if (absoluto >= p.umbralFuerte()) {
            return Magnitud.FUERTE;
        }
        if (absoluto >= p.umbralMedia()) {
            return Magnitud.MEDIA;
        }
        return Magnitud.DEBIL;
    }
}
