package pensamiento.nucleo.puertos;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Tabla auditoria: solo inserción. Se escribe al crear, importar, exportar, compartir, borrar y desactivar, y desde el
 * hito 9 en todo lo que pasa con las cuentas y al entrar (cada entrada, cada fallo y cada espera). Nunca guarda una
 * contraseña: {@code detalle} lleva a lo sumo la clave del nombre con que se probó.
 */
public interface RegistroAuditoria {

    enum Accion {
        CREAR, IMPORTAR, EXPORTAR, COMPARTIR, BORRAR, DESACTIVAR, SESION,
        REACTIVAR, CODIGO_ALTA, CODIGO_RESTABLECER, CANJEAR_CODIGO, CAMBIAR_CLAVE, ENTRAR, FALLO_ENTRAR, ESPERA_ENTRAR,
        LEVANTAR_ESPERA, REGISTRAR_LLAVE, USAR_RECUPERACION
    }

    record Evento(Optional<UUID> usuarioId, UUID institucionId, Accion accion, String objetoTipo, Optional<UUID> objetoId, Instant fecha,
                  Optional<String> detalle) {

        public Evento(Optional<UUID> usuarioId, UUID institucionId, Accion accion, String objetoTipo, Optional<UUID> objetoId, Instant fecha) {
            this(usuarioId, institucionId, accion, objetoTipo, objetoId, fecha, Optional.empty());
        }
    }

    void registrar(Evento evento);

    /** Eventos del usuario, del más reciente al más antiguo. */
    List<Evento> deUsuario(UUID usuarioId);
}
