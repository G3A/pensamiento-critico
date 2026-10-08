package pensamiento.tecnicas.f1;

import java.util.List;

import pensamiento.nucleo.Propuesta;

/**
 * Valor que pinta el patrón V04 (dos columnas) para T07 · Razonamiento por analogía: similitudes frente a diferencias,
 * la diferencia clave marcada y la fuerza de la analogía con su motivo.
 */
public record ResultadoAnalogia(String caso, String conclusion, List<String> similitudes, List<DiferenciaEvaluada> diferencias, Fuerza fuerza,
                                String motivo, List<Propuesta> propuestas, String resumen) {

    public enum Fuerza {
        INCOMPLETA("incompleta"), DEBIL("débil"), MEDIA("media"), FUERTE("fuerte");

        private final String texto;

        Fuerza(String texto) {
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

    public record DiferenciaEvaluada(String texto, boolean clave, boolean verificada, boolean delModelo) {
    }

    public ResultadoAnalogia {
        similitudes = List.copyOf(similitudes);
        diferencias = List.copyOf(diferencias);
        propuestas = propuestas == null ? List.of() : List.copyOf(propuestas);
    }
}
