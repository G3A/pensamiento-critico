package pensamiento.web.aula;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import pensamiento.nucleo.puertos.Ia;
import pensamiento.nucleo.puertos.RegistroAuditoria;
import pensamiento.nucleo.puertos.Reloj;
import pensamiento.nucleo.puertos.RepositorioAula;
import pensamiento.nucleo.puertos.RepositorioUsuarios;
import pensamiento.web.seguridad.CierreDeSesiones;
import pensamiento.web.usuarios.CodigoDeUnUso;

/** El aula del hito 10: su servicio y la sesión de un estudiante que dura lo que su clase. */
@Configuration
public class ConfiguracionAula {

    @Bean
    ServicioAula servicioAula(RepositorioAula aula, RepositorioUsuarios usuarios, RegistroAuditoria auditoria, Reloj reloj,
                              PlatformTransactionManager gestor, SessionRegistry sesiones) {
        return new ServicioAula(aula, usuarios, auditoria, reloj, gestor, CodigoDeUnUso.aleatorio(), new CierreDeSesiones(sesiones));
    }

    /**
     * El Ia que ve toda la app (entregable 9): el cortacircuitos de siempre detrás de la regla del aula, leída de la sesión del
     * estudiante (SesionDeClase la deja ahí). Sin petición (trabajos de fondo) o sin regla, el modelo como siempre.
     */
    @Bean
    @Primary
    IaDelAula iaDelAula(@Qualifier("ia") Ia ia) {
        return new IaDelAula(ia, () -> {
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes atributos
                    && atributos.getRequest().getSession(false) != null
                    && atributos.getRequest().getSession(false).getAttribute(SesionDeClase.IA) instanceof IaDelAula.EnClase regla) {
                return regla;
            }
            return new IaDelAula.EnClase.Libre();
        });
    }

    @Bean
    WebMvcConfigurer sesionDeClase(ServicioAula servicio, Reloj reloj) {
        SesionDeClase interceptor = new SesionDeClase(servicio, reloj);
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registro) {
                registro.addInterceptor(interceptor);
            }
        };
    }
}
