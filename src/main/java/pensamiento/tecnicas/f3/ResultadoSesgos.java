package pensamiento.tecnicas.f3;

import java.util.List;

/**
 * Valor que pinta el patrón V13a (lista priorizada) para T14 · Sesgos cognitivos: primero los sesgos probables, con la
 * señal que los delata, y después los que no tienen señal. Sin señal no dice "sin sesgos".
 */
public record ResultadoSesgos(String situacion, String contexto, List<SesgoRevisado> sesgos, List<String> antidotos, int probables, String resumen) {

    /**
     * @param probable si hay señal en el texto o la persona respondió que sí
     * @param senal    "Encontré «…» en tu situación.", "Respondiste que sí: …" o "No lo marcaste y el texto no lo muestra."
     */
    public record SesgoRevisado(String id, String nombre, boolean probable, String senal, String antidoto) {
    }

    public ResultadoSesgos {
        sesgos = List.copyOf(sesgos);
        antidotos = List.copyOf(antidotos);
    }
}
