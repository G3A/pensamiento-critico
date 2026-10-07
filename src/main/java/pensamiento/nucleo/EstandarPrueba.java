package pensamiento.nucleo;

/** Umbral que una afirmación debe superar para aceptarse (regla R04, modelo de Carneades). */
public enum EstandarPrueba {
    ESCRUTINIO, PREPONDERANCIA, CLARO_Y_CONVINCENTE, MAS_ALLA_DE_DUDA_RAZONABLE;

    public String enBaseDeDatos() {
        return name().toLowerCase();
    }
}
