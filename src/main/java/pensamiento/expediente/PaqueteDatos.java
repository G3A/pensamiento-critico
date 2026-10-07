package pensamiento.expediente;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import tools.jackson.databind.JsonNode;

/**
 * El archivo JSON con los datos de una persona (RF-12, P21): configuraciones, expedientes y ejecuciones con sus
 * afirmaciones y pendientes. Todos los identificadores son uuidv7, así que importar en otra instalación no choca.
 */
public record PaqueteDatos(String formato, int version, Instant exportadoEn, String persona,
                           List<Configuracion> configuraciones, List<ExpedienteDatos> expedientes, List<EjecucionDatos> ejecuciones) {

    public static final String FORMATO = "taller-de-pensamiento-critico/datos-de-una-persona";
    public static final int VERSION = 1;

    public record Configuracion(String tecnica, int versionEsquema, JsonNode valores) {
    }

    public record ExpedienteDatos(UUID id, String nombre, String estado, Instant creadoEn) {
    }

    public record AfirmacionDatos(UUID id, String texto, String tipo, String rol, String sentido, String origen) {
    }

    public record PendienteDatos(String tipo, UUID objetoId, LocalDate vence, String descripcion) {
    }

    public record EjecucionDatos(UUID id, String tecnica, int versionEsquema, UUID expedienteId, JsonNode config, JsonNode datos,
                                 JsonNode resultado, String resumen, String claveIdempotencia, Instant creadaEn,
                                 List<AfirmacionDatos> afirmaciones, List<PendienteDatos> pendientes) {
    }

    public PaqueteDatos {
        configuraciones = configuraciones == null ? List.of() : List.copyOf(configuraciones);
        expedientes = expedientes == null ? List.of() : List.copyOf(expedientes);
        ejecuciones = ejecuciones == null ? List.of() : List.copyOf(ejecuciones);
    }
}
