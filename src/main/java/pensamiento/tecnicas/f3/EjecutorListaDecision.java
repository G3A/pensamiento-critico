package pensamiento.tecnicas.f3;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import pensamiento.nucleo.AfirmacionConRol;
import pensamiento.nucleo.Contexto;
import pensamiento.nucleo.Ejecutor;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Json;
import pensamiento.nucleo.OrigenAfirmacion;
import pensamiento.nucleo.Pendiente;
import pensamiento.nucleo.Resultado;
import pensamiento.nucleo.RolAfirmacion;
import pensamiento.nucleo.SentidoAfirmacion;
import pensamiento.nucleo.TipoAfirmacion;
import pensamiento.nucleo.TipoPendiente;
import pensamiento.nucleo.Validacion;
import pensamiento.tecnicas.comun.Textos;

/**
 * T16 · Lista de verificación antes de decidir (Kahneman, Lovallo y Sibony 2011). Recorre los ítems activos, dice cuáles
 * faltan y, si la configuración lo pide, bloquea el guardado mientras falte un obligatorio. No usa IA. Las reglas están
 * en docs/ejemplos/T16.md.
 */
@Component
public class EjecutorListaDecision implements Ejecutor<EjecutorListaDecision.Config, EjecutorListaDecision.Entrada, ResultadoListaDecision> {

    public static final IdTecnica ID = IdTecnica.de("T16");
    public static final int VERSION_ESQUEMA = 1;

    /** Los seis ítems de la versión 1, en orden, con su pregunta. */
    public enum Item {
        INTERES("¿Hay interés personal de quien propone?"),
        ALTERNATIVAS("¿Consideramos al menos dos alternativas?"),
        CIFRAS("¿Las cifras vienen de una fuente independiente?"),
        ESPERAR("¿Qué pasaría si esperamos tres meses?"),
        CONTRARIA("¿Alguien defendió la postura contraria?"),
        REVERSIBLE("¿Se puede deshacer la decisión si sale mal?");

        private final String pregunta;

        Item(String pregunta) {
            this.pregunta = pregunta;
        }

        public String pregunta() {
            return pregunta;
        }

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    /** Configuración de T16, versión de esquema 1. */
    public record Config(List<Item> items, List<Item> obligatorios, boolean bloquearGuardado) {
        public Config {
            items = items == null ? List.of() : List.copyOf(items);
            obligatorios = obligatorios == null ? List.of() : List.copyOf(obligatorios);
        }
    }

    /** @param fecha la de la firma, AAAA-MM-DD */
    public record Entrada(String decision, String interes, String alternativas, String cifras, String esperar, String contraria,
                          String reversible, String firma, String fecha) {
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
    public Tipos<Config, Entrada, ResultadoListaDecision> tipos() {
        return new Tipos<>(Config.class, Entrada.class, ResultadoListaDecision.class);
    }

    @Override
    public Validacion validar(Config config, Entrada entrada) {
        List<Validacion.Error> errores = new ArrayList<>();
        if (config.items().isEmpty()) {
            errores.add(new Validacion.Error("config.items", "Activa al menos un ítem."));
        }
        if (Textos.vacio(entrada.decision())) {
            errores.add(new Validacion.Error("decision", "Escribe la decisión que vas a tomar."));
        }
        if (!Textos.vacio(entrada.fecha())) {
            try {
                LocalDate.parse(entrada.fecha());
            } catch (DateTimeParseException e) {
                errores.add(new Validacion.Error("fecha", "La fecha va como AAAA-MM-DD."));
            }
        }
        return new Validacion(errores);
    }

    @Override
    public Resultado<ResultadoListaDecision> ejecutar(Config config, Entrada entrada, Contexto ctx) {
        Validacion validacion = validar(config, entrada);
        if (!validacion.esValida()) {
            throw new IllegalArgumentException("Entrada inválida para " + ID + ": " + validacion.errores());
        }
        List<ResultadoListaDecision.ItemEvaluado> items = new ArrayList<>();
        List<Pendiente> pendientes = new ArrayList<>();
        int faltanObligatorios = 0;
        for (Item item : Item.values()) {
            if (!config.items().contains(item)) {
                continue;
            }
            String respuesta = Textos.vacio(respuesta(entrada, item)) ? null : respuesta(entrada, item).strip();
            ResultadoListaDecision.Estado estado;
            if (respuesta != null) {
                estado = ResultadoListaDecision.Estado.RESPONDIDO;
            } else if (config.obligatorios().contains(item)) {
                estado = ResultadoListaDecision.Estado.FALTA_OBLIGATORIO;
                faltanObligatorios++;
            } else {
                estado = ResultadoListaDecision.Estado.SIN_RESPONDER;
                pendientes.add(new Pendiente(TipoPendiente.REVISION, Optional.empty(), Optional.empty(), "Responder antes de decidir: " + item.pregunta()));
            }
            items.add(new ResultadoListaDecision.ItemEvaluado(item, item.pregunta(), respuesta, estado));
        }
        int respondidos = (int) items.stream().filter(i -> i.estado() == ResultadoListaDecision.Estado.RESPONDIDO).count();
        String bloqueo = config.bloquearGuardado() && faltanObligatorios > 0
                ? "Guardado bloqueado: faltan " + Textos.contar(faltanObligatorios, "ítem obligatorio", "ítems obligatorios") + "." : null;
        boolean firmada = !Textos.vacio(entrada.firma()) && !Textos.vacio(entrada.fecha());
        String firma = firmada ? "Firmada por " + entrada.firma().strip() + " el " + entrada.fecha().strip() + "."
                : "Sin firmar: escribe quién firma y la fecha.";
        String cola = bloqueo != null ? Textos.comoClausula(bloqueo) + "." : firmada ? Textos.comoClausula(firma) + "." : "sin firmar.";
        String resumen = respondidos + " de " + items.size() + " ítems respondidos · " + cola;
        String decision = entrada.decision().strip();
        List<AfirmacionConRol> afirmaciones = List.of(new AfirmacionConRol(ctx.nuevoId().get(), decision, TipoAfirmacion.JUICIO_DE_VALOR,
                RolAfirmacion.OPCION, SentidoAfirmacion.PRODUCIDA, OrigenAfirmacion.USUARIO));
        ResultadoListaDecision valor = new ResultadoListaDecision(decision, items, respondidos, bloqueo, firma, firmada, resumen);
        return new Resultado<>(VERSION_ESQUEMA, valor, afirmaciones, pendientes, resumen, List.of(), Optional.empty(), Optional.ofNullable(bloqueo));
    }

    private static String respuesta(Entrada e, Item item) {
        return switch (item) {
            case INTERES -> e.interes();
            case ALTERNATIVAS -> e.alternativas();
            case CIFRAS -> e.cifras();
            case ESPERAR -> e.esperar();
            case CONTRARIA -> e.contraria();
            case REVERSIBLE -> e.reversible();
        };
    }

    @Override
    public ResultadoListaDecision migrar(Json datosViejos, int desdeVersion) {
        throw new IllegalArgumentException(ID + " no tiene versiones anteriores a la " + VERSION_ESQUEMA
                + "; se pidió migrar desde la " + desdeVersion);
    }
}
