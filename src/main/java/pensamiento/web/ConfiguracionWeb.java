package pensamiento.web;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import pensamiento.nucleo.puertos.Reloj;

@Configuration
public class ConfiguracionWeb {

    /** Reloj real del sistema en la zona configurada (APP_ZONA). */
    @Bean
    Reloj reloj(@Value("${app.zona:America/Bogota}") String zona) {
        ZoneId zonaId = ZoneId.of(zona);
        Clock reloj = Clock.system(zonaId);
        return new Reloj() {
            @Override
            public Instant ahora() {
                return reloj.instant();
            }

            @Override
            public ZoneId zona() {
                return zonaId;
            }
        };
    }
}
