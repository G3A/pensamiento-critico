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
    private final Map<UUID, String> quienDaCuentas = new LinkedHashMap<>();
    /** La institución de la "sesión" para cambiar el texto; vacía, como sin sesión. */
    private Optional<UUID> deLaSesion = Optional.empty();

    /** Fija la institución de la sesión (lo que en la base hace ContextoRls). */
    public FakeRepositorioInstituciones comoInstitucion(UUID institucion) {
        this.deLaSesion = Optional.of(institucion);
        return this;
    }

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
    public Optional<String> quienDaCuentas(UUID institucion) {
        return Optional.ofNullable(instituciones.get(institucion)).filter(Institucion::activa)
                .map(i -> quienDaCuentas.getOrDefault(i.id(), QUIEN_DA_CUENTAS));
    }

    @Override
    public boolean cambiarQuienDaCuentas(UUID institucion, String texto) {
        if (!instituciones.containsKey(institucion) || !deLaSesion.equals(Optional.of(institucion))) {
            return false;
        }
        quienDaCuentas.put(institucion, texto);
        return true;
    }

    @Override
    public Optional<Institucion> porId(UUID id) {
        return Optional.ofNullable(instituciones.get(id));
    }
}
