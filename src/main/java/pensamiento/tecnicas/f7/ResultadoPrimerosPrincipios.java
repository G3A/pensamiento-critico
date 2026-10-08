package pensamiento.tecnicas.f7;

import java.util.List;
import java.util.UUID;

/**
 * Valor que pinta el patrón V04 para T41 · Primeros principios: dos columnas, lo que se sabe con certeza y lo que se
 * asume, cada supuesto con su forma de verificarlo.
 */
public record ResultadoPrimerosPrincipios(String problema, List<Item> certezas, List<Item> supuestos, List<String> avisos, String resumen) {

    /**
     * @param afirmacionId la afirmación de la certeza o del supuesto (el supuesto es el objeto de su pendiente)
     * @param como         cómo lo sé o cómo lo verificaría; nulo si no lo dice
     * @param faltaComo    un supuesto sin cómo verificarlo cuando la configuración lo exige
     */
    public record Item(UUID afirmacionId, String texto, String como, boolean faltaComo) {
    }

    public ResultadoPrimerosPrincipios {
        certezas = List.copyOf(certezas);
        supuestos = List.copyOf(supuestos);
        avisos = List.copyOf(avisos);
    }
}
