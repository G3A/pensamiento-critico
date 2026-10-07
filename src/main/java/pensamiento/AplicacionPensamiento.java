package pensamiento;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Taller de Pensamiento Crítico: 49 técnicas en 8 familias, offline, con docker-compose. */
@SpringBootApplication
@EnableScheduling
public class AplicacionPensamiento {

    public static void main(String[] args) {
        SpringApplication.run(AplicacionPensamiento.class, args);
    }
}
