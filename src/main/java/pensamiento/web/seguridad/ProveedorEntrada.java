package pensamiento.web.seguridad;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AuthenticationDetailsSource;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import pensamiento.nucleo.Usuario;
import pensamiento.nucleo.puertos.RegistroAuditoria;
import pensamiento.nucleo.puertos.Reloj;
import pensamiento.nucleo.puertos.RepositorioInstituciones;
import pensamiento.nucleo.puertos.RepositorioUsuarios;

/**
 * Entrar con nombre y contraseña dentro de una institución (hitos 8 y 9). La institución la fija la ruta de entrada, que el
 * formulario manda en el campo "institucion"; la persona se busca solo ahí, sin distinguir mayúsculas, tildes ni espacios.
 *
 * <p>Hito 9: antes de mirar la contraseña se consulta el límite de intentos (si hay espera, 429 aunque la contraseña sea
 * correcta). Si la cuenta no existe, no está activa o no tiene clave, se compara contra un hash de relleno: tarda lo mismo
 * y cuenta el fallo igual. Cada entrada, fallo y espera queda en la auditoría, nunca la contraseña. El overlay de sesión
 * expirada manda "persona": solo entra la misma persona que estaba en esa página. Quien todavía tiene un PIN de las
 * versiones anteriores entra con él una última vez, en la etapa de elegir su contraseña.
 */
public class ProveedorEntrada implements AuthenticationProvider {

    public static final String CAMPO_INSTITUCION = "institucion";
    public static final String CAMPO_PERSONA = "persona";

    /** Lo que el formulario de entrada aporta además del nombre y la contraseña. */
    public record DetallesEntrada(String ruta, Optional<UUID> institucion, Optional<UUID> persona, String ip) {
    }

    /** Hay que esperar antes de volver a intentar: responde 429 con cuánto falta. */
    public static class DemasiadosIntentos extends AuthenticationException {
        private final transient Duration espera;

        public DemasiadosIntentos(Duration espera) {
            super("Demasiados intentos");
            this.espera = espera;
        }

        public Duration espera() {
            return espera;
        }
    }

    /** La contraseña era correcta pero el overlay es de otra persona: no se cambia de persona sobre su página. */
    public static class OtraPersona extends AuthenticationException {
        public OtraPersona() {
            super("Esta página es de otra persona");
        }
    }

    private final RepositorioUsuarios usuarios;
    private final RepositorioInstituciones instituciones;
    private final PasswordEncoder codificador;
    private final TransactionTemplate tx;
    private final LimiteDeIntentos limite;
    private final RegistroAuditoria auditoria;
    private final Reloj reloj;
    private final EtapaInicial etapaInicial;
    private final String hashDeRelleno;

    /** Decide en qué etapa queda una persona que dio bien su contraseña (la llave de acceso la decide el hito 9, entregable 7). */
    @FunctionalInterface
    public interface EtapaInicial {
        UsuarioSesion.Etapa para(Usuario usuario);

        EtapaInicial SIN_LLAVE = u -> u.tipoClave() == Usuario.TipoClave.PIN ? UsuarioSesion.Etapa.ELEGIR_CONTRASENA : UsuarioSesion.Etapa.LISTA;
    }

    public ProveedorEntrada(RepositorioUsuarios usuarios, RepositorioInstituciones instituciones, PasswordEncoder codificador,
                            PlatformTransactionManager gestor, LimiteDeIntentos limite, RegistroAuditoria auditoria, Reloj reloj,
                            EtapaInicial etapaInicial) {
        this.usuarios = usuarios;
        this.instituciones = instituciones;
        this.codificador = codificador;
        this.tx = new TransactionTemplate(gestor);
        this.limite = limite;
        this.auditoria = auditoria;
        this.reloj = reloj;
        this.etapaInicial = etapaInicial;
        this.hashDeRelleno = codificador.encode("relleno-para-igualar-tiempos");
    }

    /** Lee la ruta del formulario y la resuelve a una institución activa (o a nada); también la persona del overlay y la IP. */
    public AuthenticationDetailsSource<HttpServletRequest, DetallesEntrada> detalles() {
        return request -> {
            String ruta = RutasReservadas.deInstitucion(request.getParameter(CAMPO_INSTITUCION)).orElse("");
            Optional<UUID> institucion = ruta.isEmpty() ? Optional.empty()
                    : ContextoRls.con(Optional.empty(), Optional.empty(), () -> tx.execute(e -> instituciones.activaPorRuta(ruta)));
            Optional<UUID> persona = Optional.ofNullable(request.getParameter(CAMPO_PERSONA)).flatMap(ConfiguracionSeguridad::uuid);
            return new DetallesEntrada(ruta, institucion == null ? Optional.empty() : institucion, persona, request.getRemoteAddr());
        };
    }

    @Override
    public boolean supports(Class<?> tipo) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(tipo);
    }

    @Override
    public Authentication authenticate(Authentication intento) throws AuthenticationException {
        String nombre = intento.getName() == null ? "" : intento.getName();
        String clave = intento.getCredentials() == null ? "" : intento.getCredentials().toString();
        if (!(intento.getDetails() instanceof DetallesEntrada detalles) || detalles.institucion().isEmpty()) {
            codificador.matches(clave, hashDeRelleno);
            throw new BadCredentialsException("Sin institución activa en la ruta de entrada");
        }
        UUID institucion = detalles.institucion().get();
        Optional<Duration> espera = sinPersona(() -> limite.espera(institucion, nombre, detalles.ip()));
        if (espera.isPresent()) {
            auditar(institucion, Optional.empty(), RegistroAuditoria.Accion.ESPERA_ENTRAR, nombre);
            throw new DemasiadosIntentos(espera.get());
        }
        Optional<Usuario> cuenta = ContextoRls.conInstitucion(institucion, () -> tx.execute(e -> usuarios.porNombre(institucion, nombre)));
        Optional<Usuario> valida = cuenta == null ? Optional.empty() : cuenta.filter(u -> u.activo() && u.tieneClave());
        boolean coincide = codificador.matches(clave, valida.map(Usuario::claveHash).orElse(hashDeRelleno)) && valida.isPresent();
        if (!coincide) {
            sinPersona(() -> {
                limite.fallo(institucion, nombre, detalles.ip());
                return null;
            });
            auditar(institucion, valida.map(Usuario::id), RegistroAuditoria.Accion.FALLO_ENTRAR, nombre);
            throw new BadCredentialsException("El nombre o la contraseña no coinciden");
        }
        Usuario usuario = valida.get();
        if (detalles.persona().isPresent() && !detalles.persona().get().equals(usuario.id())) {
            throw new OtraPersona();
        }
        sinPersona(() -> {
            limite.exito(institucion, nombre);
            return null;
        });
        auditar(institucion, Optional.of(usuario.id()), RegistroAuditoria.Accion.ENTRAR, nombre);
        UsuarioSesion sesion = new UsuarioSesion(usuario, detalles.ruta(), etapaInicial.para(usuario), false);
        UsernamePasswordAuthenticationToken resultado = UsernamePasswordAuthenticationToken.authenticated(sesion, null, sesion.getAuthorities());
        resultado.setDetails(detalles);
        return resultado;
    }

    private void auditar(UUID institucion, Optional<UUID> usuario, RegistroAuditoria.Accion accion, String nombre) {
        ContextoRls.conInstitucion(institucion, () -> tx.execute(e -> {
            auditoria.registrar(new RegistroAuditoria.Evento(usuario, institucion, accion, "usuario", usuario, reloj.ahora(),
                    Optional.of(Usuario.claveDeNombre(nombre))));
            return null;
        }));
    }

    private <T> T sinPersona(Supplier<T> accion) {
        return ContextoRls.con(Optional.empty(), Optional.empty(), () -> tx.execute(e -> accion.get()));
    }
}
