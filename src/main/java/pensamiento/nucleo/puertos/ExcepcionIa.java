package pensamiento.nucleo.puertos;

/** Base de los fallos del puerto Ia. Las tres subclases son el contrato de errores de toda implementación. */
public abstract class ExcepcionIa extends RuntimeException {

    protected ExcepcionIa(String mensaje) {
        super(mensaje);
    }

    protected ExcepcionIa(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
