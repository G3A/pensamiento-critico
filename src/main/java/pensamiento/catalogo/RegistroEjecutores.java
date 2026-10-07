package pensamiento.catalogo;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import pensamiento.nucleo.Ejecutor;
import pensamiento.nucleo.IdTecnica;

/** Ejecutores indexados por identificador. Spring inyecta la lista; no hay reflexión ni descubrimiento por nombre. */
public final class RegistroEjecutores {

    private final Map<IdTecnica, Ejecutor<?, ?, ?>> porId = new TreeMap<>();

    public RegistroEjecutores(Collection<? extends Ejecutor<?, ?, ?>> ejecutores) {
        for (Ejecutor<?, ?, ?> e : ejecutores) {
            Ejecutor<?, ?, ?> previo = porId.put(e.id(), e);
            if (previo != null) {
                throw new IllegalStateException("Dos ejecutores registrados para " + e.id() + ": "
                        + previo.getClass().getName() + " y " + e.getClass().getName());
            }
        }
    }

    public static RegistroEjecutores vacio() {
        return new RegistroEjecutores(Collections.emptyList());
    }

    public Optional<Ejecutor<?, ?, ?>> porId(IdTecnica id) {
        return Optional.ofNullable(porId.get(id));
    }

    public boolean tiene(IdTecnica id) {
        return porId.containsKey(id);
    }

    public Collection<IdTecnica> identificadores() {
        return Collections.unmodifiableCollection(porId.keySet());
    }
}
