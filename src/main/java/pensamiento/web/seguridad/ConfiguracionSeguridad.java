package pensamiento.web.seguridad;

import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import pensamiento.nucleo.puertos.RepositorioUsuarios;

/**
 * "Casa con llave": sesión en servidor, PIN obligatorio con Argon2, CSRF con el token en las cabeceras
 * de htmx, CSP script-src 'self' sin inline ni eval, sin autoregistro.
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

    /** Carga por nombre dentro de la institución única; RLS exige fijar la institución antes de consultar. */
    @Bean
    UserDetailsService userDetailsService(RepositorioUsuarios usuarios, PlatformTransactionManager tx) {
        TransactionTemplate plantilla = new TransactionTemplate(tx);
        return nombre -> {
            UUID institucion = usuarios.institucionUnica()
                    .orElseThrow(() -> new UsernameNotFoundException("La instalación no tiene institución"));
            return ContextoRls.conInstitucion(institucion, () -> plantilla.execute(estado ->
                    usuarios.porNombre(institucion, nombre)
                            .filter(u -> u.activo())
                            .map(UsuarioSesion::new)
                            .orElseThrow(() -> new UsernameNotFoundException("No existe la persona " + nombre))));
        };
    }

    @Bean
    SecurityFilterChain cadena(HttpSecurity http, RespuestasSesion respuestas) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/bloqueo", "/sesion", "/salir", "/error").permitAll()
                .requestMatchers("/htmx.min.js", "/alpine-csp.min.js", "/app.css", "/app.js", "/ext/*.js", "/favicon.svg").permitAll()
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/usuarios", "/usuarios/**").hasRole("ADMINISTRADOR")
                .anyRequest().authenticated())
            .formLogin(login -> login
                .loginPage("/bloqueo")
                .loginProcessingUrl("/sesion")
                .usernameParameter("nombre")
                .passwordParameter("pin")
                .successHandler(respuestas.alEntrar())
                .failureHandler(respuestas.alFallar()))
            .logout(salir -> salir
                .logoutUrl("/salir")
                .logoutSuccessUrl("/bloqueo")
                .invalidateHttpSession(true)
                .deleteCookies("PENSAMIENTO_SESION"))
            .exceptionHandling(ex -> ex.authenticationEntryPoint(respuestas.alNoEstarAutenticado()))
            .sessionManagement(sesion -> sesion.sessionFixation().migrateSession())
            .headers(cabeceras -> cabeceras
                .contentSecurityPolicy(csp -> csp.policyDirectives(CSP))
                .frameOptions(frame -> frame.deny())
                .referrerPolicy(ref -> ref.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN)));
        return http.build();
    }

    static Optional<UUID> uuid(String valor) {
        try {
            return Optional.of(UUID.fromString(valor));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
