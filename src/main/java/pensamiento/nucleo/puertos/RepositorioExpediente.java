package pensamiento.nucleo.puertos;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import pensamiento.nucleo.Expediente;

/** Expedientes del usuario de la sesión. Como todo repositorio, filtra por usuario; RLS es la segunda cerradura. */
public interface RepositorioExpediente {

    Expediente guardar(Expediente expediente);

    /** Vacío si no existe o si pertenece a otro usuario: ambas cosas se ven igual desde afuera. */
    Optional<Expediente> porId(UUID usuarioId, UUID id);

    /** Del más reciente al más antiguo. */
    List<Expediente> deUsuario(UUID usuarioId);
}
