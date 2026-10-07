package pensamiento.nucleo.puertos;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Tabla auditoria: solo inserción. Se escribe al crear, importar, exportar, compartir, borrar y desactivar. */
public interface RegistroAuditoria {

    enum Accion { CREAR, IMPORTAR, EXPORTAR, COMPARTIR, BORRAR, DESACTIVAR, SESION }

    record Evento(Optional<UUID> usuarioId, UUID institucionId, Accion accion, String objetoTipo, Optional<UUID> objetoId, Instant fecha) {
    }

    void registrar(Evento evento);

    /** Eventos del usuario, del más reciente al más antiguo. */
    List<Evento> deUsuario(UUID usuarioId);
}
