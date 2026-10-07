package pensamiento.nucleo.puertos;

/** Ollama no responde o no tiene el modelo: la aplicación cae al modo plantillas. */
public class IaNoDisponible extends ExcepcionIa {

    public IaNoDisponible(String mensaje) {
        super(mensaje);
    }

    public IaNoDisponible(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
