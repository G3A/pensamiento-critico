package pensamiento.nucleo.puertos;

/** El modelo respondió, pero con JSON inválido, una etiqueta fuera del enum o un texto que no pasa el validador. */
public class IaRespuestaInvalida extends ExcepcionIa {

    public IaRespuestaInvalida(String mensaje) {
        super(mensaje);
    }

    public IaRespuestaInvalida(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
