package pensamiento.tecnicas.f1;

import java.util.List;

import pensamiento.nucleo.Propuesta;

/**
 * Valor que pinta el patrón V02 (lista de verificación) para T04 · Elementos y estándares de Paul-Elder: los
 * elementos activos llenos o vacíos y los estándares puntuados, sin nota global (corrección 13).
 */
public record ResultadoPaulElder(String tema, List<ElementoEvaluado> elementos, int llenos, List<EstandarEvaluado> estandares, int puntuados,
                                 boolean suficiente, int umbral, List<Propuesta> propuestas, String resumen) {

    /** @param falta la pregunta que llena el elemento; nula si está lleno */
    public record ElementoEvaluado(String elemento, String nombre, String texto, String falta, boolean delModelo) {
        public boolean lleno() {
            return texto != null;
        }
    }

    /**
     * @param puntaje de 1 a 10; nulo si está sin puntuar
     * @param estado  "puntuado", "bajo" o "sin puntuar"
     * @param pregunta la pregunta del estándar si el puntaje es bajo; nula si no
     */
    public record EstandarEvaluado(String estandar, String nombre, Integer puntaje, String estado, String pregunta) {
    }

    public ResultadoPaulElder {
        elementos = List.copyOf(elementos);
        estandares = List.copyOf(estandares);
        propuestas = propuestas == null ? List.of() : List.copyOf(propuestas);
    }
}
