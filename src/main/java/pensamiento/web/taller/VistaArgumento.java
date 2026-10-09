package pensamiento.web.taller;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import pensamiento.flujos.FichaDeVerificacion;
import pensamiento.nucleo.Argumento;
import pensamiento.nucleo.EstadoAfirmacion;
import pensamiento.nucleo.puertos.RepositorioArgumentos;

/**
 * Un argumento guardado con los textos de su conclusión y sus premisas, el estado de cada afirmación (de la ficha de
 * verificación) y R04 recalculado con lo verificado.
 */
public record VistaArgumento(RepositorioArgumentos.ArgumentoGuardado guardado, Map<UUID, String> textos, Map<UUID, EstadoAfirmacion> estados,
                             Optional<FichaDeVerificacion.R04Recalculado> r04) {

    public EstadoAfirmacion estado(UUID afirmacion) {
        return estados.getOrDefault(afirmacion, EstadoAfirmacion.SIN_VERIFICAR);
    }

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
