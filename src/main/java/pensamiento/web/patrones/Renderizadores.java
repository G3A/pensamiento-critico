package pensamiento.web.patrones;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import gg.jte.Content;
import org.springframework.stereotype.Component;

import pensamiento.nucleo.Tecnica;

/** Renderizadores indexados por patrón. Spring inyecta la lista; sin reflexión ni descubrimiento por nombre. */
@Component
public class Renderizadores {

    private final Map<String, RenderizadorResultado<?>> porPatron = new HashMap<>();

    public Renderizadores(List<RenderizadorResultado<?>> renderizadores) {
        renderizadores.forEach(r -> porPatron.put(r.patron(), r));
    }

    public boolean tiene(String patron) {
        return porPatron.containsKey(patron);
    }

    /** Pinta el valor de una técnica con el patrón que declara en el catálogo. */
    public Content render(Tecnica tecnica, Optional<UUID> idEjecucion, String sufijo, Object valor, Modo modo) {
        RenderizadorResultado<?> r = porPatron.get(tecnica.patron());
        if (r == null) {
            throw new IllegalStateException("No hay renderizador para el patrón " + tecnica.patron() + " de " + tecnica.cita());
        }
        return pintar(r, idEjecucion, sufijo, valor, modo);
    }

    private static <R> Content pintar(RenderizadorResultado<R> r, Optional<UUID> id, String sufijo, Object valor, Modo modo) {
        return r.render(id, sufijo, r.tipo().cast(valor), modo);
    }
}
