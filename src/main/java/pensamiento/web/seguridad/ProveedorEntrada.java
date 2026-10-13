package pensamiento.web.seguridad;

import java.util.Optional;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AuthenticationDetailsSource;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.AbstractUserDetailsAuthenticationProvider;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import pensamiento.nucleo.puertos.RepositorioInstituciones;
import pensamiento.nucleo.puertos.RepositorioUsuarios;

/**
 * Entrar con nombre y PIN dentro de una institución (hito 8): la institución la fija la ruta de entrada, que el formulario
 * manda en el campo "institucion". Sin institución activa con esa ruta, nadie entra. La persona se busca solo dentro de
 * esa institución (RLS fijada antes de abrir la transacción) y, si no existe, se compara el PIN contra un hash de relleno
 * para que tarde lo mismo.
 */
public class ProveedorEntrada extends AbstractUserDetailsAuthenticationProvider {

    public static final String CAMPO_INSTITUCION = "institucion";

    /** Lo que el formulario de entrada aporta además del nombre y el PIN: la ruta y la institución que resolvió. */
    public record DetallesEntrada(String ruta, Optional<UUID> institucion) {
    }

    private final RepositorioUsuarios usuarios;
    private final RepositorioInstituciones instituciones;
    private final PasswordEncoder codificador;
    private final TransactionTemplate tx;
    private final String hashDeRelleno;

    public ProveedorEntrada(RepositorioUsuarios usuarios, RepositorioInstituciones instituciones, PasswordEncoder codificador,
                            PlatformTransactionManager gestor) {
        this.usuarios = usuarios;
        this.instituciones = instituciones;
        this.codificador = codificador;
        this.tx = new TransactionTemplate(gestor);
        this.tx.setReadOnly(true);
        this.hashDeRelleno = codificador.encode("relleno-para-igualar-tiempos");
    }

    /** Lee la ruta del formulario y la resuelve a una institución activa (o a nada). */
    public AuthenticationDetailsSource<HttpServletRequest, DetallesEntrada> detalles() {
        return request -> {
            String ruta = RutasReservadas.deInstitucion(request.getParameter(CAMPO_INSTITUCION)).orElse("");
            Optional<UUID> institucion = ruta.isEmpty() ? Optional.empty()
                    : ContextoRls.con(Optional.empty(), Optional.empty(), () -> tx.execute(e -> instituciones.activaPorRuta(ruta)));
            return new DetallesEntrada(ruta, institucion == null ? Optional.empty() : institucion);
        };
    }

    @Override
    protected UserDetails retrieveUser(String nombre, UsernamePasswordAuthenticationToken autenticacion) throws AuthenticationException {
        if (!(autenticacion.getDetails() instanceof DetallesEntrada detalles) || detalles.institucion().isEmpty()) {
            igualarTiempo(autenticacion);
            throw new UsernameNotFoundException("Sin institución activa en la ruta de entrada");
        }
        UUID institucion = detalles.institucion().get();
        Optional<UsuarioSesion> encontrado = ContextoRls.conInstitucion(institucion, () -> tx.execute(e ->
                usuarios.porNombre(institucion, nombre).filter(u -> u.activo()).map(u -> new UsuarioSesion(u, detalles.ruta()))));
        if (encontrado == null || encontrado.isEmpty()) {
            igualarTiempo(autenticacion);
            throw new UsernameNotFoundException("No existe esa persona en la institución");
        }
        return encontrado.get();
    }

    @Override
    protected void additionalAuthenticationChecks(UserDetails usuario, UsernamePasswordAuthenticationToken autenticacion)
            throws AuthenticationException {
        Object pin = autenticacion.getCredentials();
        if (pin == null || !codificador.matches(pin.toString(), usuario.getPassword())) {
            throw new BadCredentialsException("Nombre o PIN incorrectos");
        }
    }

    private void igualarTiempo(UsernamePasswordAuthenticationToken autenticacion) {
        Object pin = autenticacion.getCredentials();
        codificador.matches(pin == null ? "" : pin.toString(), hashDeRelleno);
    }
}
