package pensamiento.web.usuarios;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;

import pensamiento.nucleo.puertos.RegistroAuditoria;
import pensamiento.nucleo.puertos.Reloj;
import pensamiento.nucleo.puertos.RepositorioUsuarios;
import pensamiento.web.seguridad.CierreDeSesiones;
import pensamiento.web.seguridad.LimiteDeIntentos;

@Configuration
public class ConfiguracionUsuarios {

    @Bean
    PoliticaDeContrasenas politicaDeContrasenas() {
        return PoliticaDeContrasenas.conListaVendorizada();
    }

    @Bean
    ServicioUsuarios servicioUsuarios(RepositorioUsuarios usuarios, RegistroAuditoria auditoria, PasswordEncoder codificador, Reloj reloj,
                                      PlatformTransactionManager gestor, PoliticaDeContrasenas politica, SessionRegistry sesiones,
                                      LimiteDeIntentos limite) {
        return new ServicioUsuarios(usuarios, auditoria, codificador, reloj, gestor, politica, CodigoDeUnUso.aleatorio(),
                new CierreDeSesiones(sesiones), limite);
    }
}
