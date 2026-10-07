package pensamiento.nucleo;

/** Entrada del catálogo canónico: identificador estable, facetas, tipo, origen y estado. */
public record Tecnica(
        IdTecnica id,
        String familia,
        String nombre,
        String nombreLlano,
        String usalaCuando,
        String definicion,
        Tipo tipo,
        Operacion operacion,
        Objeto objeto,
        Modalidad modalidad,
        String patron,
        String origen,
        RequiereIa requiereIa,
        int versionEsquema,
        Json esquemaConfig,
        Json esquemaEntrada,
        Json configDefault,
        Estado estado) {

    public enum Tipo { MARCO, REPRESENTACION, CRITERIO, PROCEDIMIENTO, PRACTICA, METODO_DE_APRENDIZAJE }

    public enum Operacion { ANALIZAR, EVALUAR, GENERAR, DECIDIR, CUESTIONAR, REFLEXIONAR }

    public enum Objeto { ARGUMENTO, AFIRMACION, FUENTE, DECISION, PROBLEMA, UNO_MISMO }

    public enum Modalidad { DIAGRAMA, FORMULARIO, DIALOGO, CALCULO, LISTA }

    public enum RequiereIa { NO, OPCIONAL, SI }

    public enum Estado { ACTIVA, PENDIENTE }

    /** Cita canónica: código más nombre, por ejemplo "T28 · Análisis de hipótesis en competencia (ACH)". */
    public String cita() {
        return id + " · " + nombre;
    }

    public boolean estaPendiente() {
        return estado == Estado.PENDIENTE;
    }
}
