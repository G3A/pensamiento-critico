package pensamiento.nucleo.argdown;

/** Error de sintaxis con línea y columna (desde 1) y un mensaje en español que dice cómo corregirlo. */
public class ErrorSintaxisArgdown extends RuntimeException {

    private final int linea;
    private final int columna;
    private final String explicacion;

    public ErrorSintaxisArgdown(int linea, int columna, String explicacion) {
        super("Línea " + linea + ", columna " + columna + ": " + explicacion);
        this.linea = linea;
        this.columna = columna;
        this.explicacion = explicacion;
    }

    public int linea() {
        return linea;
    }

    public int columna() {
        return columna;
    }

    public String explicacion() {
        return explicacion;
    }
}
