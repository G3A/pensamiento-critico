package pensamiento.web.seguridad;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.sql.DataSource;

import jakarta.servlet.http.HttpServletRequest;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.webauthn.api.AuthenticatorSelectionCriteria;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRpEntity;
import org.springframework.security.web.webauthn.api.ResidentKeyRequirement;
import org.springframework.security.web.webauthn.api.UserVerificationRequirement;
import org.springframework.security.web.webauthn.management.Webauthn4JRelyingPartyOperations;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import pensamiento.nucleo.Titular;
import pensamiento.nucleo.Usuario;
import pensamiento.nucleo.puertos.RegistroAuditoria;
import pensamiento.nucleo.puertos.RepositorioLlaves;
import pensamiento.nucleo.puertos.RegistroIntentos;
import pensamiento.nucleo.puertos.Reloj;
import pensamiento.nucleo.puertos.RepositorioInstituciones;
import pensamiento.nucleo.puertos.RepositorioUsuarios;

/**
 * "Casa con llave": sesión en servidor, contraseña obligatoria con Argon2 (hito 9; antes, PIN), CSRF con el token en las cabeceras
 * de htmx, CSP script-src 'self' sin inline ni eval, sin autoregistro. Desde el hito 8 se entra por la ruta de cada
 * institución (/<ruta>) y la sesión queda atada a ella.
 */
@Configuration
@EnableWebSecurity
public class ConfiguracionSeguridad {

    public static final String CSP = "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; "
            + "font-src 'self'; connect-src 'self'; object-src 'none'; frame-ancestors 'none'; form-action 'self'; base-uri 'self'";

    @Bean
    PlatformTransactionManager transactionManager(DataSource dataSource) {
        return new GestorTransaccionesRls(dataSource);
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    /** Registro en memoria de las sesiones abiertas, para cerrarlas al restablecer o desactivar una cuenta (hito 9). */
    @Bean
    SessionRegistry registroDeSesiones() {
        return new SessionRegistryImpl();
    }

    /** Avisa al registro cuando una sesión se destruye (expira o se sale), para que no crezca sin fin. */
    @Bean
    HttpSessionEventPublisher publicadorDeSesiones() {
        return new HttpSessionEventPublisher();
    }

    /** Retardo progresivo por cuenta y topes por IP con umbrales configurables (hito 9, RF-15). */
    @Bean
    LimiteDeIntentos limiteDeIntentos(RegistroIntentos registro, Reloj reloj,
                                      @Value("${app.intentos.ip-institucion.umbral:300}") int umbralIpInstitucion,
                                      @Value("${app.intentos.ip-institucion.ventana:PT10M}") Duration ventanaIpInstitucion,
                                      @Value("${app.intentos.ip-global.umbral:1000}") int umbralIpGlobal,
                                      @Value("${app.intentos.ip-global.ventana:PT10M}") Duration ventanaIpGlobal) {
        return new LimiteDeIntentos(registro, reloj, new LimiteDeIntentos.Tope(umbralIpInstitucion, ventanaIpInstitucion),
                new LimiteDeIntentos.Tope(umbralIpGlobal, ventanaIpGlobal));
    }

    /**
     * Llave de acceso (hito 9): la verificación de WebAuthn de Spring Security 7 sobre el puerto RepositorioLlaves. El "rp id"
     * y los orígenes permitidos son configurables: en el equipo local, localhost y http://localhost:8080.
     */
    @Bean
    ServicioLlaves servicioLlaves(RepositorioLlaves llaves, PasswordEncoder codificador, Reloj reloj,
                                  @Value("${app.llave.rp-id:localhost}") String rpId,
                                  @Value("${app.llave.origenes:http://localhost:8080}") List<String> origenes) {
        RepositoriosWebAuthn repositorios = new RepositoriosWebAuthn(llaves);
        Webauthn4JRelyingPartyOperations operaciones = new Webauthn4JRelyingPartyOperations(repositorios, repositorios,
                PublicKeyCredentialRpEntity.builder().id(rpId).name("Taller de Pensamiento Crítico").build(), Set.copyOf(origenes));
        operaciones.setCustomizeCreationOptions(o -> o.authenticatorSelection(AuthenticatorSelectionCriteria.builder()
                .residentKey(ResidentKeyRequirement.PREFERRED).userVerification(UserVerificationRequirement.PREFERRED).build()));
        java.security.SecureRandom azar = new java.security.SecureRandom();
        return new ServicioLlaves(llaves, operaciones, codificador, reloj, azar::nextInt);
    }

    /** Nombre y contraseña dentro de la institución de la ruta de entrada (hitos 8 y 9). */
    @Bean
    ProveedorEntrada proveedorEntrada(RepositorioUsuarios usuarios, RepositorioInstituciones instituciones, PasswordEncoder codificador,
                                      PlatformTransactionManager tx, LimiteDeIntentos limite, RegistroAuditoria auditoria, Reloj reloj,
                                      ServicioLlaves llaves) {
        TransactionTemplate lectura = new TransactionTemplate(tx);
        ProveedorEntrada.EtapaInicial etapa = u -> {
            if (u.tipoClave() == Usuario.TipoClave.PIN) {
                return UsuarioSesion.Etapa.ELEGIR_CONTRASENA;
            }
            if (!u.rol().exigeLlave()) {
                return UsuarioSesion.Etapa.LISTA;
            }
            Boolean tiene = ContextoRls.conInstitucion(u.institucionId(), () -> lectura.execute(e ->
                    llaves.tieneLlave(Titular.cuenta(u.id(), u.institucionId()))));
            return Boolean.TRUE.equals(tiene) ? UsuarioSesion.Etapa.CONFIRMAR_LLAVE : UsuarioSesion.Etapa.REGISTRAR_LLAVE;
        };
        return new ProveedorEntrada(usuarios, instituciones, codificador, tx, limite, auditoria, reloj, etapa);
    }

    @Bean
    SecurityFilterChain cadena(HttpSecurity http, RespuestasSesion respuestas, ProveedorEntrada proveedor, SessionRegistry sesiones)
            throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/ir", "/bloqueo", "/sesion", RespuestasSesion.RUTA_RECHAZO, "/salir", "/error").permitAll()
                .requestMatchers(ConfiguracionSeguridad::esEntradaDeInstitucion).permitAll()
                .requestMatchers(ConfiguracionSeguridad::esSubrutaPublica).permitAll()
                .requestMatchers("/htmx.min.js", "/alpine-csp.min.js", "/app.css", "/app.js", "/llave.js", "/borrador.js", "/ext/*.js", "/favicon.svg").permitAll()
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/actuator/**").hasRole("OPERADOR")      // métricas: solo el operador del servicio
                .requestMatchers("/cuenta/contrasena-nueva").hasRole("PENDIENTE")   // quien entró con PIN elige su contraseña
                .requestMatchers("/llave", "/llave/**").hasAnyRole("PENDIENTE", "ADMINISTRADOR", "DOCENTE")   // registrar o confirmar su llave
                .requestMatchers("/usuarios", "/usuarios/**", "/administracion/**").hasRole("ADMINISTRADOR")
                .requestMatchers("/grupos", "/grupos/**").hasAnyRole("ADMINISTRADOR", "DOCENTE")   // hito 10: el aula
                .anyRequest().hasAnyRole("ADMINISTRADOR", "DOCENTE", "PERSONA", "ESTUDIANTE"))   // el operador y quien no terminó de entrar, no
            .formLogin(login -> login
                .loginPage("/bloqueo")
                .authenticationDetailsSource(proveedor.detalles())
                .loginProcessingUrl("/sesion")
                .usernameParameter("nombre")
                .passwordParameter("contrasena")
                .successHandler(respuestas.alEntrar())
                .failureHandler(respuestas.alFallar()))
            .logout(salir -> salir
                .logoutUrl("/salir")
                .logoutSuccessHandler(respuestas.alSalir())
                .invalidateHttpSession(true)
                .deleteCookies("PENSAMIENTO_SESION"))
            // Un ProviderManager propio y sin padre: si no, el AuthenticationManager global (que también conoce este bean)
            // volvería a evaluar cada intento fallido, que contaría doble en el límite de intentos.
            .authenticationManager(new ProviderManager(proveedor))
            .addFilterBefore(new FiltroInstitucionDeSesion(), UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(new FiltroPersonaDeLaPagina(respuestas), AuthorizationFilter.class)
            .exceptionHandling(ex -> ex.authenticationEntryPoint(respuestas.alNoEstarAutenticado())
                .accessDeniedHandler(respuestas.alNoTenerPermiso()))
            .sessionManagement(sesion -> sesion.sessionFixation().migrateSession()
                .maximumSessions(-1)
                .sessionRegistry(sesiones)
                .expiredSessionStrategy(respuestas.alCerrarseDesdeAfuera()))
            .headers(cabeceras -> cabeceras
                .contentSecurityPolicy(csp -> csp.policyDirectives(CSP))
                .frameOptions(frame -> frame.deny())
                .referrerPolicy(ref -> ref.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN)));
        return http.build();
    }

    /** GET /<ruta>: la pantalla de entrada de una institución (el controlador responde 404 si no existe o está suspendida). */
    static boolean esEntradaDeInstitucion(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return "GET".equals(request.getMethod()) && uri.length() > 1 && uri.indexOf('/', 1) < 0
                && RutasReservadas.deInstitucion(uri.substring(1)).isPresent();
    }

    /**
     * GET y POST /<ruta>/invitacion (el primer administrador canjea la invitación del operador, hito 8), /<ruta>/codigo (una
     * persona canjea su código de alta o de restablecimiento) y GET /<ruta>/sin-cuenta (a quién pedir la cuenta), hito 9.
     */
    static boolean esSubrutaPublica(HttpServletRequest request) {
        return RutasReservadas.subrutaPublica(request.getRequestURI()).isPresent();
    }

    static Optional<UUID> uuid(String valor) {
        try {
            return Optional.of(UUID.fromString(valor));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
