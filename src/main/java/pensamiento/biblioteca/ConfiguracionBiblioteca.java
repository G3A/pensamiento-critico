package pensamiento.biblioteca;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import pensamiento.nucleo.puertos.ExtractorPdf;

@Configuration
public class ConfiguracionBiblioteca {

    @Bean
    ExtractorPdf extractorPdf(@Value("${app.pdf.timeout:60s}") Duration tiempoMaximo, @Value("${app.pdf.paginas-maximas:300}") int paginasMaximas) {
        return new ExtractorPdfProceso(tiempoMaximo, paginasMaximas);
    }
}
