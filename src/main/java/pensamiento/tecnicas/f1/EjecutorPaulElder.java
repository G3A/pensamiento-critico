package pensamiento.tecnicas.f1;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import org.springframework.stereotype.Component;

import pensamiento.nucleo.AfirmacionConRol;
import pensamiento.nucleo.ConModelo;
import pensamiento.nucleo.Contexto;
import pensamiento.nucleo.Ejecutor;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Json;
import pensamiento.nucleo.OrigenAfirmacion;
import pensamiento.nucleo.Pendiente;
import pensamiento.nucleo.Propuesta;
import pensamiento.nucleo.Resultado;
import pensamiento.nucleo.RolAfirmacion;
import pensamiento.nucleo.SentidoAfirmacion;
import pensamiento.nucleo.TipoAfirmacion;
import pensamiento.nucleo.TipoPendiente;
import pensamiento.nucleo.Validacion;
import pensamiento.nucleo.puertos.Clasificacion;
import pensamiento.tecnicas.comun.ModeloLocal;
import pensamiento.tecnicas.comun.Prompts;
import pensamiento.tecnicas.comun.Textos;

/**
 * T04 · Elementos y estándares de Paul-Elder (Paul y Elder 2001). Cuenta los elementos llenos contra un umbral y
 * muestra los estándares puntuados, con la pregunta de los bajos; nunca da una nota global. El modelo puede asignar
 * oraciones de un texto libre a los elementos vacíos; no cuenta hasta adoptarse. Reglas en docs/ejemplos/T04.md.
 */
@Component
public class EjecutorPaulElder implements Ejecutor<EjecutorPaulElder.Config, EjecutorPaulElder.Entrada, ResultadoPaulElder>,
        ConModelo<EjecutorPaulElder.Config, EjecutorPaulElder.Entrada> {

    public static final IdTecnica ID = IdTecnica.de("T04");
    public static final int VERSION_ESQUEMA = 1;
    public static final String PROMPT = "t04-elemento";
    public static final int VERSION_PROMPT = 1;
    public static final String NINGUNO = "ninguno";
    public static final int ORACIONES_MAXIMAS = 12;

    /** Los ocho elementos, en orden, con la pregunta que los llena. */
    public enum Elemento {
        PROPOSITO("Propósito", "¿Qué quieres lograr con este razonamiento?"),
        PREGUNTA("Pregunta", "¿Qué pregunta intentas responder?"),
        INFORMACION("Información", "¿Qué datos, hechos u observaciones usas?"),
        SUPUESTOS("Supuestos", "¿Qué das por sentado?"),
        CONCEPTOS("Conceptos", "¿Qué ideas o palabras clave hay que definir?"),
        INFERENCIAS("Inferencias", "¿Qué concluyes a partir de la información?"),
        IMPLICACIONES("Implicaciones", "¿Qué pasaría si sigues este razonamiento?"),
        PUNTOS_DE_VISTA("Puntos de vista", "¿Desde qué punto de vista miras esto y cuál otro hay?");

        private final String nombre;
        private final String pregunta;

        Elemento(String nombre, String pregunta) {
            this.nombre = nombre;
            this.pregunta = pregunta;
        }

        public String nombre() {
            return nombre;
        }

        public String pregunta() {
            return pregunta;
        }

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    /** Los nueve estándares, en orden, con la pregunta que se muestra si el puntaje es bajo. */
    public enum Estandar {
        CLARIDAD("Claridad", "¿Puedes decirlo de otra manera o dar un ejemplo?"),
        EXACTITUD("Exactitud", "¿Cómo podrías comprobar que es cierto?"),
        PRECISION("Precisión", "¿Puedes dar más detalles o cifras?"),
        RELEVANCIA("Relevancia", "¿Qué relación tiene con la pregunta?"),
        PROFUNDIDAD("Profundidad", "¿Qué hace difícil este asunto?"),
        AMPLITUD("Amplitud", "¿Cómo se ve desde otro punto de vista?"),
        LOGICA("Lógica", "¿Las partes encajan entre sí?"),
        SIGNIFICANCIA("Significancia", "¿Es esto lo más importante?"),
        EQUIDAD("Equidad", "¿Tienes en cuenta a todos los afectados?");

        private final String nombre;
        private final String pregunta;

        Estandar(String nombre, String pregunta) {
            this.nombre = nombre;
            this.pregunta = pregunta;
        }

        public String nombre() {
            return nombre;
        }

        public String pregunta() {
            return pregunta;
        }

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    public enum Modo {
        PLANTILLAS, PLANTILLAS_Y_MODELO;

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    /** Configuración de T04, versión de esquema 1. */
    public record Config(List<Elemento> elementos, List<Estandar> estandares, int umbral, Modo modo) {
        public Config {
            elementos = elementos == null ? List.of() : List.copyOf(elementos);
            estandares = estandares == null ? List.of() : List.copyOf(estandares);
        }
    }

    /**
     * @param delModelo  los elementos que vinieron de una propuesta adoptada, separados por coma ("implicaciones")
     * @param textoLibre una conversación o una nota de donde el modelo puede proponer elementos
     */
    public record Entrada(String tema, String proposito, String pregunta, String informacion, String supuestos, String conceptos, String inferencias,
                          String implicaciones, String puntosDeVista, String delModelo, Integer claridad, Integer exactitud, Integer precision,
                          Integer relevancia, Integer profundidad, Integer amplitud, Integer logica, Integer significancia, Integer equidad,
                          String textoLibre, List<Propuesta> propuestas) {
        public Entrada {
            propuestas = propuestas == null ? List.of() : List.copyOf(propuestas);
        }

        String texto(Elemento e) {
            return switch (e) {
                case PROPOSITO -> proposito;
                case PREGUNTA -> pregunta;
                case INFORMACION -> informacion;
                case SUPUESTOS -> supuestos;
                case CONCEPTOS -> conceptos;
                case INFERENCIAS -> inferencias;
                case IMPLICACIONES -> implicaciones;
                case PUNTOS_DE_VISTA -> puntosDeVista;
            };
        }

        Integer puntaje(Estandar s) {
            return switch (s) {
                case CLARIDAD -> claridad;
                case EXACTITUD -> exactitud;
                case PRECISION -> precision;
                case RELEVANCIA -> relevancia;
                case PROFUNDIDAD -> profundidad;
                case AMPLITUD -> amplitud;
                case LOGICA -> logica;
                case SIGNIFICANCIA -> significancia;
                case EQUIDAD -> equidad;
            };
        }

        Set<String> adoptados() {
            Set<String> s = new HashSet<>();
            if (!Textos.vacio(delModelo)) {
                for (String x : delModelo.split(",")) {
                    s.add(x.strip());
                }
            }
            return s;
        }

        Entrada con(Elemento e, String texto, List<Propuesta> nuevas) {
            Set<String> adoptados = new java.util.TreeSet<>(adoptados());
            adoptados.add(e.toString());
            String origen = String.join(",", adoptados);
            return new Entrada(tema, e == Elemento.PROPOSITO ? texto : proposito, e == Elemento.PREGUNTA ? texto : pregunta,
                    e == Elemento.INFORMACION ? texto : informacion, e == Elemento.SUPUESTOS ? texto : supuestos,
                    e == Elemento.CONCEPTOS ? texto : conceptos, e == Elemento.INFERENCIAS ? texto : inferencias,
                    e == Elemento.IMPLICACIONES ? texto : implicaciones, e == Elemento.PUNTOS_DE_VISTA ? texto : puntosDeVista, origen, claridad,
                    exactitud, precision, relevancia, profundidad, amplitud, logica, significancia, equidad, textoLibre, nuevas);
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
    public Tipos<Config, Entrada, ResultadoPaulElder> tipos() {
        return new Tipos<>(Config.class, Entrada.class, ResultadoPaulElder.class);
    }

    @Override
    public Validacion validar(Config config, Entrada entrada) {
        List<Validacion.Error> errores = new ArrayList<>();
        if (config.elementos().isEmpty()) {
            errores.add(new Validacion.Error("config.elementos", "Activa al menos un elemento."));
        }
        if (config.umbral() < 1 || config.umbral() > Math.max(1, config.elementos().size())) {
            errores.add(new Validacion.Error("config.umbral", "El umbral va de 1 al número de elementos activos."));
        }
        if (config.modo() == null) {
            errores.add(new Validacion.Error("config.modo", "Elige el modo."));
        }
        if (Textos.vacio(entrada.tema())) {
            errores.add(new Validacion.Error("tema", "Escribe el tema del razonamiento."));
        }
        for (Estandar s : Estandar.values()) {
            Integer p = entrada.puntaje(s);
            if (p != null && (p < 1 || p > 10)) {
                errores.add(new Validacion.Error(s.toString(), "El puntaje va de 1 a 10, o vacío si no lo puntúas."));
            }
        }
        for (Propuesta p : entrada.propuestas()) {
            if (!Propuesta.codigoValido(p.codigo()) || elemento(p.destino()).isEmpty()) {
                errores.add(new Validacion.Error("propuestas", "Hay una propuesta del modelo que no corresponde a esta técnica."));
                break;
            }
        }
        return new Validacion(errores);
    }

    @Override
    public Resultado<ResultadoPaulElder> ejecutar(Config config, Entrada entrada, Contexto ctx) {
        Validacion validacion = validar(config, entrada);
        if (!validacion.esValida()) {
            throw new IllegalArgumentException("Entrada inválida para " + ID + ": " + validacion.errores());
        }
        Set<String> adoptados = entrada.adoptados();
        List<ResultadoPaulElder.ElementoEvaluado> elementos = new ArrayList<>();
        List<AfirmacionConRol> afirmaciones = new ArrayList<>();
        for (Elemento e : Elemento.values()) {
            if (!config.elementos().contains(e)) {
                continue;
            }
            String texto = Textos.vacio(entrada.texto(e)) ? null : entrada.texto(e).strip();
            boolean delModelo = texto != null && adoptados.contains(e.toString());
            elementos.add(new ResultadoPaulElder.ElementoEvaluado(e.toString(), e.nombre(), texto, texto == null ? e.pregunta() : null, delModelo));
            if (texto != null && (e == Elemento.SUPUESTOS || e == Elemento.INFERENCIAS)) {
                afirmaciones.add(new AfirmacionConRol(ctx.nuevoId().get(), texto, TipoAfirmacion.HECHO,
                        e == Elemento.SUPUESTOS ? RolAfirmacion.SUPUESTO : RolAfirmacion.CONCLUSION, SentidoAfirmacion.PRODUCIDA,
                        delModelo ? OrigenAfirmacion.MODELO : OrigenAfirmacion.USUARIO, true));
            }
        }
        List<ResultadoPaulElder.EstandarEvaluado> estandares = new ArrayList<>();
        for (Estandar s : Estandar.values()) {
            if (!config.estandares().contains(s)) {
                continue;
            }
            Integer p = entrada.puntaje(s);
            String estado = p == null ? "sin puntuar" : p <= 4 ? "bajo" : "puntuado";
            estandares.add(new ResultadoPaulElder.EstandarEvaluado(s.toString(), s.nombre(), p, estado, p != null && p <= 4 ? s.pregunta() : null));
        }
        int llenos = (int) elementos.stream().filter(ResultadoPaulElder.ElementoEvaluado::lleno).count();
        int puntuados = (int) estandares.stream().filter(s -> s.puntaje() != null).count();
        boolean suficiente = llenos >= config.umbral();
        String tema = entrada.tema().strip();
        List<Pendiente> pendientes = new ArrayList<>();
        if (!suficiente) {
            List<String> vacios = elementos.stream().filter(e -> !e.lleno()).map(e -> e.nombre().toLowerCase()).toList();
            pendientes.add(new Pendiente(TipoPendiente.REVISION, Optional.empty(), Optional.empty(),
                    "Completar los elementos de «" + tema + "»: " + Textos.enumerar(vacios)));
        }
        String resumen = "Tema: " + tema + " · " + llenos + " de " + elementos.size() + " elementos · " + puntuados + " de " + estandares.size()
                + " estándares puntuados.";
        ResultadoPaulElder valor = new ResultadoPaulElder(tema, elementos, llenos, estandares, puntuados, suficiente, config.umbral(),
                entrada.propuestas(), resumen);
        return new Resultado<>(VERSION_ESQUEMA, valor, afirmaciones, pendientes, resumen, List.of(), Resultado.registroDe(entrada.propuestas()),
                Optional.empty());
    }

    @Override
    public ResultadoPaulElder migrar(Json datosViejos, int desdeVersion) {
        throw new IllegalArgumentException(ID + " no tiene versiones anteriores a la " + VERSION_ESQUEMA
                + "; se pidió migrar desde la " + desdeVersion);
    }

    // ---------------------------------------------------------------------------------------------
    // El modelo propone (RF-14)
    // ---------------------------------------------------------------------------------------------

    @Override
    public boolean usaModelo(Config config) {
        return config.modo() == Modo.PLANTILLAS_Y_MODELO;
    }

    @Override
    public List<Propuesta> propuestas(Entrada entrada) {
        return entrada.propuestas();
    }

    /**
     * Una llamada por oración del texto libre, contra los elementos activos más "ninguno"; solo se propone para elementos
     * activos vacíos, una por elemento: la primera oración que el modelo les asigna.
     */
    @Override
    public Propuestas proponer(Config config, Entrada entrada, Contexto ctx, Consumer<String> provisional, int primerNumero) {
        if (Textos.vacio(entrada.textoLibre())) {
            return Propuestas.cayo("Pega primero un texto libre en «Texto para extraer elementos»: el modelo clasifica sus oraciones.");
        }
        List<Elemento> vacios = config.elementos().stream().filter(e -> Textos.vacio(entrada.texto(e))).toList();
        if (vacios.isEmpty()) {
            return Propuestas.de(List.of());
        }
        List<String> etiquetas = new ArrayList<>(config.elementos().stream().map(Elemento::toString).toList());
        etiquetas.add(NINGUNO);
        List<Textos.Oracion> oraciones = Textos.oraciones(entrada.textoLibre());
        Prompts.Prompt prompt = Prompts.de(PROMPT, VERSION_PROMPT);
        return ModeloLocal.conCaida(ctx, ia -> {
            List<Propuesta> nuevas = new ArrayList<>();
            Set<Elemento> propuestos = new HashSet<>();
            for (int i = 0; i < oraciones.size() && i < ORACIONES_MAXIMAS && propuestos.size() < vacios.size(); i++) {
                provisional.accept("Oración " + (i + 1) + " de " + oraciones.size() + "… ");
                String oracion = oraciones.get(i).texto();
                Clasificacion c = ModeloLocal.clasificar(ia, prompt.sistema(Map.of()), prompt.pedido(Map.of("oracion", oracion)), etiquetas);
                Optional<Elemento> e = elemento(c.etiqueta());
                if (e.isPresent() && vacios.contains(e.get()) && propuestos.add(e.get())) {
                    nuevas.add(new Propuesta(Propuesta.codigo(primerNumero + nuevas.size()), e.get().toString(), e.get().nombre(), oracion, c.porQue(),
                            false, c.modelo(), c.digest(), prompt.version()));
                }
            }
            return nuevas;
        });
    }

    /** Adoptar copia la oración en el elemento, con origen modelo. */
    @Override
    public Entrada adoptar(Entrada entrada, String codigo) {
        List<Propuesta> propuestas = Propuesta.adoptar(entrada.propuestas(), codigo);
        Propuesta p = Propuesta.buscar(propuestas, codigo);
        Elemento e = elemento(p.destino()).orElseThrow(() -> new IllegalArgumentException("Elemento desconocido: " + p.destino()));
        return entrada.con(e, p.valor(), propuestas);
    }

    static Optional<Elemento> elemento(String valor) {
        for (Elemento e : Elemento.values()) {
            if (e.toString().equals(valor)) {
                return Optional.of(e);
            }
        }
        return Optional.empty();
    }
}
