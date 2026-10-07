package pensamiento.graficos;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import pensamiento.nucleo.puertos.Grafico;

@Configuration
public class ConfiguracionGraficos {

    @Bean
    Grafico grafico(@Value("${app.graphviz.timeout:5s}") Duration tiempoMaximo,
                    @Value("${app.graphviz.nslimit:50}") int nslimit) {
        return new GraphvizProceso("dot", tiempoMaximo, nslimit, 4);
    }
}
