package pensamiento.web;

import pensamiento.nucleo.PendienteGuardado;
import pensamiento.nucleo.TipoPendiente;
import pensamiento.tecnicas.f4.EjecutorTriangulacion;

/**
 * Adónde lleva un pendiente: uno de verificación sobre una afirmación (o la revisión de una afirmación disputada) abre su
 * ficha de verificación; los demás, la ejecución que lo produjo.
 */
public final class EnlacePendiente {

    private EnlacePendiente() {
    }

    public static String de(PendienteGuardado p) {
        boolean aLaFicha = p.pendiente().objetoId().isPresent() && (p.pendiente().tipo() == TipoPendiente.VERIFICACION
                || p.pendiente().tipo() == TipoPendiente.REVISION && p.pendiente().descripcion().startsWith(EjecutorTriangulacion.REVISAR_CONFLICTO));
        return aLaFicha ? "/verificar/" + p.pendiente().objetoId().get() : "/ejecuciones/" + p.ejecucionId();
    }
}
