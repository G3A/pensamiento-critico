package pensamiento.web.aula;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.transaction.PlatformTransactionManager;

import pensamiento.nucleo.puertos.RegistroAuditoria;
import pensamiento.nucleo.puertos.Reloj;
import pensamiento.nucleo.puertos.RepositorioAula;
import pensamiento.nucleo.puertos.RepositorioUsuarios;
import pensamiento.web.seguridad.CierreDeSesiones;
import pensamiento.web.usuarios.CodigoDeUnUso;

/** El aula del hito 10. */
@Configuration
public class ConfiguracionAula {

    @Bean
    ServicioAula servicioAula(RepositorioAula aula, RepositorioUsuarios usuarios, RegistroAuditoria auditoria, Reloj reloj,
                              PlatformTransactionManager gestor, SessionRegistry sesiones) {
        return new ServicioAula(aula, usuarios, auditoria, reloj, gestor, CodigoDeUnUso.aleatorio(), new CierreDeSesiones(sesiones));
    }
}
