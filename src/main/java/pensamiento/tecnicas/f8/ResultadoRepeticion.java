package pensamiento.tecnicas.f8;

import java.util.List;

/**
 * Valor que pinta el patrón V13c (calendario) para T49 · Repetición espaciada y para el calendario del Dojo: los 7 días desde
 * hoy con sus repasos, los conceptos con su próximo repaso, la racha y la facilidad media.
 *
 * @param hoy            cuántos repasos tocan hoy, con los atrasados
 * @param facilidadMedia "2,6"; "—" sin conceptos
 */
public record ResultadoRepeticion(List<Dia> calendario, int hoy, List<Concepto> conceptos, int racha, String facilidadMedia, List<String> avisos,
                                  String resumen) {

    /** @param nombre "mié 7" */
    public record Dia(String fecha, String nombre, int repasos) {
    }

    /** @param facilidad "2,60"; @param proximoTexto "hoy", "mañana", "en 5 días", "atrasado 3 días" */
    public record Concepto(TemaDojo tema, String concepto, int repasos, String facilidad, int intervalo, String proximo, String proximoTexto) {
    }

    public ResultadoRepeticion {
        calendario = List.copyOf(calendario);
        conceptos = List.copyOf(conceptos);
        avisos = List.copyOf(avisos);
    }
}
