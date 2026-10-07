package pensamiento.ia;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import pensamiento.nucleo.puertos.EstadoIa;
import pensamiento.nucleo.puertos.Ia;

@Configuration
public class ConfiguracionIa {

    @Bean
    Ia ia(@Value("${app.ia.base-url}") String baseUrl,
          @Value("${app.ia.modelo-chat}") String modeloChat,
          @Value("${app.ia.modelo-embeddings}") String modeloEmbeddings) {
        return new IaSpringAi(baseUrl, modeloChat, modeloEmbeddings, new SemaforoIa(1));
    }

    /** La app sigue UP sin Ollama (modo plantillas): el componente lo dice como detalle, no tumba el health. */
    @Bean
    HealthIndicator iaHealthIndicator(MonitorIa monitor) {
        return () -> {
            EstadoIa estado = monitor.estado();
            return (estado.disponible() ? Health.up() : Health.status("PLANTILLAS"))
                    .withDetail("modelos", estado.modelos())
                    .withDetail("detalle", estado.detalle())
                    .build();
        };
    }
}
