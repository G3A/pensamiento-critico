package pensamiento.nucleo.puertos;

import java.time.Duration;
import java.util.List;

public record PeticionEmbeddings(List<String> textos, Duration tiempoMaximo) {

    public PeticionEmbeddings {
        textos = List.copyOf(textos);
        if (textos.isEmpty()) {
            throw new IllegalArgumentException("Se necesita al menos un texto para incrustar");
        }
    }
}
