package pensamiento.web.verificacion;

import java.util.List;

import pensamiento.nucleo.Afirmacion;
import pensamiento.nucleo.PendienteGuardado;

/** La entrada a la verificación: los pendientes de verificación sobre una afirmación y las fichas ya trabajadas. */
public record VistaIndice(List<PendienteGuardado> pendientes, List<Trabajada> trabajadas) {

    public record Trabajada(Afirmacion afirmacion) {
    }

    public VistaIndice {
        pendientes = List.copyOf(pendientes);
        trabajadas = List.copyOf(trabajadas);
    }
}
