package pensamiento.tecnicas.f4;

import java.util.List;

import pensamiento.nucleo.Propuesta;

/**
 * Valor que pinta el patrón V10 (tarjeta de veredicto) para T22 · Triangulación: cada pasaje con su postura, su fuerza
 * (R01) y si cuenta, la fuerza neta (R02) y el estado de la afirmación (R03) con su motivo. "Verificada" siempre es
 * "por ti, bajo R03": la app nunca dice "verdadero".
 *
 * @param estado  uno de los seis estados de R03, en minúscula ("en_verificacion")
 * @param neta    fuerza neta con signo
 */
public record ResultadoTriangulacion(String afirmacion, String tipo, List<EvidenciaEvaluada> evidencias, int neta, String magnitud, String estado,
                                     String motivo, int cuentan, int grupos, List<Propuesta> propuestas, String resumen) {

    /**
     * @param postura apoya, contradice, matiza, irrelevante o sin_etiquetar
     * @param cuenta  si entra en R02 y R03: etiquetada por la persona o por una propuesta adoptada, y no irrelevante
     * @param detalle  por qué no cuenta, si no cuenta
     * @param fuenteId la fuente en la tabla fuente; vacío en las ejecuciones de la versión 1 o si la fila no va a la tabla
     */
    public record EvidenciaEvaluada(String codigo, String titulo, String grupo, String pasaje, String postura, int fuerza, boolean cuenta,
                                    String etiquetadaPor, String detalle, String fuenteId) {
    }

    public ResultadoTriangulacion {
        evidencias = List.copyOf(evidencias);
        propuestas = propuestas == null ? List.of() : List.copyOf(propuestas);
    }
}
