package pensamiento.nucleo;

/** Los ocho tipos de afirmación. Juicio de valor y definición no son verificables. */
public enum TipoAfirmacion {
    HECHO, DATO_ESTADISTICO, CAUSAL, GENERALIZACION, DEFINICION, TESTIMONIO, PREDICCION, JUICIO_DE_VALOR;

    public String enBaseDeDatos() {
        return name().toLowerCase();
    }
}
