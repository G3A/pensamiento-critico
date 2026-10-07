package pensamiento.nucleo.puertos;

/** Se superó el tiempo máximo de la petición. */
public class IaTiempoAgotado extends ExcepcionIa {

    public IaTiempoAgotado(String mensaje) {
        super(mensaje);
    }

    public IaTiempoAgotado(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
