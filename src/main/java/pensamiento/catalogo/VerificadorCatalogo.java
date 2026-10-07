package pensamiento.catalogo;

import java.util.ArrayList;
import java.util.List;

import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Tecnica;
import pensamiento.nucleo.puertos.RepositorioTecnica;

/**
 * Verificación al arrancar: cada técnica del catálogo tiene ejecutor registrado (si está activa) o está
 * marcada pendiente; si no, la aplicación no arranca. También exige que haya exactamente 49 técnicas y
 * que ningún ejecutor apunte a una técnica inexistente.
 */
public final class VerificadorCatalogo {

    public record Informe(int tecnicas, int activas, int pendientes, List<String> problemas) {
        public boolean consistente() {
            return problemas.isEmpty();
        }
    }

    public static class CatalogoInconsistente extends IllegalStateException {
        public CatalogoInconsistente(Informe informe) {
            super("El catálogo no es consistente con los ejecutores registrados: " + String.join("; ", informe.problemas()));
        }
    }

    private final RepositorioTecnica tecnicas;
    private final RegistroEjecutores ejecutores;

    public VerificadorCatalogo(RepositorioTecnica tecnicas, RegistroEjecutores ejecutores) {
        this.tecnicas = tecnicas;
        this.ejecutores = ejecutores;
    }

    public Informe verificar() {
        List<Tecnica> todas = tecnicas.todas();
        List<String> problemas = new ArrayList<>();
        int activas = 0;
        int pendientes = 0;
        if (todas.size() != IdTecnica.TOTAL) {
            problemas.add("se esperaban " + IdTecnica.TOTAL + " técnicas y hay " + todas.size());
        }
        for (Tecnica t : todas) {
            boolean conEjecutor = ejecutores.tiene(t.id());
            if (t.estaPendiente()) {
                pendientes++;
                if (conEjecutor) {
                    problemas.add(t.cita() + " tiene ejecutor registrado pero está marcada pendiente");
                }
            } else {
                activas++;
                if (!conEjecutor) {
                    problemas.add(t.cita() + " está activa y no tiene ejecutor registrado");
                }
            }
        }
        for (IdTecnica id : ejecutores.identificadores()) {
            if (todas.stream().noneMatch(t -> t.id().equals(id))) {
                problemas.add("hay un ejecutor para " + id + " pero esa técnica no está en el catálogo");
            }
        }
        return new Informe(todas.size(), activas, pendientes, List.copyOf(problemas));
    }

    /** Lanza CatalogoInconsistente si algo no cuadra: así la aplicación no arranca. */
    public Informe exigirConsistencia() {
        Informe informe = verificar();
        if (!informe.consistente()) {
            throw new CatalogoInconsistente(informe);
        }
        return informe;
    }
}
