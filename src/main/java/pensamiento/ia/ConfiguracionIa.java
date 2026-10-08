package pensamiento.ia;

import java.time.Clock;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import pensamiento.nucleo.puertos.EstadoIa;
import pensamiento.nucleo.puertos.Ia;

/**
 * El único Ia que ve el resto de la aplicación es el cortacircuitos; el adaptador de Ollama queda detrás, y el
 * monitor consulta su estado cada 30 segundos.
 */
@Configuration
public class ConfiguracionIa {

    /** Cuánto espera el cortacircuitos antes de volver a intentar tras una llamada sin respuesta. */
    static final Duration ENFRIAMIENTO = Duration.ofSeconds(30);

    @Bean
    MonitorIa monitorIa(@Value("${app.ia.base-url}") String baseUrl,
                        @Value("${app.ia.modelo-chat}") String modeloChat,
                        @Value("${app.ia.modelo-embeddings}") String modeloEmbeddings) {
        return new MonitorIa(new IaSpringAi(baseUrl, modeloChat, modeloEmbeddings, new SemaforoIa(1)));
    }

    @Bean
    Ia ia(MonitorIa monitor) {
        return new CortacircuitosIa(monitor.ollama(), monitor::estado, Clock.systemUTC(), ENFRIAMIENTO);
    }

    /** La app sigue UP sin Ollama (modo plantillas): el componente lo dice como detalle, no tumba el health. */
    @Bean
    HealthIndicator iaHealthIndicator(Ia ia) {
        return () -> {
            EstadoIa estado = ia.estado();
            return (estado.disponible() ? Health.up() : Health.status("PLANTILLAS"))
                    .withDetail("modelos", estado.modelos())
                    .withDetail("detalle", estado.detalle())
                    .build();
        };
    }
}
