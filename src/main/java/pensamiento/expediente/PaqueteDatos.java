package pensamiento.expediente;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import tools.jackson.databind.JsonNode;

/**
 * El archivo JSON con los datos de una persona (RF-12, P21): configuraciones, expedientes y ejecuciones con sus
 * afirmaciones, pendientes y argumentos. Todos los identificadores son uuidv7, así que importar en otra
 * instalación no choca. La versión 2 (hito 2) agrega los argumentos; un archivo de la versión 1 se migra al
 * leerlo: sus ejecuciones quedan sin argumentos, que es lo que tenían.
 */
public record PaqueteDatos(String formato, int version, Instant exportadoEn, String persona,
                           List<Configuracion> configuraciones, List<ExpedienteDatos> expedientes, List<EjecucionDatos> ejecuciones) {

    public static final String FORMATO = "taller-de-pensamiento-critico/datos-de-una-persona";
    public static final int VERSION = 2;
    public static final int VERSION_ANTERIOR = 1;

    public record Configuracion(String tecnica, int versionEsquema, JsonNode valores) {
    }

    public record ExpedienteDatos(UUID id, String nombre, String estado, Instant creadoEn) {
    }

    public record AfirmacionDatos(UUID id, String texto, String tipo, String rol, String sentido, String origen) {
    }

    public record PendienteDatos(String tipo, UUID objetoId, LocalDate vence, String descripcion) {
    }

    public record PremisaDatos(UUID afirmacionId, int orden, boolean asumible) {
    }

    /** Un argumento de la ejecución, con sus premisas (versión 2). */
    public record ArgumentoDatos(UUID id, UUID conclusionId, String esquemaId, int peso, String sentido, String estandar,
                                 String textoArgdown, List<PremisaDatos> premisas) {
        public ArgumentoDatos {
            premisas = premisas == null ? List.of() : List.copyOf(premisas);
        }
    }

    public record EjecucionDatos(UUID id, String tecnica, int versionEsquema, UUID expedienteId, JsonNode config, JsonNode datos,
                                 JsonNode resultado, String resumen, String claveIdempotencia, Instant creadaEn,
                                 List<AfirmacionDatos> afirmaciones, List<PendienteDatos> pendientes, List<ArgumentoDatos> argumentos) {
        public EjecucionDatos {
            afirmaciones = afirmaciones == null ? List.of() : List.copyOf(afirmaciones);
            pendientes = pendientes == null ? List.of() : List.copyOf(pendientes);
            argumentos = argumentos == null ? List.of() : List.copyOf(argumentos);
        }
    }

    public PaqueteDatos {
        configuraciones = configuraciones == null ? List.of() : List.copyOf(configuraciones);
        expedientes = expedientes == null ? List.of() : List.copyOf(expedientes);
        ejecuciones = ejecuciones == null ? List.of() : List.copyOf(ejecuciones);
    }

    /** Un archivo de la versión 1 pasa a la vigente: el mismo contenido, sin argumentos. Otra versión no se toca. */
    public PaqueteDatos migrado() {
        if (version != VERSION_ANTERIOR) {
            return this;
        }
        return new PaqueteDatos(formato, VERSION, exportadoEn, persona, configuraciones, expedientes, ejecuciones);
    }
}
