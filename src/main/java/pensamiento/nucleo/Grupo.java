package pensamiento.nucleo;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Un grupo del aula (hito 10): un curso con su docente. El administrador lo crea y asigna su docente; un docente puede tener
 * varios (el profesor de la mesa 2 tiene 4 de 35). El docente asigna un tema del Dojo (con fecha opcional) y decide si su
 * clase abierta muestra la lista de nombres y si sus estudiantes pueden usar el modelo durante la clase (las dos opciones
 * apagadas por defecto).
 *
 * @param tema      el tema del Dojo asignado al grupo ("Reto de hoy" toma de él)
 * @param temaHasta hasta qué día rige la asignación (vacío: sin fecha)
 */
public record Grupo(UUID id, UUID institucionId, String nombre, Optional<UUID> docenteId, boolean activo, Optional<IdTecnica> tema,
                    Optional<LocalDate> temaHasta, boolean listaEnClase, boolean iaEnClase) {

    public static Grupo nuevo(UUID id, UUID institucionId, String nombre, Optional<UUID> docenteId) {
        return new Grupo(id, institucionId, nombre, docenteId, true, Optional.empty(), Optional.empty(), false, false);
    }

    /** El tema rige ese día: hay tema y no venció su fecha. */
    public Optional<IdTecnica> temaVigente(LocalDate hoy) {
        return tema.filter(t -> temaHasta.map(h -> !h.isBefore(hoy)).orElse(true));
    }
}
