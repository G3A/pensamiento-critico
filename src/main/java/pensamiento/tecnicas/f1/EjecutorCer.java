package pensamiento.tecnicas.f1;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
import pensamiento.tecnicas.f1.ResultadoCer.Estado;
import pensamiento.tecnicas.f1.ResultadoCer.Pieza;

/**
 * T03 · Afirmación, evidencia, razonamiento (CER) (McNeill y Krajcik 2008). Arma el argumento en tres piezas, con
 * contraargumento y réplica si la configuración los pide, y cuenta cuántas están completas. No usa IA. Las reglas
 * están en docs/ejemplos/T03.md.
 */
@Component
public class EjecutorCer implements Ejecutor<EjecutorCer.Config, EjecutorCer.Entrada, ResultadoCer> {

    public static final IdTecnica ID = IdTecnica.de("T03");
    public static final int VERSION_ESQUEMA = 1;

    /** Configuración de T03, versión de esquema 1. */
    public record Config(boolean contraargumento, int palabrasMinimasEvidencia, List<Pieza> obligatorias) {
        public Config {
            obligatorias = obligatorias == null ? List.of() : List.copyOf(obligatorias);
        }
    }

    public record Entrada(String afirmacion, String evidencia, String razonamiento, String contraargumento, String replica) {
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
    public Tipos<Config, Entrada, ResultadoCer> tipos() {
        return new Tipos<>(Config.class, Entrada.class, ResultadoCer.class);
    }

    @Override
    public Validacion validar(Config config, Entrada entrada) {
        List<Validacion.Error> errores = new ArrayList<>();
        if (config.palabrasMinimasEvidencia() < 1 || config.palabrasMinimasEvidencia() > 50) {
            errores.add(new Validacion.Error("config.palabrasMinimasEvidencia", "El mínimo de palabras de la evidencia va de 1 a 50."));
        }
        for (Pieza p : config.obligatorias()) {
            if ((p == Pieza.CONTRAARGUMENTO || p == Pieza.REPLICA) && !config.contraargumento()) {
                continue;
            }
            if (Textos.vacio(texto(entrada, p))) {
                errores.add(new Validacion.Error(p.toString(), "Escribe " + p.nombre().toLowerCase() + ": es obligatoria en tu configuración."));
            }
        }
        return new Validacion(errores);
    }

    @Override
    public Resultado<ResultadoCer> ejecutar(Config config, Entrada entrada, Contexto ctx) {
        Validacion validacion = validar(config, entrada);
        if (!validacion.esValida()) {
            throw new IllegalArgumentException("Entrada inválida para " + ID + ": " + validacion.errores());
        }
        List<Pieza> activas = config.contraargumento() ? List.of(Pieza.values())
                : List.of(Pieza.AFIRMACION, Pieza.EVIDENCIA, Pieza.RAZONAMIENTO);
        List<ResultadoCer.PiezaEvaluada> piezas = new ArrayList<>();
        List<AfirmacionConRol> afirmaciones = new ArrayList<>();
        for (Pieza p : activas) {
            String texto = Textos.vacio(texto(entrada, p)) ? null : texto(entrada, p).strip();
            Estado estado;
            String falta = null;
            if (texto == null) {
                estado = Estado.FALTA;
                falta = pregunta(p);
            } else if (p == Pieza.EVIDENCIA && Textos.palabras(texto) < config.palabrasMinimasEvidencia()) {
                estado = Estado.CORTA;
                falta = "La evidencia tiene " + Textos.palabras(texto) + " palabras y pides al menos " + config.palabrasMinimasEvidencia()
                        + ": ¿qué dato concreto la sostiene?";
            } else {
                estado = Estado.COMPLETA;
            }
            UUID afirmacionId = null;
            if (texto != null && (p == Pieza.AFIRMACION || p == Pieza.EVIDENCIA || p == Pieza.CONTRAARGUMENTO)) {
                afirmacionId = ctx.nuevoId().get();
                afirmaciones.add(new AfirmacionConRol(afirmacionId, texto, TipoAfirmacion.HECHO,
                        p == Pieza.AFIRMACION ? RolAfirmacion.CONCLUSION : RolAfirmacion.PREMISA, SentidoAfirmacion.PRODUCIDA, OrigenAfirmacion.USUARIO));
            }
            piezas.add(new ResultadoCer.PiezaEvaluada(p, texto, estado, falta, afirmacionId));
        }
        int completas = (int) piezas.stream().filter(ResultadoCer.PiezaEvaluada::completa).count();
        List<Pendiente> pendientes = new ArrayList<>();
        ResultadoCer.PiezaEvaluada evidencia = piezas.get(1);
        if (!evidencia.completa()) {
            pendientes.add(new Pendiente(TipoPendiente.VERIFICACION, Optional.empty(), Optional.empty(),
                    "Buscar más evidencia para: " + Optional.ofNullable(piezas.getFirst().texto()).orElse("tu afirmación")));
        }
        if (config.contraargumento() && piezas.get(3).texto() != null && piezas.get(4).texto() == null) {
            pendientes.add(new Pendiente(TipoPendiente.OBJECION, Optional.ofNullable(piezas.get(3).afirmacionId()), Optional.empty(),
                    "Responder el contraargumento: " + piezas.get(3).texto()));
        }
        String resumen = resumen(piezas, completas);
        return new Resultado<>(VERSION_ESQUEMA, new ResultadoCer(piezas, completas, piezas.size(), resumen), afirmaciones, pendientes, resumen);
    }

    @Override
    public ResultadoCer migrar(Json datosViejos, int desdeVersion) {
        throw new IllegalArgumentException(ID + " no tiene versiones anteriores a la " + VERSION_ESQUEMA
                + "; se pidió migrar desde la " + desdeVersion);
    }

    private static String resumen(List<ResultadoCer.PiezaEvaluada> piezas, int completas) {
        String inicio = "Completitud " + completas + " de " + piezas.size() + ": ";
        if (completas == piezas.size()) {
            return inicio + "están las " + (piezas.size() == 5 ? "cinco" : "tres") + " piezas.";
        }
        List<String> faltas = piezas.stream().filter(p -> !p.completa())
                .map(p -> p.estado() == Estado.CORTA ? p.pieza().nombre().toLowerCase() + " corta" : "falta " + p.pieza().nombre().toLowerCase())
                .toList();
        return inicio + String.join("; ", faltas) + ".";
    }

    private static String pregunta(Pieza p) {
        return switch (p) {
            case AFIRMACION -> "¿Qué afirmas?";
            case EVIDENCIA -> "¿Qué evidencia tienes? Un dato, un hecho observado o una fuente.";
            case RAZONAMIENTO -> "¿Por qué esa evidencia sostiene la afirmación?";
            case CONTRAARGUMENTO -> "¿Qué diría alguien que no está de acuerdo?";
            case REPLICA -> "¿Qué le respondes a ese contraargumento?";
        };
    }

    private static String texto(Entrada e, Pieza p) {
        return switch (p) {
            case AFIRMACION -> e.afirmacion();
            case EVIDENCIA -> e.evidencia();
            case RAZONAMIENTO -> e.razonamiento();
            case CONTRAARGUMENTO -> e.contraargumento();
            case REPLICA -> e.replica();
        };
    }
}
