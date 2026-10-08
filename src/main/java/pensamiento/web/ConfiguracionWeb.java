package pensamiento.web;

import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import pensamiento.nucleo.puertos.Reloj;

@Configuration
public class ConfiguracionWeb {

    /** Reloj real del sistema en la zona configurada (APP_ZONA); ajustable solo si APP_RELOJ_AJUSTABLE es verdadero. */
    @Bean
    public RelojDelSistema relojDelSistema(@Value("${app.zona:America/Bogota}") String zona, @Value("${app.reloj.ajustable:false}") boolean ajustable) {
        return new RelojDelSistema(ZoneId.of(zona), ajustable);
    }

    /** El mismo reloj sin ajuste, para el contrato real del puerto. */
    public Reloj reloj(String zona) {
        return new RelojDelSistema(ZoneId.of(zona), false);
    }
}
