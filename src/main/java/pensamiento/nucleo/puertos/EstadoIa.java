package pensamiento.nucleo.puertos;

import java.util.List;

/** Estado de la IA local: disponible solo si responde y lista los modelos esperados. */
public record EstadoIa(boolean disponible, List<String> modelos, String detalle) {

    public static EstadoIa noDisponible(String detalle) {
        return new EstadoIa(false, List.of(), detalle);
    }
}
