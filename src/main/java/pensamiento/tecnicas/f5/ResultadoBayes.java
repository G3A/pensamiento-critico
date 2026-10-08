package pensamiento.tecnicas.f5;

import java.util.List;

/**
 * Valor que pinta el patrón V08 para T24 · Razonamiento bayesiano: el prior y el posterior después de cada evidencia,
 * con su razón de verosimilitud y, en formato odds, las odds de cada paso.
 *
 * @param priorPorDefecto el prior salió de la configuración porque la persona no escribió uno
 * @param oddsPrior       nulo si el formato es porcentaje
 */
public record ResultadoBayes(String afirmacion, int prior, boolean priorPorDefecto, String oddsPrior, List<Paso> pasos, String formato,
                             List<String> avisos, String resumen) {

    /**
     * @param razon     si es cierta / si es falsa, con hasta dos decimales ("0,4")
     * @param posterior porcentaje entero después de esta evidencia
     * @param odds      nulo si el formato es porcentaje
     */
    public record Paso(String codigo, String evidencia, int siCierta, int siFalsa, String razon, int posterior, String odds) {
    }

    public ResultadoBayes {
        pasos = List.copyOf(pasos);
        avisos = List.copyOf(avisos);
    }
}
