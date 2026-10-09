package pensamiento.flujos;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import org.springframework.stereotype.Service;

import pensamiento.catalogo.BancoSocratico;
import pensamiento.catalogo.MapeadorJson;
import pensamiento.expediente.GuardadoDeEjecuciones;
import pensamiento.nucleo.AfirmacionConRol;
import pensamiento.nucleo.CambioOpinion;
import pensamiento.nucleo.Contexto;
import pensamiento.nucleo.Ejecucion;
import pensamiento.nucleo.Expediente;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Json;
import pensamiento.nucleo.Propuesta;
import pensamiento.nucleo.Resultado;
import pensamiento.nucleo.RolAfirmacion;
import pensamiento.nucleo.SesionConsejero;
import pensamiento.nucleo.TurnoConsejero;
import pensamiento.nucleo.Uuid7;
import pensamiento.nucleo.puertos.Clasificacion;
import pensamiento.nucleo.puertos.Ia;
import pensamiento.nucleo.puertos.Reloj;
import pensamiento.nucleo.puertos.RepositorioExpediente;
import pensamiento.nucleo.puertos.RepositorioSesiones;
import pensamiento.tecnicas.comun.ModeloLocal;
import pensamiento.tecnicas.comun.Prompts;
import pensamiento.tecnicas.comun.Redaccion;
import pensamiento.tecnicas.comun.Textos;
import pensamiento.tecnicas.f1.EjecutorPaulElder;
import pensamiento.tecnicas.f2.EjecutorEscalera;
import pensamiento.tecnicas.f2.EjecutorPreguntasSocraticas;
import pensamiento.tecnicas.f2.EstrategiaSocratica;
import pensamiento.tecnicas.f6.EjecutorEquipoRojo;
import pensamiento.tecnicas.f6.EjecutorSeisSombreros;

/**
 * Flujo C · Consejero socrático (P15 y P16): el motor híbrido. En cada turno elige qué toca preguntar con la estrategia
 * de la técnica del modo (T08 en ensayo y decisión, T10 en escalera, T35 en sombreros y T36 en debate) y saca la pregunta
 * del banco; si la sesión usa el modelo, el modelo solo redacta esa misma pregunta y el validador del turno la rechaza y
 * reintenta hasta dos veces antes de dejar la del banco. Al cerrar, guarda la ejecución de la técnica del modo con lo que
 * pasó en la sesión y el cambio de opinión. Dominio puro: sin web ni Spring AI; la transacción la abre quien llama y el
 * modelo se llama fuera de ella. Las reglas están en docs/consejero.md.
 */
@Service
public class Consejero {

    /** El largo máximo del texto que la persona escribe en un turno. */
    public static final int LARGO_RESPUESTA = 600;

    /** La sesión no existe o es de otra persona: para quien pregunta, es lo mismo. */
    public static class NoEncontrada extends RuntimeException {
        public NoEncontrada() {
            super("No hay una sesión con ese identificador");
        }
    }

    /** Lo que no se puede hacer en el estado en que está la sesión (cerrada, sin cierre respondido…), con su motivo. */
    public static class NoPermitido extends RuntimeException {
        public NoPermitido(String motivo) {
            super(motivo);
        }
    }

    /**
     * Lo que el motor eligió para el turno del Consejero: su paso, la pregunta del banco, cómo se lee el turno y, si se
     * puede redactar con el modelo, el pedido.
     */
    public record Paso(String paso, String pregunta, String rotulo, String porque, Optional<Pedido> pedido) {
    }

    /** El pedido al modelo: el prompt versionado, los datos y el largo máximo de la pregunta. */
    public record Pedido(String prompt, int version, Map<String, String> datos, int palabrasMaximas) {
    }

    /** El texto que quedó tras redactar: el validado o el del banco, con su origen, intentos y registro. */
    public record Redactado(String texto, TurnoConsejero.Origen origen, int intentos, Optional<Ejecucion.RegistroModelo> modelo,
                            List<Redaccion.Intento> detalle) {
    }

    private final RepositorioSesiones sesiones;
    private final RepositorioExpediente expedientes;
    private final GuardadoDeEjecuciones guardado;
    private final Reloj reloj;
    private final EjecutorPreguntasSocraticas t08;
    private final EjecutorEscalera t10;
    private final EjecutorSeisSombreros t35;
    private final EjecutorEquipoRojo t36;
    private final EstrategiaSocratica estrategia;
    private final BancoSocratico banco;

    public Consejero(RepositorioSesiones sesiones, RepositorioExpediente expedientes, GuardadoDeEjecuciones guardado, Reloj reloj,
                     EjecutorPreguntasSocraticas t08, EjecutorEscalera t10, EjecutorSeisSombreros t35, EjecutorEquipoRojo t36) {
        this.sesiones = sesiones;
        this.expedientes = expedientes;
        this.guardado = guardado;
        this.reloj = reloj;
        this.t08 = t08;
        this.t10 = t10;
        this.t35 = t35;
        this.t36 = t36;
        this.estrategia = EstrategiaSocratica.delCatalogo();
        this.banco = BancoSocratico.delCatalogo();
    }

    // ---------------------------------------------------------------------------------------------
    // Sesión
    // ---------------------------------------------------------------------------------------------

    /**
     * Abre una sesión con el primer turno del Consejero. Si no se eligió un expediente, crea uno con el prefijo
     * "Consejero: " para guardar ahí todo lo de la sesión.
     *
     * @param config     la configuración de la técnica del modo que la persona tiene guardada (o la de fábrica)
     * @param redactara  si el primer turno lo va a redactar el modelo (queda "redactando" con la pregunta del banco)
     */
    /** Un turno del Consejero recién agregado y el paso que eligió el motor (con el pedido al modelo, si se puede redactar). */
    public record Nuevo(TurnoConsejero turno, Paso paso) {
    }

    /** La sesión abierta y su primer turno. */
    public record Iniciada(SesionConsejero sesion, Nuevo primero) {
    }

    public Iniciada iniciar(UUID usuarioId, UUID institucionId, SesionConsejero.Modo modo, String postura, List<SesionConsejero.Razon> razones,
                                   Json config, boolean usaModelo, Optional<Integer> confianzaAntes, Optional<UUID> expedienteId, boolean redactara) {
        if (Textos.vacio(postura)) {
            throw new NoPermitido("Escribe tu postura para empezar.");
        }
        if (modo == SesionConsejero.Modo.DEBATE && (razones.isEmpty() || razones.stream().anyMatch(r -> Textos.vacio(r.texto())))) {
            throw new NoPermitido("En el modo debate, escribe al menos una razón de tu postura.");
        }
        if (confianzaAntes.isPresent() && (confianzaAntes.get() < 0 || confianzaAntes.get() > 100)) {
            throw new NoPermitido("La confianza va de 0 a 100.");
        }
        Instant ahora = reloj.ahora();
        UUID expediente = expedienteId.filter(e -> expedientes.porId(usuarioId, e).isPresent()).orElseGet(() -> expedientes.guardar(
                new Expediente(Uuid7.en(ahora), usuarioId, institucionId, "Consejero: " + recortar(postura.strip(), 80), Optional.empty(),
                        Expediente.Estado.ABIERTO, ahora)).id());
        List<SesionConsejero.Razon> limpias = razones.stream().map(r -> new SesionConsejero.Razon(r.texto().strip(),
                Textos.vacio(r.apoyo()) ? "no_se" : r.apoyo())).toList();
        SesionConsejero s = new SesionConsejero(Uuid7.en(ahora), usuarioId, institucionId, Optional.of(expediente), modo, postura.strip(), limpias, config,
                usaModelo, confianzaAntes, false, SesionConsejero.Estado.ABIERTA, Optional.empty(), Optional.empty(), Optional.empty(), ahora, Optional.empty());
        sesiones.crear(s);
        Paso p = siguiente(s, List.of()).orElseThrow();
        TurnoConsejero primero = turnoDelConsejero(s, 1, p, redactara && p.pedido().isPresent(), ahora);
        sesiones.agregarTurno(usuarioId, institucionId, primero);
        return new Iniciada(s, new Nuevo(primero, p));
    }

    public SesionConsejero sesion(UUID usuarioId, UUID sesionId) {
        return sesiones.porId(usuarioId, sesionId).orElseThrow(NoEncontrada::new);
    }

    public List<SesionConsejero> historial(UUID usuarioId) {
        return sesiones.deUsuario(usuarioId);
    }

    public List<TurnoConsejero> turnos(UUID usuarioId, UUID sesionId) {
        return sesiones.turnos(usuarioId, sesionId);
    }

    /**
     * La respuesta de la persona al último turno del Consejero y, si no era el cierre, el turno siguiente del Consejero.
     * Devuelve ese turno siguiente, o vacío si la persona respondió el cierre y la sesión queda lista para cerrarse.
     */
    public Optional<Nuevo> responder(UUID usuarioId, UUID sesionId, String texto, boolean redactara) {
        SesionConsejero s = sesion(usuarioId, sesionId);
        if (s.cerrada()) {
            throw new NoPermitido("Esta sesión ya está cerrada.");
        }
        if (Textos.vacio(texto)) {
            throw new NoPermitido("Escribe tu respuesta.");
        }
        if (texto.strip().length() > LARGO_RESPUESTA) {
            throw new NoPermitido("La respuesta tiene más de " + LARGO_RESPUESTA + " caracteres.");
        }
        List<TurnoConsejero> turnos = sesiones.turnos(usuarioId, sesionId);
        if (turnos.isEmpty() || !turnos.getLast().delConsejero()) {
            throw new NoPermitido("Ya respondiste: espera la pregunta que sigue.");
        }
        Instant ahora = reloj.ahora();
        TurnoConsejero pregunta = turnos.getLast();
        TurnoConsejero respuesta = new TurnoConsejero(Uuid7.en(ahora), sesionId, turnos.size() + 1, TurnoConsejero.Rol.PERSONA, pregunta.paso(), texto.strip(),
                TurnoConsejero.Origen.PERSONA, TurnoConsejero.Estado.LISTO, 0, Optional.empty(), Optional.empty(), Optional.empty(), false, ahora);
        sesiones.agregarTurno(usuarioId, s.institucionId(), respuesta);
        if (pregunta.cierre()) {
            return Optional.empty();
        }
        List<TurnoConsejero> conRespuesta = new ArrayList<>(turnos);
        conRespuesta.add(respuesta);
        Optional<Paso> p = siguiente(s, conRespuesta);
        if (p.isEmpty()) {
            return Optional.empty();
        }
        TurnoConsejero nuevo = turnoDelConsejero(s, conRespuesta.size() + 1, p.get(), redactara && p.get().pedido().isPresent(), ahora);
        sesiones.agregarTurno(usuarioId, s.institucionId(), nuevo);
        return Optional.of(new Nuevo(nuevo, p.get()));
    }

    /** "Ir al cierre": la próxima pregunta es la de falsación. Si la última pregunta no se respondió, la reemplaza el cierre. */
    public Optional<Nuevo> irAlCierre(UUID usuarioId, UUID sesionId) {
        SesionConsejero s = sesion(usuarioId, sesionId);
        if (s.cerrada()) {
            throw new NoPermitido("Esta sesión ya está cerrada.");
        }
        sesiones.pedirCierre(usuarioId, sesionId);
        List<TurnoConsejero> turnos = sesiones.turnos(usuarioId, sesionId);
        if (turnos.stream().anyMatch(TurnoConsejero::cierre)) {
            return Optional.empty();
        }
        Instant ahora = reloj.ahora();
        Paso paso = pasoCierre(s);
        TurnoConsejero cierre = turnoDelConsejero(s, turnos.size() + 1, paso, false, ahora);
        sesiones.agregarTurno(usuarioId, s.institucionId(), cierre);
        return Optional.of(new Nuevo(cierre, paso));
    }

    /** Lista para cerrar: la persona ya respondió la pregunta de falsación. */
    public static boolean listaParaCerrar(List<TurnoConsejero> turnos) {
        for (int i = 0; i < turnos.size() - 1; i++) {
            if (turnos.get(i).cierre() && turnos.get(i).delConsejero() && !turnos.get(i + 1).delConsejero()) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------------------------------------
    // El motor: qué toca preguntar
    // ---------------------------------------------------------------------------------------------

    /** Las respuestas de la persona a preguntas que no son el cierre, en orden. */
    static List<String> respuestas(List<TurnoConsejero> turnos) {
        return turnos.stream().filter(t -> !t.delConsejero() && !t.cierre()).map(TurnoConsejero::texto).toList();
    }

    /**
     * El turno que toca, o vacío si ya se preguntó el cierre. Es determinista: con la configuración de la sesión y las
     * mismas respuestas da el mismo paso.
     */
    public Optional<Paso> siguiente(SesionConsejero s, List<TurnoConsejero> turnos) {
        if (turnos.stream().anyMatch(TurnoConsejero::cierre)) {
            return Optional.empty();
        }
        List<String> respuestas = respuestas(turnos);
        String referencia = respuestas.isEmpty() ? s.postura() : respuestas.getLast();
        if (s.cierrePedido()) {
            return Optional.of(pasoCierre(s));
        }
        return switch (s.modo()) {
            case ENSAYO, DECISION -> {
                EjecutorPreguntasSocraticas.Config c = MapeadorJson.leer(s.config(), EjecutorPreguntasSocraticas.Config.class);
                EstrategiaSocratica.ModoSesion modo = s.modo() == SesionConsejero.Modo.ENSAYO ? EstrategiaSocratica.ModoSesion.ENSAYO
                        : EstrategiaSocratica.ModoSesion.DECISION;
                EstrategiaSocratica.Recorrido r = estrategia.recorrer(c.parametros(modo), s.postura(), respuestas, false);
                yield Optional.of(r.siguiente().map(m -> new Paso(m.tipo() + "/" + m.elemento(), m.pregunta(),
                        "consejero · " + estrategia.nombreDe(m.tipo()) + " · " + m.elemento().nombre().toLowerCase(), m.porque(),
                        Optional.of(new Pedido(EjecutorPreguntasSocraticas.PROMPT, EjecutorPreguntasSocraticas.VERSION_PROMPT,
                                EjecutorPreguntasSocraticas.datosDelPrompt(banco, m, referencia), EjecutorPreguntasSocraticas.PALABRAS_MAXIMAS))))
                        .orElseGet(() -> pasoCierre(s)));
            }
            case ESCALERA -> {
                EjecutorEscalera.Config c = MapeadorJson.leer(s.config(), EjecutorEscalera.Config.class);
                yield Optional.of(EjecutorEscalera.siguiente(c, respuestas.size()).map(p -> {
                    BancoSocratico.Peldano b = banco.peldano(p.toString());
                    Map<String, String> datos = Map.of("turno", referencia, "tipo", "escalera de inferencia",
                            "descripcion", "una pregunta sobre el peldaño de " + b.nombre().toLowerCase() + " de la escalera de inferencia",
                            "elemento", b.nombre().toLowerCase(), "banco", b.pregunta());
                    return new Paso(p.toString(), b.pregunta(), "consejero · peldaño " + (p.ordinal() + 1) + " · " + b.nombre().toLowerCase(),
                            "Un peldaño por turno, en el sentido de la configuración.", Optional.of(new Pedido(EjecutorPreguntasSocraticas.PROMPT,
                            EjecutorPreguntasSocraticas.VERSION_PROMPT, datos, EjecutorPreguntasSocraticas.PALABRAS_MAXIMAS)));
                }).orElseGet(() -> pasoCierre(s)));
            }
            case SOMBREROS -> {
                EjecutorSeisSombreros.Config c = MapeadorJson.leer(s.config(), EjecutorSeisSombreros.Config.class);
                EjecutorSeisSombreros.Ronda ronda = EjecutorSeisSombreros.siguiente(c, respuestas.size());
                yield Optional.of(switch (ronda) {
                    case EjecutorSeisSombreros.RondaSombrero rs -> {
                        BancoSocratico.Sombrero b = banco.sombrero(rs.sombrero().toString());
                        Map<String, String> datos = Map.of("turno", referencia, "tipo", "sombrero " + b.nombre().toLowerCase(),
                                "descripcion", "una pregunta para mirar el tema desde el sombrero " + b.nombre().toLowerCase() + " (" + b.mira() + ")",
                                "elemento", "notas del sombrero " + b.nombre().toLowerCase(), "banco", b.pregunta());
                        yield new Paso(rs.sombrero().toString(), b.pregunta(), "consejero · sombrero " + b.nombre().toLowerCase(),
                                "Una ronda por sombrero, en el orden de la configuración.", Optional.of(new Pedido(EjecutorPreguntasSocraticas.PROMPT,
                                EjecutorPreguntasSocraticas.VERSION_PROMPT, datos, EjecutorPreguntasSocraticas.PALABRAS_MAXIMAS)));
                    }
                    case EjecutorSeisSombreros.RondaSintesis rs -> new Paso(TurnoConsejero.SINTESIS, banco.sintesis(), "consejero · síntesis",
                            "Después de los sombreros, la síntesis.", Optional.empty());
                    case EjecutorSeisSombreros.RondaCierre rc -> pasoCierre(s);
                });
            }
            case DEBATE -> {
                EjecutorEquipoRojo.Config c = MapeadorJson.leer(s.config(), EjecutorEquipoRojo.Config.class);
                List<EjecutorEquipoRojo.AtaquePlaneado> plan = t36.planificar(bancoDelDebate(c), razones(s));
                if (respuestas.size() >= plan.size()) {
                    yield Optional.of(pasoCierre(s));
                }
                EjecutorEquipoRojo.AtaquePlaneado a = plan.get(respuestas.size());
                String razon = s.razones().get(a.razon()).texto();
                yield Optional.of(new Paso(a.codigo(), a.texto(), "equipo rojo · ataque " + a.codigo().substring(1) + " de " + plan.size() + " · a R" + (a.razon() + 1),
                        a.esquema() == null ? "La razón no tiene un esquema reconocible: ataque genérico." : "Debilidad: " + a.esquema() + ", pregunta " + a.pregunta() + ".",
                        Optional.of(new Pedido(EjecutorEquipoRojo.PROMPT, EjecutorEquipoRojo.VERSION_PROMPT, t36.datosDelPrompt(s.postura(), razon, a),
                                EjecutorEquipoRojo.PALABRAS_MAXIMAS))));
            }
        };
    }

    /** En el Consejero los ataques salen del banco; si la persona tiene configurado el modo a mano, igual se usa el banco. */
    private static EjecutorEquipoRojo.Config bancoDelDebate(EjecutorEquipoRojo.Config c) {
        return new EjecutorEquipoRojo.Config(c.intensidad(), c.numeroAtaques(), c.esquemas(), EjecutorEquipoRojo.Modo.BANCO);
    }

    private static List<EjecutorEquipoRojo.Razon> razones(SesionConsejero s) {
        return s.razones().stream().map(r -> new EjecutorEquipoRojo.Razon(r.texto(), EjecutorEquipoRojo.Apoyo.valueOf(r.apoyo().toUpperCase()))).toList();
    }

    private Paso pasoCierre(SesionConsejero s) {
        return new Paso(TurnoConsejero.CIERRE, banco.cierre().replace("{{postura}}", Textos.comoClausula(s.postura())), "consejero · cierre",
                "Toda sesión termina con la pregunta de falsación.", Optional.empty());
    }

    private static TurnoConsejero turnoDelConsejero(SesionConsejero s, int numero, Paso p, boolean redactara, Instant ahora) {
        return new TurnoConsejero(Uuid7.en(ahora), s.id(), numero, TurnoConsejero.Rol.CONSEJERO, p.paso(), p.pregunta(), TurnoConsejero.Origen.BANCO,
                redactara ? TurnoConsejero.Estado.REDACTANDO : TurnoConsejero.Estado.LISTO, 0, Optional.empty(), Optional.empty(), Optional.of(p.porque()), false,
                ahora);
    }

    // ---------------------------------------------------------------------------------------------
    // El modelo redacta (fuera de toda transacción)
    // ---------------------------------------------------------------------------------------------

    /**
     * Redacta con el modelo la pregunta que el motor ya eligió para el turno. Si el modelo no está, se agota el tiempo o
     * ningún intento pasa el validador, queda la pregunta del banco. Nunca lanza por el modelo.
     */
    public Redactado redactar(Optional<Ia> ia, Paso paso, Consumer<String> provisional) {
        if (ia.isEmpty() || paso.pedido().isEmpty()) {
            return new Redactado(paso.pregunta(), TurnoConsejero.Origen.BANCO, 0, Optional.empty(), List.of());
        }
        Pedido p = paso.pedido().get();
        Prompts.Prompt prompt = Prompts.de(p.prompt(), p.version());
        try {
            Redaccion.Redactado r = Redaccion.redactar(ia.get(), prompt.sistema(p.datos()), prompt.pedido(p.datos()), p.palabrasMaximas(), provisional);
            Optional<Ejecucion.RegistroModelo> registro = Optional.of(new Ejecucion.RegistroModelo(r.modelo(), r.digest(), prompt.version(),
                    Propuesta.TEMPERATURA, Propuesta.SEMILLA));
            return r.texto().map(t -> new Redactado(t, TurnoConsejero.Origen.MODELO, r.intentos().size(), registro, r.intentos()))
                    .orElseGet(() -> new Redactado(paso.pregunta(), TurnoConsejero.Origen.BANCO, r.intentos().size(), registro, r.intentos()));
        } catch (pensamiento.nucleo.puertos.ExcepcionIa e) {
            return new Redactado(paso.pregunta(), TurnoConsejero.Origen.BANCO, 0, Optional.empty(), List.of());
        }
    }

    /**
     * El segundo paso (modos ensayo y decisión): clasifica la respuesta contra los ocho elementos de Paul-Elder más
     * "ninguno" con salida estructurada (prompt de T04). Devuelve el elemento propuesto y su porqué solo si es distinto del
     * que el motor eligió para ese turno y todavía está vacío. Nunca lanza por el modelo.
     */
    public Optional<Clasificacion> extraerElemento(Optional<Ia> ia, SesionConsejero s, List<TurnoConsejero> turnos, TurnoConsejero respuesta) {
        if (ia.isEmpty() || (s.modo() != SesionConsejero.Modo.ENSAYO && s.modo() != SesionConsejero.Modo.DECISION) || respuesta.cierre()) {
            return Optional.empty();
        }
        String elegido = respuesta.paso().contains("/") ? respuesta.paso().substring(respuesta.paso().indexOf('/') + 1) : "";
        List<String> etiquetas = new ArrayList<>(List.of(EjecutorPaulElder.Elemento.values()).stream().map(EjecutorPaulElder.Elemento::toString).toList());
        etiquetas.add(EjecutorPaulElder.NINGUNO);
        Prompts.Prompt prompt = Prompts.de(EjecutorPaulElder.PROMPT, EjecutorPaulElder.VERSION_PROMPT);
        try {
            Clasificacion c = ModeloLocal.clasificar(ia.get(), prompt.sistema(Map.of()), prompt.pedido(Map.of("oracion", respuesta.texto())), etiquetas, true);
            if (EjecutorPaulElder.NINGUNO.equals(c.etiqueta()) || c.etiqueta().equals(elegido) || elementosLlenos(s, turnos).contains(c.etiqueta())) {
                return Optional.empty();
            }
            return Optional.of(c);
        } catch (pensamiento.nucleo.puertos.ExcepcionIa e) {
            return Optional.empty();
        }
    }

    /** Los elementos que ya tienen texto: los respondidos y los propuestos que la persona adoptó. */
    private static List<String> elementosLlenos(SesionConsejero s, List<TurnoConsejero> turnos) {
        List<String> llenos = new ArrayList<>();
        for (TurnoConsejero t : turnos) {
            if (!t.delConsejero() && t.paso().contains("/")) {
                llenos.add(t.paso().substring(t.paso().indexOf('/') + 1));
            }
            if (t.propuestaAdoptada()) {
                t.elementoPropuesto().ifPresent(llenos::add);
            }
        }
        return llenos;
    }

    // ---------------------------------------------------------------------------------------------
    // Panel y cierre
    // ---------------------------------------------------------------------------------------------

    /** La configuración y la entrada de la técnica del modo armadas con lo que va de la sesión, y su resultado. */
    public record Armado(Object config, Object entrada, Resultado<?> resultado) {
    }

    /** El resultado de la técnica del modo con lo que va de la sesión: es lo que pinta el panel. */
    public Resultado<?> panel(SesionConsejero s, List<TurnoConsejero> turnos, Contexto ctx, Optional<String> reflexionCambio, Optional<Integer> despues,
                              Optional<String> causa, List<String> comprobados) {
        return armar(s, turnos, ctx, despues, causa, comprobados).resultado();
    }

    public Armado armar(SesionConsejero s, List<TurnoConsejero> turnos, Contexto ctx, Optional<Integer> despues,
                              Optional<String> causa, List<String> comprobados) {
        List<String> respuestas = respuestas(turnos);
        Optional<String> cierre = turnos.stream().filter(t -> !t.delConsejero() && t.cierre()).map(TurnoConsejero::texto).findFirst();
        return switch (s.modo()) {
            case ENSAYO, DECISION -> {
                EjecutorPreguntasSocraticas.Config c = MapeadorJson.leer(s.config(), EjecutorPreguntasSocraticas.Config.class);
                List<Propuesta> propuestas = propuestasDelDialogo(turnos);
                EjecutorPreguntasSocraticas.Entrada e = new EjecutorPreguntasSocraticas.Entrada(s.postura(),
                        s.modo() == SesionConsejero.Modo.ENSAYO ? EstrategiaSocratica.ModoSesion.ENSAYO : EstrategiaSocratica.ModoSesion.DECISION,
                        respuestas.stream().map(EjecutorPreguntasSocraticas.TurnoEntrada::new).toList(), cierre.orElse(null), s.confianzaAntes().orElse(null),
                        despues.orElse(null), causa.orElse(null), propuestas);
                yield new Armado(c, e, t08.ejecutar(c, e, ctx));
            }
            case ESCALERA -> {
                EjecutorEscalera.Config c = MapeadorJson.leer(s.config(), EjecutorEscalera.Config.class);
                List<EjecutorEscalera.Peldano> orden = EjecutorEscalera.enOrden(c);
                String[] textos = new String[EjecutorEscalera.Peldano.values().length];
                for (int i = 0; i < respuestas.size() && i < orden.size(); i++) {
                    textos[orden.get(i).ordinal()] = respuestas.get(i);
                }
                List<EjecutorEscalera.Peldano> marcados = comprobados.stream().map(x -> EjecutorEscalera.Peldano.valueOf(x.toUpperCase()))
                        .filter(EjecutorEscalera.Peldano::comprobable).toList();
                if (respuestas.isEmpty()) {
                    // Sin peldaños respondidos, la postura va en la conclusión (o en el primer peldaño activo): T10 pide al menos uno.
                    EjecutorEscalera.Peldano p = c.peldanos().contains(EjecutorEscalera.Peldano.CONCLUSION) ? EjecutorEscalera.Peldano.CONCLUSION : orden.getFirst();
                    textos[p.ordinal()] = s.postura();
                }
                EjecutorEscalera.Entrada e = new EjecutorEscalera.Entrada(s.postura(), textos[0], textos[1], textos[2], textos[3], textos[4], textos[5], marcados);
                yield new Armado(c, e, conCambio(s, t10.ejecutar(c, e, ctx), despues, causa));
            }
            case SOMBREROS -> {
                EjecutorSeisSombreros.Config c = MapeadorJson.leer(s.config(), EjecutorSeisSombreros.Config.class);
                List<EjecutorSeisSombreros.Sombrero> orden = c.sombreros();
                String[] notas = new String[EjecutorSeisSombreros.Sombrero.values().length];
                for (int i = 0; i < respuestas.size() && i < orden.size(); i++) {
                    notas[orden.get(i).ordinal()] = respuestas.get(i);
                }
                String sintesis = respuestas.size() > orden.size() ? respuestas.get(orden.size()) : null;
                EjecutorSeisSombreros.Entrada e = new EjecutorSeisSombreros.Entrada(s.postura(), notas[0], notas[1], notas[2], notas[3], notas[4], notas[5], sintesis);
                yield new Armado(c, e, conCambio(s, t35.ejecutar(c, e, ctx), despues, causa));
            }
            case DEBATE -> {
                EjecutorEquipoRojo.Config c = bancoDelDebate(MapeadorJson.leer(s.config(), EjecutorEquipoRojo.Config.class));
                List<Propuesta> propuestas = new ArrayList<>();
                int n = 0;
                for (TurnoConsejero t : turnos) {
                    if (t.delConsejero() && t.origen() == TurnoConsejero.Origen.MODELO && t.paso().startsWith("A")) {
                        n++;
                        propuestas.add(propuestaRedactada(n, t.paso(), "Ataque " + t.paso().substring(1), t));
                    }
                }
                c = new EjecutorEquipoRojo.Config(c.intensidad(), c.numeroAtaques(), c.esquemas(),
                        propuestas.isEmpty() ? EjecutorEquipoRojo.Modo.BANCO : EjecutorEquipoRojo.Modo.BANCO_Y_MODELO);
                EjecutorEquipoRojo.Entrada e = new EjecutorEquipoRojo.Entrada(s.postura(), razones(s), List.of(),
                        respuestas.stream().map(EjecutorEquipoRojo.Respuesta::new).toList(), propuestas);
                yield new Armado(c, e, conCambio(s, t36.ejecutar(c, e, ctx), despues, causa));
            }
        };
    }

    /** Las preguntas que redactó el modelo y los elementos adoptados, como propuestas adoptadas de T08 (RNF-07). */
    static List<Propuesta> propuestasDelDialogo(List<TurnoConsejero> turnos) {
        List<Propuesta> propuestas = new ArrayList<>();
        int pregunta = 0;
        for (int i = 0; i < turnos.size(); i++) {
            TurnoConsejero t = turnos.get(i);
            if (t.delConsejero() && !t.cierre()) {
                boolean respondida = i + 1 < turnos.size() && !turnos.get(i + 1).delConsejero();
                if (!respondida) {
                    continue;
                }
                pregunta++;
                if (t.origen() == TurnoConsejero.Origen.MODELO) {
                    propuestas.add(propuestaRedactada(propuestas.size() + 1, String.valueOf(pregunta), "Turno " + pregunta + " · redactada en el Consejero", t));
                }
            }
            if (!t.delConsejero() && t.propuestaAdoptada() && t.elementoPropuesto().isPresent()) {
                propuestas.add(new Propuesta(Propuesta.codigo(propuestas.size() + 1), EjecutorPreguntasSocraticas.ELEMENTO + t.elementoPropuesto().get(),
                        "Elemento · " + t.elementoPropuesto().get(), t.texto(), t.porquePropuesto().orElse(""), true, "", "", ""));
            }
        }
        return propuestas;
    }

    private static Propuesta propuestaRedactada(int numero, String destino, String rotulo, TurnoConsejero t) {
        Ejecucion.RegistroModelo r = t.modelo().orElse(new Ejecucion.RegistroModelo("", "", "", Propuesta.TEMPERATURA, Propuesta.SEMILLA));
        return new Propuesta(Propuesta.codigo(numero), destino, rotulo, t.texto(), "", true, r.modelo(), r.digest(), r.promptVersion());
    }

    /** El cambio de opinión de la sesión sobre la postura (o la conclusión) de la ejecución, si la confianza cambió. */
    private Resultado<?> conCambio(SesionConsejero s, Resultado<?> r, Optional<Integer> despues, Optional<String> causa) {
        if (s.confianzaAntes().isEmpty() || despues.isEmpty() || s.confianzaAntes().get().equals(despues.get())) {
            return r;
        }
        Optional<AfirmacionConRol> sobre = r.afirmaciones().stream().filter(a -> a.rol() == RolAfirmacion.POSTURA).findFirst()
                .or(() -> r.afirmaciones().stream().filter(a -> a.rol() == RolAfirmacion.CONCLUSION).findFirst());
        if (sobre.isEmpty()) {
            return r;
        }
        CambioOpinion.Causa c = causa.filter(x -> !x.isBlank()).map(CambioOpinion.Causa::de).orElse(CambioOpinion.Causa.MANUAL);
        return r.conCambios(List.of(new CambioOpinion.Declarado(Uuid7.en(reloj.ahora()), sobre.get().afirmacionId(), s.confianzaAntes().get(), despues.get(), c)));
    }

    /**
     * Cierra la sesión: guarda la ejecución de la técnica del modo con lo que pasó (y el cambio de opinión, si la confianza
     * cambió) en el expediente de la sesión, y deja la sesión en solo lectura. Exige la respuesta a la pregunta de cierre.
     */
    public Ejecucion cerrar(UUID usuarioId, UUID sesionId, Optional<String> reflexion, Optional<Integer> confianzaDespues, Optional<String> causa,
                            List<String> comprobados, Contexto ctx) {
        SesionConsejero s = sesion(usuarioId, sesionId);
        if (s.cerrada()) {
            throw new NoPermitido("Esta sesión ya está cerrada.");
        }
        List<TurnoConsejero> turnos = sesiones.turnos(usuarioId, sesionId);
        if (!listaParaCerrar(turnos)) {
            throw new NoPermitido("Para cerrar, responde primero la pregunta de cierre: ¿qué te haría cambiar de opinión?");
        }
        if (confianzaDespues.isPresent() && (confianzaDespues.get() < 0 || confianzaDespues.get() > 100)) {
            throw new NoPermitido("La confianza va de 0 a 100.");
        }
        if (causa.isPresent() && !causa.get().isBlank() && !List.of("evidencia", "steelman", "manual").contains(causa.get())) {
            throw new NoPermitido("La causa es evidencia, steelman o manual.");
        }
        Armado a = armar(s, turnos, ctx, confianzaDespues, causa, comprobados);
        Resultado<?> r = a.resultado();
        Json config = MapeadorJson.escribir(a.config());
        Json entrada = MapeadorJson.escribir(a.entrada());
        Instant ahora = reloj.ahora();
        IdTecnica tecnica = s.modo().tecnica();
        Ejecucion nueva = new Ejecucion(Uuid7.en(ahora), usuarioId, s.institucionId(), tecnica, r.versionEsquema(), s.expedienteId(), config, entrada,
                MapeadorJson.escribir(r.valor()), r.resumen(), r.modelo(), "consejero-" + s.id(), ahora);
        Ejecucion guardada = guardado.guardar(nueva, r);
        sesiones.cerrar(usuarioId, sesionId, reflexion.filter(x -> !x.isBlank()).map(String::strip), confianzaDespues, Optional.of(guardada.id()), ahora);
        return guardada;
    }

    public void asociar(UUID usuarioId, UUID sesionId, Optional<UUID> expedienteId) {
        sesion(usuarioId, sesionId);
        if (expedienteId.isPresent() && expedientes.porId(usuarioId, expedienteId.get()).isEmpty()) {
            throw new NoEncontrada();
        }
        sesiones.asociar(usuarioId, sesionId, expedienteId);
    }

    private static String recortar(String texto, int largo) {
        return texto.length() <= largo ? texto : texto.substring(0, largo - 1).strip() + "…";
    }
}
