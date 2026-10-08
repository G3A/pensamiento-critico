package pensamiento.tecnicas.f1;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import pensamiento.nucleo.AfirmacionConRol;
import pensamiento.nucleo.Argumento;
import pensamiento.nucleo.ArgumentoProducido;
import pensamiento.nucleo.Contexto;
import pensamiento.nucleo.Ejecutor;
import pensamiento.nucleo.EstandarPrueba;
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
import pensamiento.tecnicas.f1.ResultadoToulmin.Estado;
import pensamiento.tecnicas.f1.ResultadoToulmin.Parte;

/**
 * T02 · Modelo de Toulmin (Toulmin 1958). Revisa las seis partes de un argumento como lista de verificación,
 * señala los huecos y dice qué pregunta los llenaría. No usa IA. Las reglas están en docs/ejemplos/T02.md.
 */
@Component
public class EjecutorToulmin implements Ejecutor<EjecutorToulmin.Config, EjecutorToulmin.Entrada, ResultadoToulmin> {

    public static final IdTecnica ID = IdTecnica.de("T02");
    public static final int VERSION_ESQUEMA = 1;
    private static final int LARGO_TEXTO = 300;

    /** Configuración de T02, versión de esquema 1. */
    public record Config(ResultadoToulmin.Nivel nivel, List<Parte> obligatorios, boolean exigirFuenteRespaldo) {
        public Config {
            obligatorios = obligatorios == null ? List.of() : List.copyOf(obligatorios);
        }
    }

    /** Las seis partes, más la fuente del respaldo y la respuesta a la refutación. Todo opcional salvo lo obligatorio. */
    public record Entrada(String afirmacion, String datos, String garantia, String respaldo, String fuenteRespaldo, String calificador,
                          String refutacion, String respuestaRefutacion) {
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
    public Tipos<Config, Entrada, ResultadoToulmin> tipos() {
        return new Tipos<>(Config.class, Entrada.class, ResultadoToulmin.class);
    }

    @Override
    public Validacion validar(Config config, Entrada entrada) {
        List<Validacion.Error> errores = new ArrayList<>();
        if (config.nivel() == null) {
            errores.add(new Validacion.Error("nivel", "Elige el nivel: básico o completo."));
            return new Validacion(errores);
        }
        for (Parte parte : partes(config.nivel())) {
            String texto = texto(entrada, parte);
            if (config.obligatorios().contains(parte) && texto == null) {
                errores.add(new Validacion.Error(parte.toString(), parte.nombre() + ": es obligatorio en tu configuración."));
            }
        }
        String[][] campos = {
                {"afirmacion", entrada.afirmacion()}, {"datos", entrada.datos()}, {"garantia", entrada.garantia()}, {"respaldo", entrada.respaldo()},
                {"fuenteRespaldo", entrada.fuenteRespaldo()}, {"calificador", entrada.calificador()}, {"refutacion", entrada.refutacion()},
                {"respuestaRefutacion", entrada.respuestaRefutacion()}};
        for (String[] campo : campos) {
            if (campo[1] != null && campo[1].strip().length() > LARGO_TEXTO) {
                errores.add(new Validacion.Error(campo[0], "Como máximo " + LARGO_TEXTO + " caracteres."));
            }
        }
        return new Validacion(errores);
    }

    @Override
    public Resultado<ResultadoToulmin> ejecutar(Config config, Entrada entrada, Contexto ctx) {
        Validacion validacion = validar(config, entrada);
        if (!validacion.esValida()) {
            throw new IllegalArgumentException("Entrada inválida para " + ID + ": " + validacion.errores());
        }
        boolean hayAfirmacion = limpio(entrada.afirmacion()) != null;
        List<ResultadoToulmin.ParteEvaluada> partes = new ArrayList<>();
        for (Parte parte : partes(config.nivel())) {
            String texto = texto(entrada, parte);
            String complemento = switch (parte) {
                case RESPALDO -> limpio(entrada.fuenteRespaldo());
                case REFUTACION -> limpio(entrada.respuestaRefutacion());
                default -> null;
            };
            Estado estado = estado(parte, texto, complemento, config.exigirFuenteRespaldo());
            boolean esAfirmacion = texto != null && hayAfirmacion
                    && (parte == Parte.AFIRMACION || parte == Parte.DATOS || parte == Parte.GARANTIA || parte == Parte.REFUTACION);
            partes.add(new ResultadoToulmin.ParteEvaluada(parte, texto, complemento, estado, falta(parte, estado),
                    esAfirmacion ? ctx.nuevoId().get() : null));
        }

        List<AfirmacionConRol> afirmaciones = new ArrayList<>();
        for (ResultadoToulmin.ParteEvaluada p : partes) {
            if (p.afirmacionId() != null) {
                afirmaciones.add(new AfirmacionConRol(p.afirmacionId(), p.texto(), TipoAfirmacion.HECHO,
                        p.parte() == Parte.AFIRMACION ? RolAfirmacion.CONCLUSION : RolAfirmacion.PREMISA, SentidoAfirmacion.PRODUCIDA,
                        OrigenAfirmacion.USUARIO));
            }
        }
        List<ArgumentoProducido> argumentos = argumentos(partes, ctx);
        List<Pendiente> pendientes = pendientes(partes);
        int completas = (int) partes.stream().filter(ResultadoToulmin.ParteEvaluada::completa).count();
        String resumen = resumen(partes, completas);
        ResultadoToulmin valor = new ResultadoToulmin(config.nivel(), partes, completas, partes.size(), resumen);
        return new Resultado<>(VERSION_ESQUEMA, valor, afirmaciones, pendientes, resumen, argumentos);
    }

    @Override
    public ResultadoToulmin migrar(Json datosViejos, int desdeVersion) {
        throw new IllegalArgumentException(ID + " no tiene versiones anteriores a la " + VERSION_ESQUEMA
                + "; se pidió migrar desde la " + desdeVersion);
    }

    private static List<Parte> partes(ResultadoToulmin.Nivel nivel) {
        return nivel == ResultadoToulmin.Nivel.BASICO ? List.of(Parte.AFIRMACION, Parte.DATOS, Parte.GARANTIA) : List.of(Parte.values());
    }

    private static Estado estado(Parte parte, String texto, String complemento, boolean exigirFuente) {
        if (texto == null) {
            return Estado.FALTA;
        }
        if (parte == Parte.RESPALDO && exigirFuente && complemento == null) {
            return Estado.SIN_FUENTE;
        }
        if (parte == Parte.REFUTACION && complemento == null) {
            return Estado.SIN_RESPONDER;
        }
        return Estado.COMPLETA;
    }

    /** La pregunta que completaría la parte (docs/ejemplos/T02.md, regla 4). */
    private static String falta(Parte parte, Estado estado) {
        return switch (estado) {
            case COMPLETA -> null;
            case SIN_FUENTE -> "¿De dónde sale el respaldo? Anota la fuente.";
            case SIN_RESPONDER -> "¿Qué respondes a esa excepción?";
            case FALTA -> switch (parte) {
                case AFIRMACION -> "¿Qué quieres que el otro acepte?";
                case DATOS -> "¿En qué hechos te apoyas?";
                case GARANTIA -> "¿Por qué esos datos llevan a la afirmación?";
                case RESPALDO -> "¿Qué respalda la garantía? Por ejemplo, una norma, un estudio o la experiencia registrada.";
                case CALIFICADOR -> "¿Qué tan seguro estás? Por ejemplo: probablemente, casi siempre, en la mayoría de los casos.";
                case REFUTACION -> "¿En qué casos no se cumpliría la afirmación?";
            };
        };
    }

    /** Uno pro con datos y garantía (la garantía, asumible) y, si hay refutación, uno contra. Sin afirmación, ninguno. */
    private static List<ArgumentoProducido> argumentos(List<ResultadoToulmin.ParteEvaluada> partes, Contexto ctx) {
        Optional<UUID> conclusion = afirmacionDe(partes, Parte.AFIRMACION);
        if (conclusion.isEmpty()) {
            return List.of();
        }
        List<ArgumentoProducido> argumentos = new ArrayList<>();
        List<Argumento.Premisa> premisas = new ArrayList<>();
        afirmacionDe(partes, Parte.DATOS).ifPresent(id -> premisas.add(new Argumento.Premisa(id, premisas.size() + 1, false)));
        afirmacionDe(partes, Parte.GARANTIA).ifPresent(id -> premisas.add(new Argumento.Premisa(id, premisas.size() + 1, true)));
        if (!premisas.isEmpty()) {
            argumentos.add(new ArgumentoProducido(new Argumento(ctx.nuevoId().get(), conclusion.get(), premisas, 1, Argumento.Sentido.PRO),
                    EstandarPrueba.PREPONDERANCIA, Optional.empty(), Optional.empty()));
        }
        afirmacionDe(partes, Parte.REFUTACION).ifPresent(id -> argumentos.add(new ArgumentoProducido(
                new Argumento(ctx.nuevoId().get(), conclusion.get(), List.of(new Argumento.Premisa(id, 1, false)), 1, Argumento.Sentido.CONTRA),
                EstandarPrueba.PREPONDERANCIA, Optional.empty(), Optional.empty())));
        return argumentos;
    }

    private static List<Pendiente> pendientes(List<ResultadoToulmin.ParteEvaluada> partes) {
        List<Pendiente> pendientes = new ArrayList<>();
        Optional<ResultadoToulmin.ParteEvaluada> garantia = parte(partes, Parte.GARANTIA).filter(p -> p.texto() != null);
        parte(partes, Parte.RESPALDO).ifPresent(r -> {
            if (r.estado() == Estado.FALTA && garantia.isPresent()) {
                pendientes.add(new Pendiente(TipoPendiente.VERIFICACION, Optional.ofNullable(garantia.get().afirmacionId()), Optional.empty(),
                        "Buscar respaldo para la garantía: " + garantia.get().texto()));
            } else if (r.estado() == Estado.SIN_FUENTE) {
                pendientes.add(new Pendiente(TipoPendiente.VERIFICACION, garantia.map(ResultadoToulmin.ParteEvaluada::afirmacionId), Optional.empty(),
                        "Buscar una fuente para el respaldo: " + r.texto()));
            }
        });
        parte(partes, Parte.REFUTACION).filter(r -> r.estado() == Estado.SIN_RESPONDER).ifPresent(r ->
                pendientes.add(new Pendiente(TipoPendiente.OBJECION, Optional.ofNullable(r.afirmacionId()), Optional.empty(),
                        "Responder la refutación: " + r.texto())));
        return pendientes;
    }

    /** "Completitud 3 de 6: respaldo sin fuente; falta calificador; refutación sin responder." */
    private static String resumen(List<ResultadoToulmin.ParteEvaluada> partes, int completas) {
        String inicio = "Completitud " + completas + " de " + partes.size() + ": ";
        if (completas == partes.size()) {
            return inicio + "están las " + (partes.size() == 6 ? "seis" : "tres") + " partes.";
        }
        List<String> huecos = new ArrayList<>();
        for (ResultadoToulmin.ParteEvaluada p : partes) {
            String nombre = p.parte().nombre().toLowerCase();
            switch (p.estado()) {
                case FALTA -> huecos.add("falta " + nombre);
                case SIN_FUENTE, SIN_RESPONDER -> huecos.add(nombre + " " + p.estado().texto());
                case COMPLETA -> { }
            }
        }
        return inicio + String.join("; ", huecos) + ".";
    }

    private static Optional<ResultadoToulmin.ParteEvaluada> parte(List<ResultadoToulmin.ParteEvaluada> partes, Parte parte) {
        return partes.stream().filter(p -> p.parte() == parte).findFirst();
    }

    private static Optional<UUID> afirmacionDe(List<ResultadoToulmin.ParteEvaluada> partes, Parte parte) {
        return parte(partes, parte).map(ResultadoToulmin.ParteEvaluada::afirmacionId);
    }

    private static String texto(Entrada e, Parte parte) {
        return limpio(switch (parte) {
            case AFIRMACION -> e.afirmacion();
            case DATOS -> e.datos();
            case GARANTIA -> e.garantia();
            case RESPALDO -> e.respaldo();
            case CALIFICADOR -> e.calificador();
            case REFUTACION -> e.refutacion();
        });
    }

    private static String limpio(String texto) {
        return texto == null || texto.isBlank() ? null : texto.strip();
    }
}
