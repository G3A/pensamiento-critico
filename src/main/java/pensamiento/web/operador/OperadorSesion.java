package pensamiento.web.operador;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import pensamiento.nucleo.Titular;
import pensamiento.web.seguridad.TitularDeSesion;
import pensamiento.web.seguridad.UsuarioSesion;

/**
 * La sesión del operador del servicio: sin institución. ContextoRls no fija ni usuario ni institución para ella, así que
 * bajo RLS no ve ninguna fila de ninguna institución; y las rutas de la app exigen el rol de una persona de institución.
 * Hito 9: después de su clave confirma con su llave de acceso (o la registra la primera vez); mientras tanto solo tiene el
 * rol OPERADOR_PENDIENTE y nada de /operador le responde salvo /operador/llave.
 */
public final class OperadorSesion implements TitularDeSesion {

    public static final String ROL = "ROLE_OPERADOR";
    public static final String ROL_PENDIENTE = "ROLE_OPERADOR_PENDIENTE";

    /** Dónde está la entrada del operador: registrar la llave, confirmarla o ya dentro. */
    public enum Etapa { REGISTRAR_LLAVE, CONFIRMAR_LLAVE, LISTA }

    private final UUID id;
    private final String claveHash;
    private final Etapa etapa;
    private final boolean conLlave;

    public OperadorSesion(UUID id, String claveHash) {
        this(id, claveHash, Etapa.LISTA, false);
    }

    public OperadorSesion(UUID id, String claveHash, Etapa etapa, boolean conLlave) {
        this.id = id;
        this.claveHash = claveHash;
        this.etapa = etapa;
        this.conLlave = conLlave;
    }

    public UUID id() {
        return id;
    }

    public Etapa etapa() {
        return etapa;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        List<GrantedAuthority> autoridades = new ArrayList<>();
        autoridades.add(new SimpleGrantedAuthority(UsuarioSesion.FACTOR_CONTRASENA));
        if (conLlave) {
            autoridades.add(new SimpleGrantedAuthority(UsuarioSesion.FACTOR_LLAVE));
        }
        autoridades.add(new SimpleGrantedAuthority(etapa == Etapa.LISTA ? ROL : ROL_PENDIENTE));
        return autoridades;
    }

    @Override
    public String getPassword() {
        return claveHash;
    }

    @Override
    public String getUsername() {
        return ServicioOperador.NOMBRE_OPERADOR;
    }

    @Override
    public Titular titular() {
        return Titular.operador(id);
    }

    @Override
    public String nombreVisible() {
        return "operador del servicio";
    }

    @Override
    public String baseDeLlave() {
        return "/operador/llave";
    }

    @Override
    public String inicio() {
        return "/operador";
    }

    @Override
    public boolean debeRegistrarLlave() {
        return etapa == Etapa.REGISTRAR_LLAVE;
    }

    @Override
    public boolean lista() {
        return etapa == Etapa.LISTA;
    }

    @Override
    public OperadorSesion lista(boolean confirmoConLlave) {
        return new OperadorSesion(id, claveHash, Etapa.LISTA, confirmoConLlave);
    }

    @Override
    public boolean equals(Object otro) {
        return otro instanceof OperadorSesion o && o.id.equals(id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "OperadorSesion[" + id + "]";
    }
}
