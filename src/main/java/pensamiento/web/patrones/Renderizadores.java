package pensamiento.web.patrones;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import gg.jte.Content;
import org.springframework.stereotype.Component;

import pensamiento.nucleo.Tecnica;

/**
 * Renderizadores indexados por el tipo de resultado que pintan. Varias técnicas comparten un patrón (V02, V04, V05,
 * V10), cada una con su renderizador que traduce su resultado al record del patrón. Spring inyecta la lista; sin
 * reflexión ni descubrimiento por nombre. El patrón del renderizador debe ser el que la técnica declara en el catálogo.
 */
@Component
public final class Renderizadores {

    private final Map<Class<?>, RenderizadorResultado<?>> porTipo = new HashMap<>();

    public Renderizadores(List<RenderizadorResultado<?>> renderizadores) {
        for (RenderizadorResultado<?> r : renderizadores) {
            if (porTipo.put(r.tipo(), r) != null) {
                throw new IllegalStateException("Dos renderizadores para " + r.tipo().getSimpleName());
            }
        }
    }

    public boolean tiene(String patron) {
        return porTipo.values().stream().anyMatch(r -> r.patron().equals(patron));
    }

    /** Pinta el valor de una técnica con su renderizador, que debe ser del patrón que la técnica declara. */
    public Content render(Tecnica tecnica, Optional<UUID> idEjecucion, String sufijo, Object valor, Modo modo) {
        RenderizadorResultado<?> r = porTipo.get(valor.getClass());
        if (r == null) {
            throw new IllegalStateException("No hay renderizador para " + valor.getClass().getSimpleName() + " de " + tecnica.cita());
        }
        if (!r.patron().equals(tecnica.patron())) {
            throw new IllegalStateException(tecnica.cita() + " declara el patrón " + tecnica.patron() + " pero su resultado se pinta con " + r.patron());
        }
        return pintar(r, idEjecucion, sufijo, valor, modo);
    }

    private static <R> Content pintar(RenderizadorResultado<R> r, Optional<UUID> id, String sufijo, Object valor, Modo modo) {
        return r.render(id, sufijo, r.tipo().cast(valor), modo);
    }
}
