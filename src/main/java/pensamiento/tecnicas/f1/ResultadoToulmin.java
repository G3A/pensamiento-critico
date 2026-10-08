package pensamiento.tecnicas.f1;

import java.util.List;
import java.util.UUID;

/**
 * Valor que pinta el patrón V02 (lista de verificación con estado) para T02 · Modelo de Toulmin. Cada parte
 * dice su estado con texto y, si no está completa, qué haría falta para completarla (corrección 13).
 *
 * @param completas cuántas partes están completas
 * @param total     6 en el nivel completo, 3 en el básico
 */
public record ResultadoToulmin(Nivel nivel, List<ParteEvaluada> partes, int completas, int total, String resumen) {

    public enum Nivel {
        BASICO, COMPLETO;

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    /** Las seis partes, en el orden del modelo. */
    public enum Parte {
        AFIRMACION("Afirmación"), DATOS("Datos"), GARANTIA("Garantía"), RESPALDO("Respaldo"), CALIFICADOR("Calificador"), REFUTACION("Refutación");

        private final String nombre;

        Parte(String nombre) {
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
        COMPLETA("completa"), FALTA("falta"), SIN_FUENTE("sin fuente"), SIN_RESPONDER("sin responder");

        private final String texto;

        Estado(String texto) {
            this.texto = texto;
        }

        /** Lo que se lee en la lista, además del color. */
        public String texto() {
            return texto;
        }

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    /**
     * @param texto        lo que escribió la persona; nulo si la parte está vacía
     * @param complemento  la fuente del respaldo o la respuesta a la refutación; nulo si no hay
     * @param falta        la pregunta que la completaría; nula si está completa
     * @param afirmacionId la afirmación guardada para esta parte, si es una (afirmación, datos, garantía, refutación)
     */
    public record ParteEvaluada(Parte parte, String texto, String complemento, Estado estado, String falta, UUID afirmacionId) {
        public boolean completa() {
            return estado == Estado.COMPLETA;
        }
    }

    public ResultadoToulmin {
        partes = List.copyOf(partes);
    }
}
