package pensamiento.web.operador;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * La sesión del operador del servicio: sin institución. ContextoRls no fija ni usuario ni institución para ella, así que
 * bajo RLS no ve ninguna fila de ninguna institución; y las rutas de la app exigen el rol de una persona de institución.
 */
public final class OperadorSesion implements UserDetails {

    public static final String ROL = "ROLE_OPERADOR";

    private final UUID id;
    private final String claveHash;

    public OperadorSesion(UUID id, String claveHash) {
        this.id = id;
        this.claveHash = claveHash;
    }

    public UUID id() {
        return id;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(ROL));
    }

    @Override
    public String getPassword() {
        return claveHash;
    }

    @Override
    public String getUsername() {
        return ServicioOperador.NOMBRE_OPERADOR;
    }
}
