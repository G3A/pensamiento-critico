package pensamiento.catalogo;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import pensamiento.nucleo.Ejecutor;
import pensamiento.nucleo.puertos.RepositorioTecnica;

/** Registro de ejecutores por inyección y verificación del catálogo al arrancar. */
@Configuration
public class ConfiguracionCatalogo {

    private static final Logger LOG = LoggerFactory.getLogger(ConfiguracionCatalogo.class);

    @Bean
    RegistroEjecutores registroEjecutores(List<Ejecutor<?, ?, ?>> ejecutores) {
        return new RegistroEjecutores(ejecutores);
    }

    @Bean
    VerificadorCatalogo verificadorCatalogo(RepositorioTecnica tecnicas, RegistroEjecutores ejecutores) {
        return new VerificadorCatalogo(tecnicas, ejecutores);
    }

    /** Si el catálogo y los ejecutores no cuadran, la excepción tumba el contexto y la aplicación no arranca. */
    @Bean
    SmartInitializingSingleton verificacionAlArrancar(VerificadorCatalogo verificador) {
        return () -> {
            VerificadorCatalogo.Informe informe = verificador.exigirConsistencia();
            LOG.info("Catálogo verificado: {} técnicas, {} activas, {} pendientes", informe.tecnicas(), informe.activas(), informe.pendientes());
        };
    }
}
