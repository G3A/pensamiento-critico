package pensamiento.tecnicas.f5;

/**
 * Configuración de T28 · Análisis de hipótesis en competencia (ACH), versión de esquema 1.
 *
 * @param maxHipotesis de 2 a 8; por defecto 4
 * @param escala       C, I, N o numérica de -2 a +2
 * @param pesosActivos si es falso, toda evidencia pesa 1
 */
public record ConfigAch(int maxHipotesis, Escala escala, boolean pesosActivos) {

    public static final int MIN_HIPOTESIS = 2;
    public static final int TOPE_HIPOTESIS = 8;
    public static final int TOPE_EVIDENCIAS = 12;

    public static final ConfigAch POR_DEFECTO = new ConfigAch(4, Escala.CIN, true);

    public enum Escala {
        CIN, NUMERICA;

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }
}
