package pensamiento.web;

/** Lo que no existe y lo que es de otra persona se ven igual desde afuera: 404. */
public class ObjetoNoEncontrado extends RuntimeException {

    public ObjetoNoEncontrado(String tipo) {
        super("No existe ese " + tipo);
    }
}
