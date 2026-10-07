package pensamiento.web.usuarios;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import pensamiento.nucleo.puertos.RegistroAuditoria;
import pensamiento.nucleo.puertos.Reloj;
import pensamiento.nucleo.puertos.RepositorioUsuarios;

@Configuration
public class ConfiguracionUsuarios {

    @Bean
    ServicioUsuarios servicioUsuarios(RepositorioUsuarios usuarios, RegistroAuditoria auditoria, PasswordEncoder codificador, Reloj reloj) {
        return new ServicioUsuarios(usuarios, auditoria, codificador, reloj);
    }
}
