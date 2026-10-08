package pensamiento.web.taller;

import java.util.Map;
import java.util.UUID;

import pensamiento.nucleo.Argumento;
import pensamiento.nucleo.puertos.RepositorioArgumentos;

/** Un argumento guardado con los textos de su conclusión y sus premisas. */
public record VistaArgumento(RepositorioArgumentos.ArgumentoGuardado guardado, Map<UUID, String> textos) {

    public Argumento argumento() {
        return guardado.argumento().argumento();
    }

    public String texto(UUID afirmacion) {
        return textos.getOrDefault(afirmacion, "(afirmación sin texto)");
    }

    public String sentido() {
        return argumento().sentido() == Argumento.Sentido.PRO ? "a favor" : "en contra";
    }
}
