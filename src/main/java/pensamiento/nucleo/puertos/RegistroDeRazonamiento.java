package pensamiento.nucleo.puertos;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import pensamiento.nucleo.CambioOpinion;
import pensamiento.nucleo.IdTecnica;

/**
 * Lo que leen T45 · Diario de razonamiento, T46 · Registro de cambios de opinión y P20, juntando ejecuciones, expedientes,
 * cambios de opinión y posturas. Solo lectura; todo método recibe el usuario de la sesión y solo devuelve lo suyo (la
 * implementación real además corre bajo RLS). Las ejecuciones borradas no cuentan.
 */
public interface RegistroDeRazonamiento {

    /**
     * Una ejecución vista desde el diario.
     *
     * @param expediente el nombre de su expediente; vacío si no tiene o si se borró
     * @param cambios    cuántos cambios de opinión registró
     */
    record EjecucionEnDiario(UUID id, IdTecnica tecnica, String resumen, Optional<String> expediente, int cambios, Instant creadaEn) {
    }

    /** Un cambio de opinión con la técnica de la ejecución que lo registró (aunque esté en la papelera); vacía si no tiene ejecución. */
    record CambioRegistrado(CambioOpinion cambio, Optional<IdTecnica> tecnica) {
    }

    /** Una afirmación con rol postura y la fecha de la última ejecución que la produjo o la consumió. */
    record PosturaRegistrada(UUID afirmacionId, String texto, Instant ultimaVez) {
    }

    /** Las ejecuciones guardadas desde ese instante (incluido), de la más vieja a la más nueva. */
    List<EjecucionEnDiario> ejecucionesDesde(UUID usuarioId, Instant desde);

    /** Todos los cambios de opinión de la persona, del más viejo al más nuevo. */
    List<CambioRegistrado> cambios(UUID usuarioId);

    /** Las posturas de la persona, de la que lleva más tiempo sin tocarse a la más reciente (y por identificador). */
    List<PosturaRegistrada> posturas(UUID usuarioId);
}
