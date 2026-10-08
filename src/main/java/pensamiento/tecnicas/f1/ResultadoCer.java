package pensamiento.tecnicas.f1;

import java.util.List;
import java.util.UUID;

/**
 * Valor que pinta el patrón V10 (tarjeta de veredicto con barras) para T03 · Afirmación, evidencia, razonamiento (CER):
 * cada pieza con su estado y, si no está completa, la pregunta que la completaría (corrección 13).
 */
public record ResultadoCer(List<PiezaEvaluada> piezas, int completas, int total, String resumen) {

    /** Las cinco piezas, en orden; contraargumento y réplica solo si la configuración los pide. */
    public enum Pieza {
        AFIRMACION("Afirmación"), EVIDENCIA("Evidencia"), RAZONAMIENTO("Razonamiento"), CONTRAARGUMENTO("Contraargumento"), REPLICA("Réplica");

        private final String nombre;

        Pieza(String nombre) {
            this.nombre = nombre;
        }

        public String nombre() {
            return nombre;
        }

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    public enum Estado {
        COMPLETA("completa"), CORTA("corta"), FALTA("falta");

        private final String texto;

        Estado(String texto) {
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

    /** @param falta la pregunta que la completaría; nula si está completa */
    public record PiezaEvaluada(Pieza pieza, String texto, Estado estado, String falta, UUID afirmacionId) {
        public boolean completa() {
            return estado == Estado.COMPLETA;
        }
    }

    public ResultadoCer {
        piezas = List.copyOf(piezas);
    }
}
