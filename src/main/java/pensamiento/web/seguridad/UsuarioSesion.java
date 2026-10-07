package pensamiento.web.seguridad;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import pensamiento.nucleo.Usuario;

/** Lo que vive en la sesión del servidor: identidad, institución y rol. El PIN hasheado no sale de aquí. */
public final class UsuarioSesion implements UserDetails {

    public static final String ROL_ADMINISTRADOR = "ROLE_ADMINISTRADOR";
    public static final String ROL_PERSONA = "ROLE_PERSONA";

    private final UUID id;
    private final UUID institucionId;
    private final String nombre;
    private final Usuario.RolGlobal rol;
    private final boolean activo;
    private final String pinHash;

    public UsuarioSesion(Usuario usuario) {
        this.id = usuario.id();
        this.institucionId = usuario.institucionId();
        this.nombre = usuario.nombre();
        this.rol = usuario.rol();
        this.activo = usuario.activo();
        this.pinHash = usuario.pinHash();
    }

    public UUID id() {
        return id;
    }

    public UUID institucionId() {
        return institucionId;
    }

    public String nombre() {
        return nombre;
    }

    public boolean esAdministrador() {
        return rol == Usuario.RolGlobal.ADMINISTRADOR;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(esAdministrador() ? ROL_ADMINISTRADOR : ROL_PERSONA));
    }

    @Override
    public String getPassword() {
        return pinHash;
    }

    @Override
    public String getUsername() {
        return nombre;
    }

    @Override
    public boolean isEnabled() {
        return activo;
    }

    @Override
    public boolean isAccountNonLocked() {
        return activo;
    }
}
