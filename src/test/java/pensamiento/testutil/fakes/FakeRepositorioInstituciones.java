package pensamiento.testutil.fakes;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import pensamiento.nucleo.Institucion;
import pensamiento.nucleo.puertos.RepositorioInstituciones;

/** Instituciones en memoria. Certificado por FakeRepositorioInstitucionesContractTest. */
public final class FakeRepositorioInstituciones implements RepositorioInstituciones {

    private final Map<UUID, Institucion> instituciones = new LinkedHashMap<>();

    /** Siembra una institución (lo que en la base hace el operador). */
    public Institucion agregar(Institucion institucion) {
        instituciones.put(institucion.id(), institucion);
        return institucion;
    }

    @Override
    public Optional<UUID> activaPorRuta(String ruta) {
        return Institucion.rutaValida(ruta).flatMap(r -> instituciones.values().stream()
                .filter(i -> i.ruta().equals(r) && i.activa()).map(Institucion::id).findFirst());
    }

    @Override
    public Optional<Institucion> porId(UUID id) {
        return Optional.ofNullable(instituciones.get(id));
    }
}
