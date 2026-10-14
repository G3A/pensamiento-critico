package pensamiento.web.seguridad;

import java.util.UUID;
import java.util.function.Consumer;

import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;

/**
 * Cierra todas las sesiones abiertas de una persona (hito 9, RF-17): al restablecer su contraseña, al canjear un código y
 * al desactivarla. Usa el registro de sesiones de Spring Security, en memoria: alcanza mientras el servicio corra en un
 * solo proceso. La sesión cerrada recibe 401 en su siguiente petición (ConfiguracionSeguridad, expiredSessionStrategy).
 */
public final class CierreDeSesiones implements Consumer<UUID> {

    private final SessionRegistry registro;

    public CierreDeSesiones(SessionRegistry registro) {
        this.registro = registro;
    }

    @Override
    public void accept(UUID usuarioId) {
        for (Object principal : registro.getAllPrincipals()) {
            if (principal instanceof UsuarioSesion u && u.id().equals(usuarioId)) {
                registro.getAllSessions(principal, false).forEach(SessionInformation::expireNow);
            }
        }
    }
}
