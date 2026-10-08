package pensamiento.web.diario;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import pensamiento.flujos.DiarioDeDecisiones;
import pensamiento.nucleo.Prediccion;
import pensamiento.nucleo.puertos.Reloj;
import pensamiento.web.patrones.EstadoDePrediccion;
import pensamiento.web.seguridad.ContextoRls;

/** El estado de una predicción de la persona de la sesión, para pintar su registro al día; sin sesión, nada. */
@Component
public class EstadoDePrediccionEnSesion implements EstadoDePrediccion {

    private final DiarioDeDecisiones diario;
    private final Reloj reloj;

    public EstadoDePrediccionEnSesion(DiarioDeDecisiones diario, Reloj reloj) {
        this.diario = diario;
        this.reloj = reloj;
    }

    @Override
    public Optional<Revision> de(UUID prediccionId) {
        return ContextoRls.usuarioDeSesion().flatMap(yo -> diario.prediccion(yo.id(), prediccionId))
                .filter(Prediccion::resuelta)
                .map(p -> new Revision(p.estado() == Prediccion.Estado.ACIERTO, p.resueltaEn().orElseThrow().atZone(reloj.zona()).toLocalDate()));
    }
}
