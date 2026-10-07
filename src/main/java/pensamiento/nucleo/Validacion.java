package pensamiento.nucleo;

import java.util.List;

/** Resultado de validar configuración y entrada: una lista de errores por campo; vacía si es válida. */
public record Validacion(List<Error> errores) {

    public record Error(String campo, String mensaje) {
    }

    public static final Validacion VALIDA = new Validacion(List.of());

    public Validacion {
        errores = List.copyOf(errores);
    }

    public boolean esValida() {
        return errores.isEmpty();
    }
}
