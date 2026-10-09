package pensamiento.expediente;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import tools.jackson.databind.JsonNode;

/**
 * El archivo JSON con los datos de una persona (RF-12, P21): configuraciones, expedientes y ejecuciones con sus
 * afirmaciones, pendientes, argumentos, predicciones y cambios de opinión, y las sesiones del Consejero con sus turnos. Todos
 * los identificadores son uuidv7, así que importar en otra instalación no choca. La versión 2 (hito 2) agrega los argumentos,
 * la 3 (hito 4) las predicciones del Diario, la 4 (hito 5) los cambios de opinión y las sesiones del Consejero y la 5 (hito 6)
 * la biblioteca (metadatos y texto de cada fragmento, sin el archivo original ni los vectores), las fuentes con sus
 * evidencias, las fichas de verificación y el veredicto de cada afirmación; un archivo de una versión anterior se migra al
 * leerlo: queda sin lo que todavía no existía.
 */
public record PaqueteDatos(String formato, int version, Instant exportadoEn, String persona,
                           List<Configuracion> configuraciones, List<ExpedienteDatos> expedientes, List<EjecucionDatos> ejecuciones,
                           List<SesionDatos> sesiones, List<DocumentoDatos> documentos, List<EvidenciaDatos> evidencias,
                           List<VerificacionDatos> verificaciones, List<VeredictoDatos> veredictos) {

    public static final String FORMATO = "taller-de-pensamiento-critico/datos-de-una-persona";
    public static final int VERSION = 5;
    /** Las versiones que se pueden importar, de la más vieja a la vigente. */
    public static final List<Integer> VERSIONES_LEIBLES = List.of(1, 2, 3, 4, 5);

    public record Configuracion(String tecnica, int versionEsquema, JsonNode valores) {
    }

    public record ExpedienteDatos(UUID id, String nombre, String estado, Instant creadoEn) {
    }

    /** adoptada puede faltar en archivos anteriores al hito 3: entonces se deduce del origen. */
    public record AfirmacionDatos(UUID id, String texto, String tipo, String rol, String sentido, String origen, Boolean adoptada) {
    }

    /** Modelo, digest, prompt, temperatura y semilla de una ejecución con IA (RNF-07); falta en las demás. */
    public record RegistroModeloDatos(String modelo, String digest, String promptVersion, double temperatura, long semilla) {
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

    /**
     * Una predicción del Diario (versión 3): la afirmación de la ejecución que predice, la confianza declarada, la fecha de
     * revisión y, si ya se revisó, el resultado ("acierto" o "fallo") y cuándo; resuelta sigue inmutable al importarse.
     */
    public record PrediccionDatos(UUID id, UUID afirmacionId, int confianza, LocalDate fechaRevision, String resultado, Instant resueltaEn) {
    }

    /** Una razón de la postura en una sesión de debate. */
    public record RazonDatos(String texto, String apoyo) {
    }

    /** Un turno de una sesión del Consejero (versión 4). */
    public record TurnoDatos(UUID id, int numero, String rol, String paso, String texto, String origen, String estado, int intentos,
                             RegistroModeloDatos modelo, String elementoPropuesto, String porquePropuesto, boolean propuestaAdoptada, Instant creadoEn) {
    }

    /** Una sesión del Consejero socrático con sus turnos (versión 4). */
    public record SesionDatos(UUID id, UUID expedienteId, String modo, String postura, List<RazonDatos> razones, JsonNode config, boolean usaModelo,
                              Integer confianzaAntes, boolean cierrePedido, String estado, String reflexion, Integer confianzaDespues, UUID ejecucionId,
                              Instant creadaEn, Instant cerradaEn, List<TurnoDatos> turnos) {
        public SesionDatos {
            razones = razones == null ? List.of() : List.copyOf(razones);
            turnos = turnos == null ? List.of() : List.copyOf(turnos);
        }
    }

    /** Un cambio de opinión de la ejecución (versión 4): sobre una afirmación suya, con la causa y la fecha. */
    public record CambioDatos(UUID id, UUID afirmacionId, int confianzaAntes, int confianzaDespues, String causa, Instant creadoEn) {
    }

    public record EjecucionDatos(UUID id, String tecnica, int versionEsquema, UUID expedienteId, JsonNode config, JsonNode datos,
                                 JsonNode resultado, String resumen, String claveIdempotencia, Instant creadaEn, RegistroModeloDatos modelo,
                                 List<AfirmacionDatos> afirmaciones, List<PendienteDatos> pendientes, List<ArgumentoDatos> argumentos,
                                 List<PrediccionDatos> predicciones, List<CambioDatos> cambios) {
        public EjecucionDatos {
            afirmaciones = afirmaciones == null ? List.of() : List.copyOf(afirmaciones);
            pendientes = pendientes == null ? List.of() : List.copyOf(pendientes);
            argumentos = argumentos == null ? List.of() : List.copyOf(argumentos);
            predicciones = predicciones == null ? List.of() : List.copyOf(predicciones);
            cambios = cambios == null ? List.of() : List.copyOf(cambios);
        }
    }

    /** Un fragmento de un documento de la biblioteca (versión 5): su texto literal y su página. */
    public record FragmentoDatos(UUID id, int orden, String texto, Integer pagina) {
    }

    /**
     * Un documento indexado de la biblioteca (versión 5): metadatos y fragmentos, sin el archivo original ni los vectores. Al
     * importarlo queda indexado, privado y sin original, y se vuelve a vectorizar.
     */
    public record DocumentoDatos(UUID id, String nombre, String tipo, String hash, long tamano, Integer paginas, Instant creadoEn,
                                 List<FragmentoDatos> fragmentos) {
        public DocumentoDatos {
            fragmentos = fragmentos == null ? List.of() : List.copyOf(fragmentos);
        }
    }

    /** La ficha de una fuente (versión 5), con los cinco criterios de CRAAP si se puntuaron y las notas de SIFT. */
    public record FuenteDatos(UUID id, String titulo, String autor, LocalDate fecha, String tipo, String diseno, String grupo, boolean independiente,
                              boolean original, Integer puntajeCraap, List<Integer> craap, String siftInvestigue, String siftCobertura,
                              String siftContexto, UUID documentoId, String documentoNombre, Integer pagina) {
    }

    /** Una evidencia sobre una afirmación de una ejecución del archivo (versión 5), con su fuente. */
    public record EvidenciaDatos(UUID id, UUID afirmacionId, UUID fragmentoId, String pasaje, String postura, int fuerza, String etiquetadaPor,
                                 boolean adoptada, FuenteDatos fuente) {
    }

    /** Las preguntas críticas marcadas en la ficha de una afirmación (versión 5). */
    public record VerificacionDatos(UUID afirmacionId, List<String> preguntas, Instant actualizadaEn) {
        public VerificacionDatos {
            preguntas = preguntas == null ? List.of() : List.copyOf(preguntas);
        }
    }

    /** El veredicto guardado en una afirmación (versión 5): tipo, estado, fuerza neta y confianza. */
    public record VeredictoDatos(UUID afirmacionId, String tipo, String estado, int fuerzaNeta, Integer confianza) {
    }

    public PaqueteDatos {
        configuraciones = configuraciones == null ? List.of() : List.copyOf(configuraciones);
        expedientes = expedientes == null ? List.of() : List.copyOf(expedientes);
        ejecuciones = ejecuciones == null ? List.of() : List.copyOf(ejecuciones);
        sesiones = sesiones == null ? List.of() : List.copyOf(sesiones);
        documentos = documentos == null ? List.of() : List.copyOf(documentos);
        evidencias = evidencias == null ? List.of() : List.copyOf(evidencias);
        verificaciones = verificaciones == null ? List.of() : List.copyOf(verificaciones);
        veredictos = veredictos == null ? List.of() : List.copyOf(veredictos);
    }

    /**
     * Un archivo de una versión anterior pasa a la vigente: el mismo contenido, sin argumentos (versión 1) ni predicciones
     * (versiones 1 y 2), que las listas vacías ya representan. Otra versión no se toca.
     */
    public PaqueteDatos migrado() {
        if (version == VERSION || !VERSIONES_LEIBLES.contains(version)) {
            return this;
        }
        return new PaqueteDatos(formato, VERSION, exportadoEn, persona, configuraciones, expedientes, ejecuciones, sesiones, documentos, evidencias,
                verificaciones, veredictos);
    }
}
