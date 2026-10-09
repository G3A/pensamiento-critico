package pensamiento.tecnicas.f2;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import org.springframework.stereotype.Component;

import pensamiento.catalogo.BancoSocratico;
import pensamiento.nucleo.AfirmacionConRol;
import pensamiento.nucleo.CambioOpinion;
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
import pensamiento.tecnicas.comun.ModeloLocal;
import pensamiento.tecnicas.comun.Prompts;
import pensamiento.tecnicas.comun.Redaccion;
import pensamiento.tecnicas.comun.Textos;
import pensamiento.tecnicas.f1.EjecutorPaulElder.Elemento;
import pensamiento.tecnicas.f2.EstrategiaSocratica.ModoSesion;
import pensamiento.tecnicas.f2.EstrategiaSocratica.Movimiento;
import pensamiento.tecnicas.f2.EstrategiaSocratica.Orden;
import pensamiento.tecnicas.f2.EstrategiaSocratica.TipoSocratico;

/**
 * T08 · Preguntas socráticas (Paul 1993). El código elige el tipo socrático, el elemento de Paul-Elder y la rama del
 * banco de cada turno (EstrategiaSocratica); el modelo, si se le pide, solo redacta la pregunta que sigue, que no cuenta
 * hasta adoptarla. El resultado es la transcripción, los elementos extraídos y la pregunta de cierre. Las reglas están en
 * docs/ejemplos/T08.md; las mismas mueven al Consejero socrático en sus modos ensayo y decisión.
 */
@Component
public class EjecutorPreguntasSocraticas implements Ejecutor<EjecutorPreguntasSocraticas.Config, EjecutorPreguntasSocraticas.Entrada,
        ResultadoPreguntasSocraticas>, ConModelo<EjecutorPreguntasSocraticas.Config, EjecutorPreguntasSocraticas.Entrada> {

    public static final IdTecnica ID = IdTecnica.de("T08");
    public static final int VERSION_ESQUEMA = 1;
    public static final String PROMPT = "t08-pregunta";
    public static final int VERSION_PROMPT = 2;
    public static final int PALABRAS_MAXIMAS = 40;

    public enum Modo {
        PLANTILLAS, PLANTILLAS_Y_MODELO;

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    /** Configuración de T08, versión de esquema 1. */
    public record Config(List<TipoSocratico> tipos, Orden orden, int turnosMaximos, Modo modo) {
        public Config {
            tipos = tipos == null ? List.of() : List.copyOf(tipos);
        }

        public EstrategiaSocratica.Parametros parametros(ModoSesion modoSesion) {
            return new EstrategiaSocratica.Parametros(Set.copyOf(tipos), orden, turnosMaximos, modoSesion);
        }
    }

    public record TurnoEntrada(String respuesta) {
    }

    /**
     * @param cierre       la respuesta a la pregunta de cierre; con texto, la sesión está cerrada
     * @param causaCambio  evidencia, steelman o manual (manual si falta)
     * @param propuestas   las preguntas que redactó el modelo para un turno ("destino" es el número del turno)
     */
    public record Entrada(String postura, ModoSesion modoSesion, List<TurnoEntrada> turnos, String cierre, Integer confianzaAntes,
                          Integer confianzaDespues, String causaCambio, List<Propuesta> propuestas) {
        public Entrada {
            turnos = turnos == null ? List.of() : List.copyOf(turnos);
            propuestas = propuestas == null ? List.of() : List.copyOf(propuestas);
        }

        List<String> respuestas() {
            return turnos.stream().map(t -> t.respuesta() == null ? "" : t.respuesta().strip()).toList();
        }

        boolean cerrada() {
            return !Textos.vacio(cierre);
        }
    }

    private final EstrategiaSocratica estrategia;

    public EjecutorPreguntasSocraticas() {
        this(EstrategiaSocratica.delCatalogo());
    }

    public EjecutorPreguntasSocraticas(EstrategiaSocratica estrategia) {
        this.estrategia = estrategia;
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
    public Tipos<Config, Entrada, ResultadoPreguntasSocraticas> tipos() {
        return new Tipos<>(Config.class, Entrada.class, ResultadoPreguntasSocraticas.class);
    }

    @Override
    public Validacion validar(Config config, Entrada entrada) {
        List<Validacion.Error> errores = new ArrayList<>();
        if (config.tipos().size() < 2) {
            errores.add(new Validacion.Error("config.tipos", "Activa al menos dos tipos de pregunta."));
        }
        if (config.orden() == null) {
            errores.add(new Validacion.Error("config.orden", "Elige el orden."));
        }
        if (config.turnosMaximos() < 3 || config.turnosMaximos() > 12) {
            errores.add(new Validacion.Error("config.turnosMaximos", "Los turnos máximos van de 3 a 12."));
        }
        if (config.modo() == null) {
            errores.add(new Validacion.Error("config.modo", "Elige el modo."));
        }
        if (Textos.vacio(entrada.postura())) {
            errores.add(new Validacion.Error("postura", "Escribe la postura que quieres que te pregunten."));
        }
        if (entrada.modoSesion() == null) {
            errores.add(new Validacion.Error("modoSesion", "Elige el modo de la sesión."));
        }
        if (entrada.respuestas().stream().anyMatch(Textos::vacio)) {
            errores.add(new Validacion.Error("turnos", "Cada turno necesita tu respuesta."));
        }
        if (entrada.turnos().size() > config.turnosMaximos()) {
            errores.add(new Validacion.Error("turnos", "Hay " + entrada.turnos().size() + " respuestas y la configuración permite "
                    + config.turnosMaximos() + " turnos."));
        }
        confianza(errores, "confianzaAntes", entrada.confianzaAntes());
        confianza(errores, "confianzaDespues", entrada.confianzaDespues());
        if (!Textos.vacio(entrada.causaCambio()) && !List.of("evidencia", "steelman", "manual").contains(entrada.causaCambio())) {
            errores.add(new Validacion.Error("causaCambio", "La causa es evidencia, steelman o manual."));
        }
        for (Propuesta p : entrada.propuestas()) {
            if (!Propuesta.codigoValido(p.codigo()) || !(p.destino().matches("\\d{1,2}") || elementoDe(p).isPresent())) {
                errores.add(new Validacion.Error("propuestas", "Hay una propuesta del modelo que no corresponde a esta técnica."));
                break;
            }
        }
        if (errores.isEmpty()) {
            EstrategiaSocratica.Recorrido r = estrategia.recorrer(config.parametros(entrada.modoSesion()), entrada.postura().strip(),
                    entrada.respuestas(), false);
            if (r.pasos().size() < entrada.turnos().size()) {
                errores.add(new Validacion.Error("turnos", "La sesión llegó al cierre en el turno " + (r.pasos().size() + 1)
                        + ": las respuestas de más no tienen pregunta."));
            }
        }
        return new Validacion(errores);
    }

    private static void confianza(List<Validacion.Error> errores, String campo, Integer valor) {
        if (valor != null && (valor < 0 || valor > 100)) {
            errores.add(new Validacion.Error(campo, "La confianza va de 0 a 100."));
        }
    }

    @Override
    public Resultado<ResultadoPreguntasSocraticas> ejecutar(Config config, Entrada entrada, Contexto ctx) {
        Validacion validacion = validar(config, entrada);
        if (!validacion.esValida()) {
            throw new IllegalArgumentException("Entrada inválida para " + ID + ": " + validacion.errores());
        }
        String postura = entrada.postura().strip();
        EstrategiaSocratica.Recorrido r = estrategia.recorrer(config.parametros(entrada.modoSesion()), postura, entrada.respuestas(), entrada.cerrada());
        List<ResultadoPreguntasSocraticas.Turno> turnos = new ArrayList<>();
        Map<Elemento, String> llenos = new EnumMap<>(Elemento.class);
        for (EstrategiaSocratica.Paso paso : r.pasos()) {
            turnos.add(turno(paso.movimiento(), entrada.propuestas(), paso.respuesta()));
            llenos.put(paso.movimiento().elemento(), paso.respuesta());
        }
        ResultadoPreguntasSocraticas.Turno siguiente = r.siguiente().map(m -> turno(m, entrada.propuestas(), null)).orElse(null);

        // El segundo paso del Consejero: un elemento que el modelo extrajo de una respuesta y la persona adoptó llena ese
        // elemento si sigue vacío, con origen modelo.
        Set<Elemento> delModelo = new HashSet<>();
        for (Propuesta p : entrada.propuestas()) {
            Optional<Elemento> e = elementoDe(p);
            if (p.adoptada() && e.isPresent() && !llenos.containsKey(e.get())) {
                llenos.put(e.get(), p.valor());
                delModelo.add(e.get());
            }
        }
        Set<TipoSocratico> activos = Set.copyOf(config.tipos());
        List<ResultadoPreguntasSocraticas.ElementoPanel> elementos = new ArrayList<>();
        for (Elemento e : Elemento.values()) {
            String estado = llenos.containsKey(e) ? "lleno" : activos.contains(estrategia.tipoDe(e)) ? "pendiente" : "sin preguntar";
            elementos.add(new ResultadoPreguntasSocraticas.ElementoPanel(e.toString(), e.nombre(), estado, llenos.get(e),
                    !llenos.containsKey(e) ? null : delModelo.contains(e) ? "modelo" : "usuario"));
        }
        List<ResultadoPreguntasSocraticas.EstandarPanel> estandares = EstandaresPorReglas.puntuar(estrategia.banco(), postura, llenos);

        List<AfirmacionConRol> afirmaciones = new ArrayList<>();
        UUID posturaId = ctx.nuevoId().get();
        afirmaciones.add(new AfirmacionConRol(posturaId, postura, entrada.modoSesion() == ModoSesion.ENSAYO ? TipoAfirmacion.HECHO
                : TipoAfirmacion.JUICIO_DE_VALOR, RolAfirmacion.POSTURA, SentidoAfirmacion.PRODUCIDA, OrigenAfirmacion.USUARIO));
        if (llenos.containsKey(Elemento.SUPUESTOS)) {
            afirmaciones.add(new AfirmacionConRol(ctx.nuevoId().get(), llenos.get(Elemento.SUPUESTOS), TipoAfirmacion.HECHO, RolAfirmacion.SUPUESTO,
                    SentidoAfirmacion.PRODUCIDA, delModelo.contains(Elemento.SUPUESTOS) ? OrigenAfirmacion.MODELO : OrigenAfirmacion.USUARIO, true));
        }
        if (llenos.containsKey(Elemento.INFERENCIAS)) {
            afirmaciones.add(new AfirmacionConRol(ctx.nuevoId().get(), llenos.get(Elemento.INFERENCIAS), TipoAfirmacion.HECHO,
                    RolAfirmacion.CONCLUSION, SentidoAfirmacion.PRODUCIDA, delModelo.contains(Elemento.INFERENCIAS) ? OrigenAfirmacion.MODELO
                    : OrigenAfirmacion.USUARIO, true));
        }
        String respuestaCierre = entrada.cerrada() ? entrada.cierre().strip() : null;
        if (respuestaCierre != null) {
            afirmaciones.add(new AfirmacionConRol(ctx.nuevoId().get(), respuestaCierre, TipoAfirmacion.HECHO, RolAfirmacion.CONDICION_FALSACION,
                    SentidoAfirmacion.PRODUCIDA, OrigenAfirmacion.USUARIO));
        }
        List<Pendiente> pendientes = new ArrayList<>();
        if (r.tocaCierre() && respuestaCierre == null) {
            pendientes.add(new Pendiente(TipoPendiente.REVISION, Optional.of(posturaId), Optional.empty(), "Responder la pregunta de cierre: " + r.cierre()));
        }
        List<CambioOpinion.Declarado> cambios = new ArrayList<>();
        String cambio = null;
        if (entrada.confianzaAntes() != null && entrada.confianzaDespues() != null && !entrada.confianzaAntes().equals(entrada.confianzaDespues())) {
            CambioOpinion.Causa causa = Textos.vacio(entrada.causaCambio()) ? CambioOpinion.Causa.MANUAL : CambioOpinion.Causa.de(entrada.causaCambio());
            cambios.add(new CambioOpinion.Declarado(ctx.nuevoId().get(), posturaId, entrada.confianzaAntes(), entrada.confianzaDespues(), causa));
            cambio = "Confianza: " + entrada.confianzaAntes() + "% → " + entrada.confianzaDespues() + "% (causa: " + causa + ").";
        }
        int nLlenos = llenos.size();
        String estado = siguiente != null ? "sigue: " + siguiente.tipoNombre() : respuestaCierre != null ? "cierre respondido" : "falta la pregunta de cierre";
        String resumen = Textos.contar(turnos.size(), "turno", "turnos") + " · " + nLlenos + " de 8 elementos · " + estado + ".";
        ResultadoPreguntasSocraticas valor = new ResultadoPreguntasSocraticas(postura, entrada.modoSesion().toString(), turnos, siguiente, r.cierre(),
                r.tocaCierre(), respuestaCierre, elementos, nLlenos, estandares, cambio, entrada.propuestas(), resumen, posturaId);
        return new Resultado<>(VERSION_ESQUEMA, valor, afirmaciones, pendientes, resumen, List.of(), Resultado.registroDe(entrada.propuestas()),
                Optional.empty(), List.of(), cambios);
    }

    /** El prefijo del destino de un elemento extraído por el segundo paso del Consejero ("elemento:supuestos"). */
    public static final String ELEMENTO = "elemento:";

    /** El elemento de una propuesta de elemento extraído; vacío si la propuesta es una pregunta. */
    public static Optional<Elemento> elementoDe(Propuesta p) {
        if (!p.destino().startsWith(ELEMENTO)) {
            return Optional.empty();
        }
        String id = p.destino().substring(ELEMENTO.length());
        for (Elemento e : Elemento.values()) {
            if (e.toString().equals(id)) {
                return Optional.of(e);
            }
        }
        return Optional.empty();
    }

    /** El turno con su pregunta: la de una propuesta adoptada para ese número, o la del banco. */
    private ResultadoPreguntasSocraticas.Turno turno(Movimiento m, List<Propuesta> propuestas, String respuesta) {
        Optional<Propuesta> adoptada = propuestas.stream().filter(p -> p.adoptada() && p.destino().equals(String.valueOf(m.numero()))).findFirst();
        return new ResultadoPreguntasSocraticas.Turno(m.numero(), m.tipo().toString(), estrategia.nombreDe(m.tipo()), m.elemento().toString(),
                m.elemento().nombre(), m.rama(), m.marca(), m.porque(), adoptada.map(Propuesta::valor).orElse(m.pregunta()),
                adoptada.isPresent() ? "modelo" : "banco", respuesta);
    }

    @Override
    public ResultadoPreguntasSocraticas migrar(Json datosViejos, int desdeVersion) {
        throw new IllegalArgumentException(ID + " no tiene versiones anteriores a la " + VERSION_ESQUEMA + "; se pidió migrar desde la " + desdeVersion);
    }

    // ---------------------------------------------------------------------------------------------
    // El modelo propone (RF-14): redacta la pregunta que sigue; el código ya eligió qué preguntar
    // ---------------------------------------------------------------------------------------------

    @Override
    public boolean usaModelo(Config config) {
        return config.modo() == Modo.PLANTILLAS_Y_MODELO;
    }

    @Override
    public List<Propuesta> propuestas(Entrada entrada) {
        return entrada.propuestas();
    }

    @Override
    public Propuestas proponer(Config config, Entrada entrada, Contexto ctx, Consumer<String> provisional, int primerNumero) {
        if (Textos.vacio(entrada.postura()) || entrada.modoSesion() == null) {
            return Propuestas.cayo("Escribe primero la postura y elige el modo de la sesión: el modelo redacta la pregunta que sigue.");
        }
        String postura = entrada.postura().strip();
        EstrategiaSocratica.Recorrido r = estrategia.recorrer(config.parametros(entrada.modoSesion()), postura, entrada.respuestas(), entrada.cerrada());
        if (r.siguiente().isEmpty()) {
            return Propuestas.cayo("La sesión ya llegó al cierre: no hay pregunta que redactar.");
        }
        Movimiento m = r.siguiente().get();
        if (entrada.propuestas().stream().anyMatch(p -> p.destino().equals(String.valueOf(m.numero())))) {
            return Propuestas.de(List.of());
        }
        String referencia = entrada.respuestas().isEmpty() ? postura : entrada.respuestas().getLast();
        Prompts.Prompt prompt = Prompts.de(PROMPT, VERSION_PROMPT);
        Map<String, String> datos = datosDelPrompt(estrategia.banco(), m, referencia);
        return ModeloLocal.conCaida(ctx, ia -> {
            Redaccion.Redactado red = Redaccion.redactar(ia, prompt.sistema(datos), prompt.pedido(datos), PALABRAS_MAXIMAS, provisional);
            if (red.texto().isEmpty()) {
                throw new pensamiento.nucleo.puertos.IaRespuestaInvalida("Ningún intento pasó el validador del turno");
            }
            return List.of(new Propuesta(Propuesta.codigo(primerNumero), String.valueOf(m.numero()),
                    "Turno " + m.numero() + " · " + estrategia.nombreDe(m.tipo()), red.texto().get(), "", false, red.modelo(), red.digest(),
                    prompt.version()));
        });
    }

    /** Los datos del pedido de t08-pregunta: lo último que escribió la persona, el tipo, el elemento y la pregunta del banco. */
    public static Map<String, String> datosDelPrompt(BancoSocratico banco, Movimiento m, String referencia) {
        BancoSocratico.Tipo tipo = banco.tipo(m.tipo().toString());
        return Map.of("turno", referencia, "tipo", tipo.nombre(), "descripcion", tipo.descripcion(), "elemento", m.elemento().nombre().toLowerCase(),
                "banco", m.pregunta());
    }

    @Override
    public Entrada adoptar(Entrada entrada, String codigo) {
        List<Propuesta> propuestas = Propuesta.adoptar(entrada.propuestas(), codigo);
        return new Entrada(entrada.postura(), entrada.modoSesion(), entrada.turnos(), entrada.cierre(), entrada.confianzaAntes(), entrada.confianzaDespues(),
                entrada.causaCambio(), propuestas);
    }
}
