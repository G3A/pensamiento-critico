package pensamiento.nucleo;

/** Umbral que una afirmación debe superar para aceptarse (regla R04, modelo de Carneades). */
public enum EstandarPrueba {
    ESCRUTINIO, PREPONDERANCIA, CLARO_Y_CONVINCENTE, MAS_ALLA_DE_DUDA_RAZONABLE;

    public String enBaseDeDatos() {
        return name().toLowerCase();
    }

    /** En minúscula, como en el catálogo y en el JSONB ("preponderancia"). */
    @Override
    public String toString() {
        return enBaseDeDatos();
    }

    /** "más allá de duda razonable", para las tarjetas. */
    public String nombre() {
        return switch (this) {
            case ESCRUTINIO -> "escrutinio";
            case PREPONDERANCIA -> "preponderancia";
            case CLARO_Y_CONVINCENTE -> "claro y convincente";
            case MAS_ALLA_DE_DUDA_RAZONABLE -> "más allá de duda razonable";
        };
    }
}
