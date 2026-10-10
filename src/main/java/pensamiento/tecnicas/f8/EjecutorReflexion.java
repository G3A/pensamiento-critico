package pensamiento.tecnicas.f8;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import pensamiento.nucleo.Contexto;
import pensamiento.nucleo.Ejecutor;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Json;
import pensamiento.nucleo.Resultado;
import pensamiento.nucleo.Validacion;
import pensamiento.tecnicas.comun.Textos;

/**
 * T47 · Reflexión estructurada (Gibbs 1988). Ordena el cierre de una sesión o una decisión con las preguntas activas: lo
 * respondido y lo pendiente. No califica lo que escribe la persona ni usa IA. El Consejero usa sus preguntas al cerrar y,
 * si la configuración la hace obligatoria, no cierra sin al menos una respuesta. Las reglas están en docs/ejemplos/T47.md.
 */
@Component
public class EjecutorReflexion implements Ejecutor<EjecutorReflexion.Config, EjecutorReflexion.Entrada, ResultadoReflexion> {

    public static final IdTecnica ID = IdTecnica.de("T47");
    public static final int VERSION_ESQUEMA = 1;

    /** Las seis preguntas, en el orden del ciclo de Gibbs. */
    public enum Pregunta {
        APRENDI("¿Qué aprendí?"),
        SIN_CLARO("¿Qué sigue sin estar claro?"),
        DISTINTO("¿Qué haría distinto?"),
        CAMBIO("¿Qué cambió en lo que pienso?"),
        SENTIMIENTOS("¿Qué sentí y cómo pesó en lo que pensé?"),
        SIGUIENTE("¿Cuál es mi siguiente paso?");

        private final String texto;

        Pregunta(String texto) {
            this.texto = texto;
        }

        public String texto() {
            return texto;
        }

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    /** Configuración de T47, versión de esquema 1. */
    public record Config(List<Pregunta> preguntas, boolean obligatoriaAlCerrar) {
        public Config {
            preguntas = preguntas == null ? List.of() : List.copyOf(preguntas);
        }
    }

    /** Sobre qué reflexionas y una respuesta por pregunta; las de preguntas apagadas no cuentan. */
    public record Entrada(String sobre, String aprendi, String sinClaro, String distinto, String cambio, String sentimientos, String siguiente) {

        public String respuesta(Pregunta p) {
            String r = switch (p) {
                case APRENDI -> aprendi;
                case SIN_CLARO -> sinClaro;
                case DISTINTO -> distinto;
                case CAMBIO -> cambio;
                case SENTIMIENTOS -> sentimientos;
                case SIGUIENTE -> siguiente;
            };
            return Textos.vacio(r) ? null : r.strip();
        }
    }

    @Override
    public IdTecnica id() {
        return ID;
    }

    @Override
    public int versionEsquema() {
        return VERSION_ESQUEMA;
    }

    @Override
    public Tipos<Config, Entrada, ResultadoReflexion> tipos() {
        return new Tipos<>(Config.class, Entrada.class, ResultadoReflexion.class);
    }

    @Override
    public Validacion validar(Config config, Entrada entrada) {
        List<Validacion.Error> errores = new ArrayList<>();
        if (config.preguntas().isEmpty()) {
            errores.add(new Validacion.Error("config.preguntas", "Elige al menos una pregunta."));
        }
        if (Textos.vacio(entrada.sobre())) {
            errores.add(new Validacion.Error("sobre", "Escribe sobre qué reflexionas: una sesión, una decisión, un mapa."));
        }
        if (!config.preguntas().isEmpty() && respondidas(config, entrada) == 0) {
            errores.add(new Validacion.Error("_reflexion", "Responde al menos una pregunta: una reflexión vacía no se guarda."));
        }
        return new Validacion(errores);
    }

    /** Cuántas preguntas activas tienen respuesta. */
    public static int respondidas(Config config, Entrada entrada) {
        return (int) config.preguntas().stream().filter(p -> entrada.respuesta(p) != null).count();
    }

    @Override
    public Resultado<ResultadoReflexion> ejecutar(Config config, Entrada entrada, Contexto ctx) {
        Validacion validacion = validar(config, entrada);
        if (!validacion.esValida()) {
            throw new IllegalArgumentException("Entrada inválida para " + ID + ": " + validacion.errores());
        }
        List<ResultadoReflexion.Item> items = new ArrayList<>();
        for (Pregunta p : Pregunta.values()) {
            if (config.preguntas().contains(p)) {
                items.add(new ResultadoReflexion.Item(p, p.texto(), entrada.respuesta(p)));
            }
        }
        int respondidas = respondidas(config, entrada);
        String nota = (config.obligatoriaAlCerrar() ? "Obligatoria" : "Opcional")
                + " al cerrar una sesión del Consejero · se guarda en el diario (T45 · Diario de razonamiento).";
        String sobre = sinPuntoFinal(entrada.sobre());
        String resumen = respondidas + " de " + items.size() + " preguntas respondidas · " + sobre + ".";
        return new Resultado<>(VERSION_ESQUEMA, new ResultadoReflexion(sobre, items, respondidas, nota, resumen), List.of(), List.of(), resumen);
    }

    private static String sinPuntoFinal(String texto) {
        String t = texto.strip();
        while (t.endsWith(".")) {
            t = t.substring(0, t.length() - 1).strip();
        }
        return t;
    }

    @Override
    public ResultadoReflexion migrar(Json datosViejos, int desdeVersion) {
        throw new IllegalArgumentException(ID + " no tiene versiones anteriores a la " + VERSION_ESQUEMA
                + "; se pidió migrar desde la " + desdeVersion);
    }
}
