package pensamiento.tecnicas.f3;

import java.util.List;

/**
 * Configuración de T13 · Falacias como esquemas fallidos, versión de esquema 1.
 *
 * @param esquemas        esquemas de Walton activos del catálogo único
 * @param sensibilidad    solo reglas léxicas, o reglas y modelo (hito 3): el modelo clasifica las oraciones sin marca
 * @param mostrarPregunta mostrar la pregunta crítica que falló (R06)
 */
public record ConfigFalacias(List<String> esquemas, Sensibilidad sensibilidad, boolean mostrarPregunta) {

    public enum Sensibilidad {
        REGLAS, REGLAS_Y_MODELO;

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    public ConfigFalacias {
        esquemas = esquemas == null ? List.of() : List.copyOf(esquemas);
    }

    public static ConfigFalacias porDefecto() {
        return new ConfigFalacias(ReglasFalacias.ESQUEMAS, Sensibilidad.REGLAS, true);
    }
}
