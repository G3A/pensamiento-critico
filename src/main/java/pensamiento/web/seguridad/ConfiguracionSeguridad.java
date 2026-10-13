package pensamiento.web.seguridad;

import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.transaction.PlatformTransactionManager;

import pensamiento.nucleo.puertos.RepositorioInstituciones;
import pensamiento.nucleo.puertos.RepositorioUsuarios;

/**
 * "Casa con llave": sesión en servidor, PIN obligatorio con Argon2, CSRF con el token en las cabeceras
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

    /** Nombre y PIN dentro de la institución de la ruta de entrada (hito 8). */
    @Bean
    ProveedorEntrada proveedorEntrada(RepositorioUsuarios usuarios, RepositorioInstituciones instituciones, PasswordEncoder codificador,
                                      PlatformTransactionManager tx) {
        return new ProveedorEntrada(usuarios, instituciones, codificador, tx);
    }

    @Bean
    SecurityFilterChain cadena(HttpSecurity http, RespuestasSesion respuestas, ProveedorEntrada proveedor) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/ir", "/bloqueo", "/sesion", "/salir", "/error").permitAll()
                .requestMatchers(ConfiguracionSeguridad::esEntradaDeInstitucion).permitAll()
                .requestMatchers(ConfiguracionSeguridad::esCanjeDeInvitacion).permitAll()
                .requestMatchers("/htmx.min.js", "/alpine-csp.min.js", "/app.css", "/app.js", "/ext/*.js", "/favicon.svg").permitAll()
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/actuator/**").hasRole("OPERADOR")      // métricas: solo el operador del servicio
                .requestMatchers("/usuarios", "/usuarios/**", "/administracion/**").hasRole("ADMINISTRADOR")
                .anyRequest().hasAnyRole("ADMINISTRADOR", "PERSONA"))   // el operador no entra a nada de una institución
            .formLogin(login -> login
                .loginPage("/bloqueo")
                .authenticationDetailsSource(proveedor.detalles())
                .loginProcessingUrl("/sesion")
                .usernameParameter("nombre")
                .passwordParameter("pin")
                .successHandler(respuestas.alEntrar())
                .failureHandler(respuestas.alFallar()))
            .logout(salir -> salir
                .logoutUrl("/salir")
                .logoutSuccessHandler(respuestas.alSalir())
                .invalidateHttpSession(true)
                .deleteCookies("PENSAMIENTO_SESION"))
            .authenticationProvider(proveedor)
            .addFilterBefore(new FiltroInstitucionDeSesion(), UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(ex -> ex.authenticationEntryPoint(respuestas.alNoEstarAutenticado()))
            .sessionManagement(sesion -> sesion.sessionFixation().migrateSession())
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

    /** GET y POST /<ruta>/invitacion: el primer administrador canjea la invitación del operador (hito 8). */
    static boolean esCanjeDeInvitacion(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String sufijo = "/invitacion";
        return uri.endsWith(sufijo) && uri.length() > sufijo.length() + 1
                && uri.indexOf('/', 1) == uri.length() - sufijo.length()
                && RutasReservadas.deInstitucion(uri.substring(1, uri.length() - sufijo.length())).isPresent();
    }

    static Optional<UUID> uuid(String valor) {
        try {
            return Optional.of(UUID.fromString(valor));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
