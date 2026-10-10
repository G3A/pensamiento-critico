package pensamiento.tecnicas.f8;

import java.util.List;

import pensamiento.nucleo.NivelBloom;

/**
 * Valor que pinta el patrón V13b (barras de progreso) para T48 · Taxonomía de Bloom y para el progreso del Dojo: los cuatro
 * niveles con su estado, sus aciertos de la meta y sus intentos; el nivel actual, el mensaje y los avisos.
 */
public record ResultadoBloom(TemaDojo tema, List<Nivel> niveles, NivelBloom actual, int meta, String mensaje, List<String> avisos, String resumen) {

    public record Nivel(NivelBloom nivel, EscaleraBloom.Estado estado, int aciertos, int intentos) {
    }

    public ResultadoBloom {
        niveles = List.copyOf(niveles);
        avisos = List.copyOf(avisos);
    }
}
