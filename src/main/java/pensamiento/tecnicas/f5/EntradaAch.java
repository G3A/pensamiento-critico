package pensamiento.tecnicas.f5;

import java.util.List;

/**
 * Entrada de T28: la pregunta, las hipótesis en competencia y las evidencias con su peso y una celda por
 * hipótesis. Una celda es "C", "I" o "N" en la escala C, I, N, o un entero de -2 a 2 en la numérica.
 */
public record EntradaAch(String pregunta, List<Hipotesis> hipotesis, List<Evidencia> evidencias) {

    public record Hipotesis(String texto) {
    }

    public record Evidencia(String texto, Peso peso, List<String> celdas) {
    }

    /** Alto pesa 3, medio 2 y bajo 1 (deducido del mockup de la pestaña Usar). */
    public enum Peso {
        ALTO(3), MEDIO(2), BAJO(1);

        private final int valor;

        Peso(int valor) {
            this.valor = valor;
        }

        public int valor() {
            return valor;
        }

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    public EntradaAch {
        hipotesis = hipotesis == null ? List.of() : List.copyOf(hipotesis);
        evidencias = evidencias == null ? List.of() : List.copyOf(evidencias);
    }
}
