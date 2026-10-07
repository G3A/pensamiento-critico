package pensamiento.nucleo;

import java.util.Optional;
import java.util.UUID;

/**
 * Entidad unificada: reemplaza a premisa, conclusión, hipótesis, predicción, postura, supuesto,
 * condición de falsación y opción, que pasan a ser roles dentro de una ejecución.
 */
public record Afirmacion(
        UUID id,
        UUID usuarioId,
        UUID institucionId,
        String texto,
        TipoAfirmacion tipo,
        OrigenAfirmacion origen,
        boolean adoptada,
        Optional<Integer> confianza,
        EstadoAfirmacion estado,
        int fuerzaNeta) {

    public boolean esVerificable() {
        return tipo != TipoAfirmacion.JUICIO_DE_VALOR && tipo != TipoAfirmacion.DEFINICION;
    }
}
