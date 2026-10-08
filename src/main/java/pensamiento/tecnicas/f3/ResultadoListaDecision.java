package pensamiento.tecnicas.f3;

import java.util.List;

/**
 * Valor que pinta el patrón V02 (lista de verificación con estado) para T16 · Lista de verificación antes de decidir.
 *
 * @param bloqueo el motivo por el que no se puede guardar ("Guardado bloqueado: faltan 2 ítems obligatorios."); nulo si se puede
 * @param firma   "Firmada por … el …." o "Sin firmar: escribe quién firma y la fecha."
 */
public record ResultadoListaDecision(String decision, List<ItemEvaluado> items, int respondidos, String bloqueo, String firma, boolean firmada,
                                     String resumen) {

    public enum Estado {
        RESPONDIDO("respondido"), FALTA_OBLIGATORIO("falta (obligatorio)"), SIN_RESPONDER("sin responder");

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

    public record ItemEvaluado(EjecutorListaDecision.Item item, String pregunta, String respuesta, Estado estado) {
    }

    public ResultadoListaDecision {
        items = List.copyOf(items);
    }
}
