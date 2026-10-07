package pensamiento.nucleo.reglas;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import pensamiento.nucleo.EstadoAfirmacion;
import pensamiento.nucleo.Evidencia;
import pensamiento.nucleo.TipoAfirmacion;

/**
 * R03 · Estado de una afirmación. Se evalúa en orden y la primera rama que aplica decide:
 * 1) juicio de valor o definición → no verificable; 2) sin evidencias adoptadas → sin verificar;
 * 3) evidencia fuerte en ambos sentidos → disputada; 4) neta positiva fuerte y al menos dos fuentes a
 * favor de grupo distinto → verificada; 5) lo mismo en negativo → refutada; 6) cualquier otro caso →
 * en verificación.
 */
public final class R03EstadoAfirmacion {

    public record Parametros(int umbralFuerte, int fuentesIndependientesMinimas) {
        public static Parametros v1() {
            return new Parametros(6, 2);
        }
    }

    private R03EstadoAfirmacion() {
    }

    public static EstadoAfirmacion estado(TipoAfirmacion tipo, List<Evidencia> evidencias,
                                          R02FuerzaNeta.FuerzaNeta neta, Parametros p) {
        if (tipo == TipoAfirmacion.JUICIO_DE_VALOR || tipo == TipoAfirmacion.DEFINICION) {
            return EstadoAfirmacion.NO_VERIFICABLE;
        }
        List<Evidencia> adoptadas = evidencias.stream().filter(Evidencia::cuenta).toList();
        if (adoptadas.isEmpty()) {
            return EstadoAfirmacion.SIN_VERIFICAR;
        }
        boolean fuerteAFavor = adoptadas.stream().anyMatch(e -> e.postura() == Evidencia.Postura.APOYA && e.fuerza() >= p.umbralFuerte());
        boolean fuerteEnContra = adoptadas.stream().anyMatch(e -> e.postura() == Evidencia.Postura.CONTRADICE && e.fuerza() >= p.umbralFuerte());
        if (fuerteAFavor && fuerteEnContra) {
            return EstadoAfirmacion.DISPUTADA;
        }
        boolean netaFuerte = neta.magnitud() == R02FuerzaNeta.Magnitud.FUERTE;
        if (netaFuerte && neta.aFavor() && gruposIndependientes(adoptadas, Evidencia.Postura.APOYA) >= p.fuentesIndependientesMinimas()) {
            return EstadoAfirmacion.VERIFICADA;
        }
        if (netaFuerte && neta.enContra() && gruposIndependientes(adoptadas, Evidencia.Postura.CONTRADICE) >= p.fuentesIndependientesMinimas()) {
            return EstadoAfirmacion.REFUTADA;
        }
        return EstadoAfirmacion.EN_VERIFICACION;
    }

    /** Independencia mutua: dos fuentes son independientes si su grupo de origen difiere. Sin grupo, cada fuente es su propio grupo. */
    static int gruposIndependientes(List<Evidencia> evidencias, Evidencia.Postura postura) {
        Set<String> grupos = new HashSet<>();
        for (Evidencia e : evidencias) {
            if (e.postura() == postura) {
                grupos.add(e.fuente().grupoOrigen().orElse("fuente:" + e.fuente().id()));
            }
        }
        return grupos.size();
    }
}
