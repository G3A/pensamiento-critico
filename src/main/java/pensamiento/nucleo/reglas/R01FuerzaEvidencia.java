package pensamiento.nucleo.reglas;

import java.time.LocalDate;
import java.util.Map;

import pensamiento.nucleo.Fuente;
import pensamiento.nucleo.TipoAfirmacion;

/**
 * R01 · Fuerza de una evidencia. Tabla de decisión por tipo de la afirmación.
 * Dato estadístico o relación causal: por diseño del estudio de la fuente. Hecho puntual, testimonio y
 * el resto: por tipo de fuente (primaria, secundaria, terciaria). A ambos se suman cuatro bonos.
 * Rango 0 a 8.
 */
public final class R01FuerzaEvidencia {

    public record Parametros(
            Map<Fuente.DisenoEstudio, Integer> porDisenoEstudio,
            Map<Fuente.TipoFuente, Integer> porTipoFuente,
            int bonoReciente,
            int aniosParaReciente,
            int bonoIndependiente,
            int bonoAccesoOriginal,
            int bonoCraap,
            int umbralCraap,
            int maximo) {

        /** Versión 1 de la regla (catalogo/reglas.json). */
        public static Parametros v1() {
            return new Parametros(
                    Map.of(Fuente.DisenoEstudio.REVISION_SISTEMATICA, 4,
                           Fuente.DisenoEstudio.ENSAYO_CONTROLADO, 3,
                           Fuente.DisenoEstudio.OBSERVACIONAL, 2,
                           Fuente.DisenoEstudio.OPINION_EXPERTO, 1,
                           Fuente.DisenoEstudio.TESTIMONIO, 0),
                    Map.of(Fuente.TipoFuente.PRIMARIA, 2,
                           Fuente.TipoFuente.SECUNDARIA, 1,
                           Fuente.TipoFuente.TERCIARIA, 0),
                    1, 5, 1, 1, 1, 18, 8);
        }
    }

    private R01FuerzaEvidencia() {
    }

    public static int fuerza(TipoAfirmacion tipo, Fuente fuente, LocalDate hoy, Parametros p) {
        int base;
        if (tipo == TipoAfirmacion.DATO_ESTADISTICO || tipo == TipoAfirmacion.CAUSAL) {
            base = fuente.disenoEstudio().map(d -> p.porDisenoEstudio().getOrDefault(d, 0)).orElse(0);
        } else {
            base = p.porTipoFuente().getOrDefault(fuente.tipo(), 0);
        }
        int bonos = 0;
        if (fuente.fecha().map(f -> !f.isBefore(hoy.minusYears(p.aniosParaReciente()))).orElse(false)) {
            bonos += p.bonoReciente();
        }
        if (fuente.independienteDelAutor()) {
            bonos += p.bonoIndependiente();
        }
        if (fuente.accesoOriginal()) {
            bonos += p.bonoAccesoOriginal();
        }
        if (fuente.puntajeCraap().map(c -> c >= p.umbralCraap()).orElse(false)) {
            bonos += p.bonoCraap();
        }
        return Math.max(0, Math.min(p.maximo(), base + bonos));
    }
}
