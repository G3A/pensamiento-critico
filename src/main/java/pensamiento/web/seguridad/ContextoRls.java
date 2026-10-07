package pensamiento.web.seguridad;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Quién es el usuario y la institución de la transacción actual. Por defecto sale de la sesión de
 * Spring Security; fuera de una petición (arranque, inicio de sesión, pruebas) se fija de forma explícita.
 */
public final class ContextoRls {

    public record Contexto(Optional<UUID> usuarioId, Optional<UUID> institucionId) {
        public static final Contexto VACIO = new Contexto(Optional.empty(), Optional.empty());
    }

    private static final ThreadLocal<Contexto> EXPLICITO = new ThreadLocal<>();

    private ContextoRls() {
    }

    public static Contexto actual() {
        Contexto explicito = EXPLICITO.get();
        if (explicito != null) {
            return explicito;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UsuarioSesion usuario) {
            return new Contexto(Optional.of(usuario.id()), Optional.of(usuario.institucionId()));
        }
        return Contexto.VACIO;
    }

    public static Optional<UsuarioSesion> usuarioDeSesion() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UsuarioSesion usuario) {
            return Optional.of(usuario);
        }
        return Optional.empty();
    }

    /** Ejecuta con un contexto explícito (por ejemplo, solo institución durante el inicio de sesión). */
    public static <T> T con(Optional<UUID> usuarioId, Optional<UUID> institucionId, Supplier<T> accion) {
        Contexto previo = EXPLICITO.get();
        EXPLICITO.set(new Contexto(usuarioId, institucionId));
        try {
            return accion.get();
        } finally {
            if (previo == null) {
                EXPLICITO.remove();
            } else {
                EXPLICITO.set(previo);
            }
        }
    }

    public static <T> T conInstitucion(UUID institucionId, Supplier<T> accion) {
        return con(Optional.empty(), Optional.of(institucionId), accion);
    }

    public static <T> T conUsuario(UUID usuarioId, UUID institucionId, Supplier<T> accion) {
        return con(Optional.of(usuarioId), Optional.of(institucionId), accion);
    }
}
