package pensamiento.tecnicas.f4;

import java.util.List;

/**
 * Valor que pinta el patrón V10 (tarjeta de veredicto) para T23 · Jerarquía de evidencia: cada nivel de la jerarquía con sus
 * evidencias y su fuerza (R01), y la fuerza neta de la afirmación (R02). No da el estado de la afirmación: eso es R03.
 *
 * @param porDiseno verdadero si los niveles son diseños del estudio (dato estadístico, relación causal); falso si son tipos de fuente
 * @param neta      fuerza neta con signo
 * @param sentido   "a favor", "en contra" o vacío con neta 0
 */
public record ResultadoJerarquia(String afirmacion, String tipo, boolean porDiseno, List<Nivel> niveles, List<EvidenciaPesada> evidencias, int neta,
                                 String magnitud, String sentido, String resumen) {

    /** @param postura apoya, contradice o matiza */
    public record EvidenciaPesada(String codigo, String descripcion, String nivel, String postura, int fuerza) {
    }

    /** Un nivel de la jerarquía, del más alto al más bajo, con los códigos de sus evidencias; vacío es "ninguna encontrada". */
    public record Nivel(String nombre, List<String> evidencias) {
        public Nivel {
            evidencias = List.copyOf(evidencias);
        }
    }

    public ResultadoJerarquia {
        niveles = List.copyOf(niveles);
        evidencias = List.copyOf(evidencias);
    }
}
