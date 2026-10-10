package pensamiento.nucleo;

/**
 * Los cuatro niveles de reto del Dojo (T48 · Taxonomía de Bloom; sección 7): identificar, elige el nombre; analizar,
 * señala dónde está; evaluar, di si se sostiene sin el error; crear, reescríbelo sin él. En este orden.
 */
public enum NivelBloom {
    IDENTIFICAR("elige el nombre de la falacia, el sesgo, el error o el movimiento"),
    ANALIZAR("señala en qué parte del texto está"),
    EVALUAR("di si el argumento se sostiene sin el error"),
    CREAR("reescríbelo sin el error");

    private final String quePide;

    NivelBloom(String quePide) {
        this.quePide = quePide;
    }

    /** Lo que pide un reto de este nivel, en una línea. */
    public String quePide() {
        return quePide;
    }

    /** "identificar". */
    @Override
    public String toString() {
        return name().toLowerCase();
    }

    public static NivelBloom de(String valor) {
        return valueOf(valor.toUpperCase());
    }
}
