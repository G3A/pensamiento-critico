package pensamiento.tecnicas.f6;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import org.springframework.stereotype.Component;

import pensamiento.catalogo.BancoAtaques;
import pensamiento.nucleo.AfirmacionConRol;
import pensamiento.nucleo.ConModelo;
import pensamiento.nucleo.Contexto;
import pensamiento.nucleo.Ejecutor;
import pensamiento.nucleo.Esquema;
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
import pensamiento.nucleo.puertos.IaRespuestaInvalida;
import pensamiento.nucleo.puertos.RepositorioEsquemas;
import pensamiento.tecnicas.comun.ModeloLocal;
import pensamiento.tecnicas.comun.Prompts;
import pensamiento.tecnicas.comun.Redaccion;
import pensamiento.tecnicas.comun.Textos;
import pensamiento.tecnicas.f3.ReglasFalacias;

/**
 * T36 · Equipo rojo / abogado del diablo (advocatus diaboli; red teaming). El código identifica la debilidad de cada razón
 * con los esquemas de Walton (por "en qué se apoya" o con las reglas léxicas de T13) y saca los ataques del banco por
 * esquema y pregunta crítica, en rondas; el modelo, si se le pide, solo redacta otro ataque sobre la misma debilidad, que
 * no cuenta hasta adoptarlo. También admite ataques escritos a mano por otra persona. Reglas en docs/ejemplos/T36.md.
 */
@Component
public class EjecutorEquipoRojo implements Ejecutor<EjecutorEquipoRojo.Config, EjecutorEquipoRojo.Entrada, ResultadoEquipoRojo>,
        ConModelo<EjecutorEquipoRojo.Config, EjecutorEquipoRojo.Entrada> {

    public static final IdTecnica ID = IdTecnica.de("T36");
    public static final int VERSION_ESQUEMA = 1;
    public static final String PROMPT = "t36-ataque";
    public static final int VERSION_PROMPT = 1;
    public static final int PALABRAS_MAXIMAS = 45;
    public static final String SIN_ESQUEMA = "sin esquema";

    /** En qué se apoya una razón y el esquema de Walton que le corresponde (nulo: "no sé", lo buscan las reglas). */
    public enum Apoyo {
        LO_DIJO_ALGUIEN("autoridad", "lo dijo alguien"), UN_CASO("ejemplo", "un caso"), COMPARACION("analogia", "una comparación"),
        CAUSA("causa_efecto", "una causa"), CONSECUENCIA("consecuencias", "una consecuencia"), MAYORIA("opinion_popular", "lo que hace la mayoría"),
        SENAL("signo", "una señal"), DEFINICION("clasificacion_verbal", "una definición"), NO_SE(null, "no sé");

        private final String esquema;
        private final String texto;

        Apoyo(String esquema, String texto) {
            this.esquema = esquema;
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

    public enum Modo {
        BANCO, BANCO_Y_MODELO, A_MANO;

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    /** Configuración de T36, versión de esquema 1. "esquemas" es el dominio: los esquemas de Walton de los que salen las debilidades. */
    public record Config(int intensidad, int numeroAtaques, List<String> esquemas, Modo modo) {
        public Config {
            esquemas = esquemas == null ? List.of() : List.copyOf(esquemas);
        }
    }

    public record Razon(String texto, Apoyo apoyo) {
    }

    /** @param razon el código de la razón que ataca ("R1") */
    public record AtaqueEscrito(String texto, String razon) {
    }

    public record Respuesta(String texto) {
    }

    public record Entrada(String postura, List<Razon> razones, List<AtaqueEscrito> ataquesEscritos, List<Respuesta> respuestas,
                          List<Propuesta> propuestas) {
        public Entrada {
            razones = razones == null ? List.of() : List.copyOf(razones);
            ataquesEscritos = ataquesEscritos == null ? List.of() : List.copyOf(ataquesEscritos);
            respuestas = respuestas == null ? List.of() : List.copyOf(respuestas);
            propuestas = propuestas == null ? List.of() : List.copyOf(propuestas);
        }

        public String respuesta(int i) {
            return i < respuestas.size() && !Textos.vacio(respuestas.get(i).texto()) ? respuestas.get(i).texto().strip() : null;
        }
    }

    /** La debilidad que el código identificó en una razón, con las preguntas críticas que va a usar, en orden. */
    public record DebilidadPlaneada(int razon, String esquema, String deDonde, List<Integer> preguntas) {
        public DebilidadPlaneada {
            preguntas = List.copyOf(preguntas);
        }
    }

    /** Un ataque planeado: su código, la razón (desde 0), el esquema (nulo sin esquema), la pregunta crítica y el texto del banco. */
    public record AtaquePlaneado(String codigo, int razon, String esquema, int pregunta, String texto) {
    }

    private final RepositorioEsquemas esquemas;
    private final BancoAtaques banco;

    public EjecutorEquipoRojo(RepositorioEsquemas esquemas) {
        this(esquemas, BancoAtaques.delCatalogo());
    }

    public EjecutorEquipoRojo(RepositorioEsquemas esquemas, BancoAtaques banco) {
        this.esquemas = esquemas;
        this.banco = banco;
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
    public Tipos<Config, Entrada, ResultadoEquipoRojo> tipos() {
        return new Tipos<>(Config.class, Entrada.class, ResultadoEquipoRojo.class);
    }

    static String codigoRazon(int i) {
        return "R" + (i + 1);
    }

    @Override
    public Validacion validar(Config config, Entrada entrada) {
        List<Validacion.Error> errores = new ArrayList<>();
        if (config.intensidad() < 1 || config.intensidad() > 3) {
            errores.add(new Validacion.Error("config.intensidad", "La intensidad va de 1 a 3."));
        }
        if (config.numeroAtaques() < 1 || config.numeroAtaques() > 6) {
            errores.add(new Validacion.Error("config.numeroAtaques", "El número de ataques va de 1 a 6."));
        }
        if (config.modo() == null) {
            errores.add(new Validacion.Error("config.modo", "Elige de dónde salen los ataques."));
        }
        if (Textos.vacio(entrada.postura())) {
            errores.add(new Validacion.Error("postura", "Escribe la postura que quieres que ataquen."));
        }
        if (entrada.razones().isEmpty()) {
            errores.add(new Validacion.Error("razones", "Escribe al menos una razón."));
        } else if (entrada.razones().stream().anyMatch(r -> Textos.vacio(r.texto()) || r.apoyo() == null)) {
            errores.add(new Validacion.Error("razones", "Cada razón necesita su texto y en qué se apoya."));
        }
        if (config.modo() == Modo.A_MANO) {
            if (entrada.ataquesEscritos().isEmpty()) {
                errores.add(new Validacion.Error("ataquesEscritos", "En el modo a mano, escribe al menos un ataque."));
            }
            for (AtaqueEscrito a : entrada.ataquesEscritos()) {
                if (Textos.vacio(a.texto()) || a.razon() == null || !a.razon().strip().toUpperCase().matches("R[1-5]")
                        || Integer.parseInt(a.razon().strip().substring(1)) > entrada.razones().size()) {
                    errores.add(new Validacion.Error("ataquesEscritos", "Cada ataque necesita su texto y la razón que ataca (R1, R2…)."));
                    break;
                }
            }
        }
        for (Propuesta p : entrada.propuestas()) {
            if (!Propuesta.codigoValido(p.codigo()) || !p.destino().matches("A\\d")) {
                errores.add(new Validacion.Error("propuestas", "Hay una propuesta del modelo que no corresponde a esta técnica."));
                break;
            }
        }
        return new Validacion(errores);
    }

    /** La debilidad de cada razón: el esquema por su apoyo o, con "no sé", por la primera marca de las reglas de T13. */
    public List<DebilidadPlaneada> debilidades(Config config, List<Razon> razones) {
        Set<String> activos = Set.copyOf(config.esquemas());
        List<DebilidadPlaneada> debilidades = new ArrayList<>();
        for (int i = 0; i < razones.size(); i++) {
            Razon r = razones.get(i);
            String esquema = null;
            Integer detectada = null;
            String deDonde;
            if (r.apoyo() != Apoyo.NO_SE) {
                esquema = r.apoyo().esquema;
                deDonde = "apoyo: " + r.apoyo().texto();
            } else {
                List<ReglasFalacias.Hallazgo> h = ReglasFalacias.buscar(r.texto().strip(), activos);
                if (h.isEmpty()) {
                    deDonde = "ninguna regla coincide";
                } else {
                    esquema = h.getFirst().regla().esquema();
                    detectada = h.getFirst().regla().pregunta();
                    deDonde = "detectado por las reglas: «" + h.getFirst().pista() + "»";
                }
            }
            if (esquema == null || !activos.contains(esquema)) {
                debilidades.add(new DebilidadPlaneada(i, null, esquema == null ? deDonde : "el esquema no está en el dominio activo", List.of()));
                continue;
            }
            Esquema e = esquemas.porId(esquema).orElseThrow(() -> new IllegalStateException("Falta el esquema " + r.apoyo() + " en el catálogo"));
            List<Integer> preguntas = new ArrayList<>();
            int hasta = config.intensidad() >= 3 ? e.preguntas().size() : Math.min(config.intensidad(), e.preguntas().size());
            if (detectada != null) {
                preguntas.add(detectada);
            }
            for (int n = 1; n <= hasta; n++) {
                if (!preguntas.contains(n)) {
                    preguntas.add(n);
                }
            }
            debilidades.add(new DebilidadPlaneada(i, esquema, deDonde, preguntas));
        }
        return debilidades;
    }

    /** Los ataques del banco, por rondas: la primera pregunta de cada razón, después la segunda, hasta el número de ataques. */
    public List<AtaquePlaneado> planificar(Config config, List<Razon> razones) {
        List<DebilidadPlaneada> debilidades = debilidades(config, razones);
        List<AtaquePlaneado> plan = new ArrayList<>();
        for (int ronda = 0; plan.size() < config.numeroAtaques(); ronda++) {
            boolean alguno = false;
            for (DebilidadPlaneada d : debilidades) {
                if (plan.size() >= config.numeroAtaques()) {
                    break;
                }
                if (d.esquema() == null) {
                    if (ronda == 0) {
                        plan.add(new AtaquePlaneado("A" + (plan.size() + 1), d.razon(), null, 0, banco.sinEsquema()));
                        alguno = true;
                    }
                } else if (ronda < d.preguntas().size()) {
                    int q = d.preguntas().get(ronda);
                    plan.add(new AtaquePlaneado("A" + (plan.size() + 1), d.razon(), d.esquema(), q, banco.texto(d.esquema(), q)));
                    alguno = true;
                }
            }
            if (!alguno) {
                break;
            }
        }
        return plan;
    }

    @Override
    public Resultado<ResultadoEquipoRojo> ejecutar(Config config, Entrada entrada, Contexto ctx) {
        Validacion validacion = validar(config, entrada);
        if (!validacion.esValida()) {
            throw new IllegalArgumentException("Entrada inválida para " + ID + ": " + validacion.errores());
        }
        String postura = entrada.postura().strip();
        List<AfirmacionConRol> afirmaciones = new ArrayList<>();
        afirmaciones.add(new AfirmacionConRol(ctx.nuevoId().get(), postura, TipoAfirmacion.JUICIO_DE_VALOR, RolAfirmacion.POSTURA,
                SentidoAfirmacion.PRODUCIDA, OrigenAfirmacion.USUARIO));
        List<UUID> razonIds = new ArrayList<>();
        for (Razon r : entrada.razones()) {
            UUID id = ctx.nuevoId().get();
            razonIds.add(id);
            afirmaciones.add(new AfirmacionConRol(id, r.texto().strip(), TipoAfirmacion.HECHO, RolAfirmacion.PREMISA, SentidoAfirmacion.PRODUCIDA,
                    OrigenAfirmacion.USUARIO));
        }
        List<DebilidadPlaneada> planeadas = debilidades(config, entrada.razones());
        List<AtaquePlaneado> plan = config.modo() == Modo.A_MANO ? List.of() : planificar(config, entrada.razones());
        List<ResultadoEquipoRojo.Debilidad> debilidades = new ArrayList<>();
        Map<String, Esquema> porId = new HashMap<>();
        for (DebilidadPlaneada d : planeadas) {
            Razon r = entrada.razones().get(d.razon());
            if (d.esquema() == null) {
                debilidades.add(new ResultadoEquipoRojo.Debilidad(codigoRazon(d.razon()), r.texto().strip(), null, SIN_ESQUEMA, d.deDonde(), List.of()));
                continue;
            }
            Esquema e = porId.computeIfAbsent(d.esquema(), id -> esquemas.porId(id).orElseThrow());
            // En los modos con banco, el panel muestra las preguntas que de verdad se usaron en un ataque.
            List<Integer> usadas = config.modo() == Modo.A_MANO ? d.preguntas()
                    : d.preguntas().stream().filter(n -> plan.stream().anyMatch(p -> p.razon() == d.razon() && p.pregunta() == n)).toList();
            List<ResultadoEquipoRojo.Pregunta> preguntas = usadas.stream().map(n -> e.pregunta(n).orElseThrow())
                    .map(q -> new ResultadoEquipoRojo.Pregunta(q.numero(), q.texto(), q.falacia())).toList();
            debilidades.add(new ResultadoEquipoRojo.Debilidad(codigoRazon(d.razon()), r.texto().strip(), e.id(), e.nombre(), d.deDonde(), preguntas));
        }
        List<ResultadoEquipoRojo.Ataque> ataques = new ArrayList<>();
        if (config.modo() == Modo.A_MANO) {
            for (int i = 0; i < entrada.ataquesEscritos().size(); i++) {
                AtaqueEscrito a = entrada.ataquesEscritos().get(i);
                String razon = a.razon().strip().toUpperCase();
                String esquema = debilidades.get(Integer.parseInt(razon.substring(1)) - 1).esquema();
                ataques.add(ataque("A" + (i + 1), razon, esquema, 0, a.texto().strip(), "persona", entrada.respuesta(i)));
            }
        } else {
            for (int i = 0; i < plan.size(); i++) {
                AtaquePlaneado p = plan.get(i);
                Optional<Propuesta> adoptada = entrada.propuestas().stream().filter(x -> x.adoptada() && x.destino().equals(p.codigo())).findFirst();
                ataques.add(ataque(p.codigo(), codigoRazon(p.razon()), p.esquema(), p.pregunta(), adoptada.map(Propuesta::valor).orElse(p.texto()),
                        adoptada.isPresent() ? "modelo" : "banco", entrada.respuesta(i)));
            }
        }
        List<Pendiente> pendientes = new ArrayList<>();
        int respondidos = 0;
        for (ResultadoEquipoRojo.Ataque a : ataques) {
            if (a.respondido()) {
                respondidos++;
            } else {
                UUID razonId = razonIds.get(Integer.parseInt(a.razon().substring(1)) - 1);
                pendientes.add(new Pendiente(TipoPendiente.OBJECION, Optional.of(razonId), Optional.empty(), "Responder al ataque " + a.codigo() + ": "
                        + a.texto()));
            }
        }
        int sinResponder = ataques.size() - respondidos;
        String resumen = Textos.contar(ataques.size(), "ataque", "ataques") + " · " + Textos.contar(respondidos, "respondido", "respondidos") + " · "
                + sinResponder + " sin responder.";
        ResultadoEquipoRojo valor = new ResultadoEquipoRojo(postura, config.modo().toString(), debilidades, ataques, respondidos, sinResponder,
                entrada.propuestas(), resumen);
        return new Resultado<>(VERSION_ESQUEMA, valor, afirmaciones, pendientes, resumen, List.of(), Resultado.registroDe(entrada.propuestas()),
                Optional.empty());
    }

    private static ResultadoEquipoRojo.Ataque ataque(String codigo, String razon, String esquema, int pregunta, String texto, String origen, String respuesta) {
        return new ResultadoEquipoRojo.Ataque(codigo, razon, esquema, pregunta, texto, origen, respuesta, respuesta == null ? "sin responder" : "respondido");
    }

    @Override
    public ResultadoEquipoRojo migrar(Json datosViejos, int desdeVersion) {
        throw new IllegalArgumentException(ID + " no tiene versiones anteriores a la " + VERSION_ESQUEMA + "; se pidió migrar desde la " + desdeVersion);
    }

    // ---------------------------------------------------------------------------------------------
    // El modelo propone (RF-14): redacta otro ataque sobre la debilidad que el código ya eligió
    // ---------------------------------------------------------------------------------------------

    @Override
    public boolean usaModelo(Config config) {
        return config.modo() == Modo.BANCO_Y_MODELO;
    }

    @Override
    public List<Propuesta> propuestas(Entrada entrada) {
        return entrada.propuestas();
    }

    @Override
    public Propuestas proponer(Config config, Entrada entrada, Contexto ctx, Consumer<String> provisional, int primerNumero) {
        if (Textos.vacio(entrada.postura()) || entrada.razones().isEmpty() || entrada.razones().stream().anyMatch(r -> Textos.vacio(r.texto()) || r.apoyo() == null)) {
            return Propuestas.cayo("Escribe primero la postura y tus razones con en qué se apoyan: el modelo ataca la debilidad que el código encuentra.");
        }
        List<AtaquePlaneado> pendientes = planificar(config, entrada.razones()).stream()
                .filter(p -> entrada.propuestas().stream().noneMatch(x -> x.destino().equals(p.codigo()))).toList();
        if (pendientes.isEmpty()) {
            return Propuestas.de(List.of());
        }
        Prompts.Prompt prompt = Prompts.de(PROMPT, VERSION_PROMPT);
        return ModeloLocal.conCaida(ctx, ia -> {
            List<Propuesta> nuevas = new ArrayList<>();
            for (AtaquePlaneado p : pendientes) {
                provisional.accept(p.codigo() + ": ");
                Map<String, String> datos = datosDelPrompt(entrada.postura().strip(), entrada.razones().get(p.razon()).texto().strip(), p);
                Redaccion.Redactado red = Redaccion.redactar(ia, prompt.sistema(datos), prompt.pedido(datos), PALABRAS_MAXIMAS, provisional);
                provisional.accept(" ");
                red.texto().ifPresent(t -> nuevas.add(new Propuesta(Propuesta.codigo(primerNumero + nuevas.size()), p.codigo(),
                        "Ataque " + p.codigo().substring(1) + " · a " + codigoRazon(p.razon()), t, "", false, red.modelo(), red.digest(), prompt.version())));
            }
            if (nuevas.isEmpty()) {
                throw new IaRespuestaInvalida("Ningún ataque pasó el validador del turno");
            }
            return nuevas;
        });
    }

    /** Los datos del pedido de t36-ataque: la postura, la razón y la debilidad que eligió el código. */
    public Map<String, String> datosDelPrompt(String postura, String razon, AtaquePlaneado p) {
        if (p.esquema() == null) {
            return Map.of("postura", postura, "razon", razon, "esquema", "sin esquema reconocible", "pregunta", "¿En qué se apoya esa razón?",
                    "banco", p.texto());
        }
        Esquema e = esquemas.porId(p.esquema()).orElseThrow();
        return Map.of("postura", postura, "razon", razon, "esquema", e.nombre(), "pregunta", e.pregunta(p.pregunta()).orElseThrow().texto(), "banco", p.texto());
    }

    @Override
    public Entrada adoptar(Entrada entrada, String codigo) {
        return new Entrada(entrada.postura(), entrada.razones(), entrada.ataquesEscritos(), entrada.respuestas(), Propuesta.adoptar(entrada.propuestas(), codigo));
    }
}
