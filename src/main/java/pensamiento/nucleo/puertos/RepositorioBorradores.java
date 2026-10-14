package pensamiento.nucleo.puertos;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * La copia en el servidor de lo que una persona está escribiendo (hito 10, entregable 8): un borrador por persona y formulario,
 * que se reemplaza en cada guardado y se borra al enviar. Cada método recibe la persona de la sesión y solo toca lo suyo; la
 * implementación real además corre bajo RLS por usuario.
 */
public interface RepositorioBorradores {

    /** Lo guardado: campo → valor, y cuándo. */
    record Borrador(String clave, Map<String, String> campos, Instant actualizadoEn) {
    }

    /** Guarda o reemplaza el borrador de ese formulario. */
    void guardar(UUID usuarioId, UUID institucionId, String clave, Map<String, String> campos, Instant ahora);

    Optional<Borrador> leer(UUID usuarioId, String clave);

    /** Lo borra; false si no había. */
    boolean borrar(UUID usuarioId, String clave);
}
