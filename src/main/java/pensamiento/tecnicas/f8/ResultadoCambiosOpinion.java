package pensamiento.tecnicas.f8;

import java.util.List;

import pensamiento.nucleo.CambioOpinion;

/**
 * Valor que pinta el patrón V11 (línea de tiempo) para T46 · Registro de cambios de opinión y para P20: los cambios del más
 * reciente al más viejo, el resumen del año con las seis causas, la lectura y las posturas sin revisar.
 *
 * @param anio            el año del reloj
 * @param porCausa        las seis causas siempre, en su orden
 * @param mesesSinRevisar el umbral de la configuración, para el título de la lista
 */
public record ResultadoCambiosOpinion(List<Cambio> linea, int anio, int total, List<PorCausa> porCausa, String lectura, int mesesSinRevisar,
                                      List<SinRevisar> sinRevisar, List<String> avisos, String resumen) {

    /** @param tecnica la que lo registró ("T22"); T46 para el registrado a mano en esta ejecución */
    public record Cambio(String fecha, String fechaTexto, String postura, int antes, int despues, CambioOpinion.Causa causa, String tecnica) {
    }

    public record PorCausa(CambioOpinion.Causa causa, int cambios) {
    }

    /** @param desde la última vez que se trabajó, AAAA-MM-DD; @param meses los meses completos hasta hoy */
    public record SinRevisar(String postura, String desde, String desdeTexto, int meses) {
    }

    public ResultadoCambiosOpinion {
        linea = List.copyOf(linea);
        porCausa = List.copyOf(porCausa);
        sinRevisar = List.copyOf(sinRevisar);
        avisos = List.copyOf(avisos);
    }
}
