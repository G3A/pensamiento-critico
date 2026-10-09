package pensamiento.tecnicas.f6;

import java.util.List;
import java.util.UUID;

/**
 * Valor que pinta el patrón V04 (dos columnas comparativas) para T38 · Double crux: cada lado con lo que sostiene y los
 * hechos de los que depende; debajo, los cruxes con su estado, las sugerencias de la regla y los avisos.
 */
public record ResultadoDoubleCrux(Lado a, Lado b, List<Crux> cruxes, int comunes, int aVerificacion, List<String> sugerencias, List<String> avisos,
                                  String estado, String resumen) {

    public record Lado(String quien, String postura, List<String> depende) {
        public Lado {
            depende = List.copyOf(depende);
        }
    }

    /** @param comun si cambiaría a los dos lados */
    public record Crux(String hecho, boolean cambiaA, boolean cambiaB, boolean comun, boolean verificable, String como, UUID afirmacionId) {
    }

    public ResultadoDoubleCrux {
        cruxes = List.copyOf(cruxes);
        sugerencias = List.copyOf(sugerencias);
        avisos = List.copyOf(avisos);
    }
}
