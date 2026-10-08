package pensamiento.tecnicas.f5;

import java.util.List;
import java.util.UUID;

/**
 * Valor que pinta el patrón V13a (lista priorizada) para T29 · Pre-mortem: el fracaso imaginado y las causas de más a
 * menos probable, cada una con su mitigación o su falta.
 */
public record ResultadoPremortem(String decision, String enunciado, List<CausaPriorizada> causas, List<String> avisos, String resumen) {

    public enum EstadoMitigacion {
        CON_MITIGACION, FALTA_MITIGACION, SIN_MITIGACION;

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    /**
     * @param probabilidad alta, media o baja
     * @param categoria    el nombre corto de la categoría de ayuda; nulo si no tiene
     */
    public record CausaPriorizada(UUID afirmacionId, String texto, String probabilidad, String categoria, String mitigacion,
                                  EstadoMitigacion estado) {
    }

    public ResultadoPremortem {
        causas = List.copyOf(causas);
        avisos = List.copyOf(avisos);
    }
}
