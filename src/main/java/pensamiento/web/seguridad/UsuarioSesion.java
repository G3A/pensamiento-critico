package pensamiento.web.seguridad;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import pensamiento.nucleo.Titular;
import pensamiento.nucleo.Usuario;

/**
 * Lo que vive en la sesión del servidor: identidad, institución (con la ruta por la que entró), rol y etapa de la entrada.
 * La clave hasheada no sale de aquí. La sesión queda atada a esa institución: una petición a la ruta de otra se rechaza
 * (hito 8).
 *
 * <p>Hito 9: entrar tiene etapas. Quien entró con el PIN de las versiones anteriores primero elige su contraseña; un adulto
 * que administra (administrador, docente) confirma con su llave de acceso, o la registra si todavía no tiene. Mientras no
 * llegue a {@link Etapa#LISTA} solo tiene el rol PENDIENTE y nada de la app le responde salvo la pantalla de su etapa.
 */
public final class UsuarioSesion implements TitularDeSesion {

    public static final String ROL_ADMINISTRADOR = "ROLE_ADMINISTRADOR";
    public static final String ROL_PERSONA = "ROLE_PERSONA";
    public static final String ROL_PENDIENTE = "ROLE_PENDIENTE";
    /** Factores con los nombres de Spring Security 7 (FactorGrantedAuthority): contraseña y llave de acceso. */
    public static final String FACTOR_CONTRASENA = "FACTOR_PASSWORD";
    public static final String FACTOR_LLAVE = "FACTOR_WEBAUTHN";

    public enum Etapa {
        ELEGIR_CONTRASENA("/cuenta/contrasena-nueva"), REGISTRAR_LLAVE("/llave"), CONFIRMAR_LLAVE("/llave"), LISTA("/");

        private final String pantalla;

        Etapa(String pantalla) {
            this.pantalla = pantalla;
        }

        /** Adonde va quien está en esta etapa. */
        public String pantalla() {
            return pantalla;
        }
    }

    private final UUID id;
    private final UUID institucionId;
    private final String ruta;
    private final String nombre;
    private final Usuario.RolGlobal rol;
    private final boolean activo;
    private final String claveHash;
    private final Etapa etapa;
    private final boolean conLlave;

    public UsuarioSesion(Usuario usuario, String ruta) {
        this(usuario, ruta, usuario.tipoClave() == Usuario.TipoClave.PIN ? Etapa.ELEGIR_CONTRASENA : Etapa.LISTA, false);
    }

    public UsuarioSesion(Usuario usuario, String ruta, Etapa etapa, boolean conLlave) {
        this.id = usuario.id();
        this.institucionId = usuario.institucionId();
        this.ruta = ruta;
        this.nombre = usuario.nombre();
        this.rol = usuario.rol();
        this.activo = usuario.activo();
        this.claveHash = usuario.claveHash();
        this.etapa = etapa;
        this.conLlave = conLlave;
    }

    private UsuarioSesion(UsuarioSesion base, Etapa etapa, boolean conLlave) {
        this.id = base.id;
        this.institucionId = base.institucionId;
        this.ruta = base.ruta;
        this.nombre = base.nombre;
        this.rol = base.rol;
        this.activo = base.activo;
        this.claveHash = base.claveHash;
        this.etapa = etapa;
        this.conLlave = conLlave;
    }

    /** La misma sesión en otra etapa (por ejemplo, después de elegir la contraseña o de confirmar con la llave). */
    public UsuarioSesion enEtapa(Etapa nueva, boolean confirmoConLlave) {
        return new UsuarioSesion(this, nueva, confirmoConLlave);
    }

    public UUID id() {
        return id;
    }

    public UUID institucionId() {
        return institucionId;
    }

    /** La ruta de entrada de su institución: /<ruta>. */
    public String ruta() {
        return ruta;
    }

    public String nombre() {
        return nombre;
    }

    public Usuario.RolGlobal rol() {
        return rol;
    }

    public Etapa etapa() {
        return etapa;
    }

    @Override
    public boolean lista() {
        return etapa == Etapa.LISTA;
    }

    @Override
    public Titular titular() {
        return Titular.cuenta(id, institucionId);
    }

    @Override
    public String nombreVisible() {
        return nombre;
    }

    @Override
    public String baseDeLlave() {
        return "/llave";
    }

    @Override
    public String inicio() {
        return "/";
    }

    @Override
    public boolean debeRegistrarLlave() {
        return etapa == Etapa.REGISTRAR_LLAVE;
    }

    @Override
    public UsuarioSesion lista(boolean conLlave) {
        return enEtapa(Etapa.LISTA, conLlave);
    }

    public boolean esAdministrador() {
        return rol == Usuario.RolGlobal.ADMINISTRADOR;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        List<GrantedAuthority> autoridades = new ArrayList<>();
        autoridades.add(new SimpleGrantedAuthority(FACTOR_CONTRASENA));
        if (conLlave) {
            autoridades.add(new SimpleGrantedAuthority(FACTOR_LLAVE));
        }
        if (etapa != Etapa.LISTA) {
            autoridades.add(new SimpleGrantedAuthority(ROL_PENDIENTE));
        } else {
            autoridades.add(new SimpleGrantedAuthority(esAdministrador() ? ROL_ADMINISTRADOR : ROL_PERSONA));
        }
        return autoridades;
    }

    @Override
    public String getPassword() {
        return claveHash;
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

    /** Dos sesiones son de la misma persona si tienen el mismo id: así el registro de sesiones las agrupa (CierreDeSesiones). */
    @Override
    public boolean equals(Object otro) {
        return otro instanceof UsuarioSesion u && u.id.equals(id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "UsuarioSesion[" + id + "]";
    }
}
