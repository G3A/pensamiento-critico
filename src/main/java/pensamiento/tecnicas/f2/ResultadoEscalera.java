package pensamiento.tecnicas.f2;

import java.util.List;

/**
 * Valor que pinta el patrón V02 (lista de verificación con estado) para T10 · Escalera de inferencia: los peldaños activos
 * en el orden del sentido, cada uno con su estado y, en el débil, el motivo que dio la regla.
 *
 * @param debil  el identificador del peldaño débil; nulo si no hay
 * @param nota   la línea que se muestra si ningún peldaño es débil por las reglas; nula si hay uno
 */
public record ResultadoEscalera(String revisa, String sentido, List<Peldano> peldanos, int llenos, String debil, String nota, String resumen) {

    /**
     * @param numero   su lugar de abajo hacia arriba (datos es 1, acción es 6)
     * @param estado   "lleno", "pendiente" o "peldaño débil"
     * @param motivo   por qué es débil; nulo si no lo es
     */
    public record Peldano(String id, int numero, String nombre, String texto, String estado, boolean comprobado, String motivo) {
    }

    public ResultadoEscalera {
        peldanos = List.copyOf(peldanos);
    }
}
