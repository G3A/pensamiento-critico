package pensamiento.web.operador;

import java.util.Optional;
import java.util.UUID;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.AbstractUserDetailsAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import pensamiento.nucleo.Titular;
import pensamiento.nucleo.puertos.Reloj;
import pensamiento.nucleo.puertos.RepositorioInstituciones;
import pensamiento.nucleo.puertos.RepositorioOperador;
import pensamiento.web.seguridad.ConfiguracionSeguridad;
import pensamiento.web.seguridad.ContextoRls;
import pensamiento.web.seguridad.RespuestasSesion;
import pensamiento.web.seguridad.ServicioLlaves;
import pensamiento.web.usuarios.CodigoDeUnUso;
import pensamiento.web.usuarios.ServicioUsuarios;

/**
 * El operador del servicio entra por /operador con su propia cadena de seguridad (antes que la de las instituciones):
 * formulario de clave, sesión sin institución y las mismas cabeceras (CSP, marcos, referer). Todo lo de /operador exige
 * el rol de operador; todo lo demás exige el rol de una persona de institución (ConfiguracionSeguridad).
 */
@Configuration
public class ConfiguracionOperador {

    @Bean
    ServicioOperador servicioOperador(RepositorioOperador operador, RepositorioInstituciones instituciones, ServicioUsuarios usuarios,
                                      PasswordEncoder codificador, Reloj reloj) {
        return new ServicioOperador(operador, instituciones, usuarios, codificador, reloj, CodigoDeUnUso.aleatorio());
    }

    @Bean
    @Order(1)
    SecurityFilterChain cadenaOperador(HttpSecurity http, RepositorioOperador operador, PasswordEncoder codificador,
                                       PlatformTransactionManager gestor, ServicioLlaves llaves) throws Exception {
        http
            .securityMatcher("/operador", "/operador/**")
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/operador/entrar").permitAll()
                .requestMatchers("/operador/llave", "/operador/llave/**").hasAnyRole("OPERADOR_PENDIENTE", "OPERADOR")   // hito 9
                .anyRequest().hasRole("OPERADOR"))
            .formLogin(login -> login
                .loginPage("/operador/entrar")
                .loginProcessingUrl("/operador/sesion")
                .usernameParameter("nombre")
                .passwordParameter("clave")
                .successHandler((request, response, auth) -> response.sendRedirect(
                        auth.getPrincipal() instanceof OperadorSesion o && !o.lista() ? o.baseDeLlave() : "/operador"))
                .failureUrl("/operador/entrar?error"))
            .logout(salir -> salir
                .logoutUrl("/operador/salir")
                .logoutSuccessUrl("/operador/entrar")
                .invalidateHttpSession(true)
                .deleteCookies("PENSAMIENTO_SESION"))
            .authenticationManager(new ProviderManager(new ProveedorOperador(operador, codificador, gestor, llaves)))   // sin padre (hito 9)
            .exceptionHandling(ex -> ex.accessDeniedHandler((request, response, negado) -> {
                if (SecurityContextHolder.getContext().getAuthentication() != null
                        && SecurityContextHolder.getContext().getAuthentication().getPrincipal() instanceof OperadorSesion o && !o.lista()) {
                    RespuestasSesion.redirigir(request, response, o.baseDeLlave());
                } else {
                    response.sendError(403);
                }
            }))
            .sessionManagement(sesion -> sesion.sessionFixation().migrateSession())
            .headers(cabeceras -> cabeceras
                .contentSecurityPolicy(csp -> csp.policyDirectives(ConfiguracionSeguridad.CSP))
                .frameOptions(frame -> frame.deny())
                .referrerPolicy(ref -> ref.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN)));
        return http.build();
    }

    /** Clave del operador contra su hash Argon2; si el nombre no existe, compara contra un hash de relleno (mismo tiempo). */
    static final class ProveedorOperador extends AbstractUserDetailsAuthenticationProvider {

        private final RepositorioOperador operador;
        private final PasswordEncoder codificador;
        private final TransactionTemplate tx;
        private final String hashDeRelleno;
        private final ServicioLlaves llaves;

        ProveedorOperador(RepositorioOperador operador, PasswordEncoder codificador, PlatformTransactionManager gestor, ServicioLlaves llaves) {
            this.llaves = llaves;
            this.operador = operador;
            this.codificador = codificador;
            this.tx = new TransactionTemplate(gestor);
            this.tx.setReadOnly(true);
            this.hashDeRelleno = codificador.encode("relleno-para-igualar-tiempos");
        }

        @Override
        protected UserDetails retrieveUser(String nombre, UsernamePasswordAuthenticationToken autenticacion) {
            Optional<RepositorioOperador.Operador> encontrado = ContextoRls.con(Optional.empty(), Optional.empty(),
                    () -> tx.execute(e -> operador.porNombre(nombre)));
            if (encontrado == null || encontrado.isEmpty()) {
                Object clave = autenticacion.getCredentials();
                codificador.matches(clave == null ? "" : clave.toString(), hashDeRelleno);
                throw new UsernameNotFoundException("No existe ese operador");
            }
            UUID id = encontrado.get().id();
            boolean tieneLlave = Boolean.TRUE.equals(ContextoRls.con(Optional.empty(), Optional.empty(),
                    () -> tx.execute(e -> llaves.tieneLlave(Titular.operador(id)))));
            return new OperadorSesion(id, encontrado.get().claveHash(),
                    tieneLlave ? OperadorSesion.Etapa.CONFIRMAR_LLAVE : OperadorSesion.Etapa.REGISTRAR_LLAVE, false);
        }

        @Override
        protected void additionalAuthenticationChecks(UserDetails usuario, UsernamePasswordAuthenticationToken autenticacion) {
            Object clave = autenticacion.getCredentials();
            if (clave == null || !codificador.matches(clave.toString(), usuario.getPassword())) {
                throw new BadCredentialsException("Clave incorrecta");
            }
        }
    }
}
