package pensamiento.nucleo.puertos;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import pensamiento.nucleo.EvidenciaGuardada;

/**
 * Las evidencias de cada persona con la ficha de su fuente (tablas evidencia y fuente). Todo método recibe el usuario de la
 * sesión y solo devuelve lo suyo; la implementación real además corre bajo RLS. La afirmación de cada evidencia ya debe
 * existir. Una fuente sin evidencias se borra con la última.
 */
public interface RepositorioEvidencias {

    /**
     * Guarda la evidencia y su fuente: si la fuente ya existe se actualiza su ficha; si la evidencia ya existe, no se duplica
     * ni cambia.
     */
    void guardar(UUID usuarioId, UUID institucionId, EvidenciaGuardada evidencia);

    /** Las de la afirmación, en el orden en que se registraron; vacía si la afirmación no es del usuario. */
    List<EvidenciaGuardada> deAfirmacion(UUID usuarioId, UUID afirmacionId);

    /** Vacío si no existe o si es de otra persona. */
    Optional<EvidenciaGuardada> porId(UUID usuarioId, UUID id);

    /** Quita la evidencia y, si nadie más la usa, su fuente. Falso si no existe o es de otra persona. */
    boolean quitar(UUID usuarioId, UUID id);

    /** Todas las de la persona, en el orden en que se registraron (para el respaldo). */
    List<EvidenciaGuardada> deUsuario(UUID usuarioId);
}
