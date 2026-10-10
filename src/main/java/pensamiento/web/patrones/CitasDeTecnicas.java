package pensamiento.web.patrones;

import java.util.Map;
import java.util.stream.Collectors;

import pensamiento.catalogo.CatalogoJson;
import pensamiento.nucleo.Tecnica;

/**
 * "T29 · Pre-mortem" a partir del código, con los nombres del catálogo del repo (no cambian mientras la app corre). Una
 * técnica individual se cita siempre con código y nombre.
 */
public final class CitasDeTecnicas {

    private static final class Carga {
        static final Map<String, String> CITAS = new CatalogoJson().tecnicas().stream()
                .collect(Collectors.toUnmodifiableMap(t -> t.id().valor(), Tecnica::cita));
    }

    private CitasDeTecnicas() {
    }

    /** La cita de la técnica; el código tal cual si no está en el catálogo. */
    public static String cita(String codigo) {
        return Carga.CITAS.getOrDefault(codigo, codigo);
    }
}
