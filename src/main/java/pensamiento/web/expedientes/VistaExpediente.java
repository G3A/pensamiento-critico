package pensamiento.web.expedientes;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import gg.jte.Content;

import pensamiento.expediente.ServicioExpedientes;
import pensamiento.nucleo.Ejecucion;
import pensamiento.nucleo.Expediente;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.PendienteGuardado;
import pensamiento.nucleo.Tecnica;
import pensamiento.web.tecnicas.Fechas;

/**
 * Lo que pinta la vista del Expediente: no renderiza nada propio, incrusta en modo lectura los fragmentos de
 * resultado de cada técnica y agrega línea de tiempo, resumen por familia y qué falta para cerrar.
 *
 * @param muestra verdadero para el expediente de muestra: solo lectura, sin acciones
 */
public record VistaExpediente(Expediente expediente, boolean muestra, List<Item> lineaDeTiempo, List<ServicioExpedientes.PorFamilia> porFamilia,
                              List<Falta> faltaParaCerrar, long familiasConEjecuciones) {

    /** Una ejecución en la línea de tiempo, con su fragmento de resultado en modo lectura. */
    public record Item(Ejecucion ejecucion, Tecnica tecnica, String fecha, Content resultado) {
    }

    /** Un pendiente accionable: abre la ejecución que lo produjo. */
    public record Falta(PendienteGuardado pendiente, String enlace) {
    }

    public static VistaExpediente de(ServicioExpedientes.Vista vista, Map<IdTecnica, Tecnica> tecnicas, boolean muestra,
                                     Function<Ejecucion, Content> resultado, ZoneId zona) {
        List<Item> items = vista.lineaDeTiempo().stream()
                .map(e -> new Item(e, tecnicas.get(e.tecnica()), Fechas.corta(e.creadaEn(), zona), resultado.apply(e)))
                .toList();
        List<Falta> falta = vista.faltaParaCerrar().stream()
                .map(p -> new Falta(p, muestra ? "" : pensamiento.web.EnlacePendiente.de(p)))
                .toList();
        return new VistaExpediente(vista.expediente(), muestra, items, vista.porFamilia(), falta, vista.familiasConEjecuciones());
    }

    public String idRaiz() {
        return muestra ? "expediente-muestra" : "expediente-" + expediente.id();
    }
}
