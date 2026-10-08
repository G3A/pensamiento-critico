package pensamiento.tecnicas.f1;

import java.util.List;
import java.util.UUID;

/**
 * Valor que pinta el patrón V10 para T05 · Validez y solidez: tipo de argumento, si la conclusión se seguiría y si las
 * premisas están establecidas. Nunca dice "válido" ni "sólido": dice qué pregunta quedó sin responder (corrección 13).
 *
 * @param tipoDetectado si el tipo lo detectaron las reglas léxicas (configuración "detectar")
 */
public record ResultadoValidez(Tipo tipo, boolean tipoDetectado, String conclusion, List<PremisaEvaluada> premisas, String estadoForma,
                               String fraseForma, String estadoSolidez, String fraseSolidez, List<String> preguntas, String resumen) {

    public enum Tipo {
        DEDUCTIVO("deductivo"), INDUCTIVO("inductivo");

        private final String texto;

        Tipo(String texto) {
            this.texto = texto;
        }

        public String texto() {
            return texto;
        }

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    public record PremisaEvaluada(String texto, boolean establecida, UUID afirmacionId) {
    }

    public ResultadoValidez {
        premisas = List.copyOf(premisas);
        preguntas = preguntas == null ? List.of() : List.copyOf(preguntas);
    }
}
