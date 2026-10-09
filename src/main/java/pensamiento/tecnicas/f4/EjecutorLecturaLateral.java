package pensamiento.tecnicas.f4;

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

/**
 * T20 · Lectura lateral (Wineburg y McGrew 2017): sale de la página que juzgas y registra qué dicen de ella otras fuentes,
 * si son independientes y su postura; da un veredicto con las que cuentan. No usa IA. Las reglas están en
 * docs/ejemplos/T20.md.
 */
@Component
public class EjecutorLecturaLateral implements Ejecutor<EjecutorLecturaLateral.Config, EjecutorLecturaLateral.Entrada, ResultadoLecturaLateral> {

    public static final IdTecnica ID = IdTecnica.de("T20");
    public static final int VERSION_ESQUEMA = 1;

    public enum Postura {
        CONFIRMA("la confirma"), CONTRADICE("la contradice"), NO_MENCIONA("no la menciona");

        private final String texto;

        Postura(String texto) {
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

    /** Configuración de T20, versión de esquema 1: el mínimo de fuentes externas y si se exige que sean independientes. */
    public record Config(int minimo, boolean exigirIndependencia) {
    }

    public record Externa(String nombre, String dice, Postura postura, boolean independiente) {
    }

    public record Entrada(String original, String afirmacion, List<Externa> externas) {
        public Entrada {
            externas = externas == null ? List.of() : List.copyOf(externas);
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
    public Tipos<Config, Entrada, ResultadoLecturaLateral> tipos() {
        return new Tipos<>(Config.class, Entrada.class, ResultadoLecturaLateral.class);
    }

    @Override
    public Validacion validar(Config config, Entrada entrada) {
        List<Validacion.Error> errores = new ArrayList<>();
        if (config.minimo() < 1 || config.minimo() > 6) {
            errores.add(new Validacion.Error("config.minimo", "El mínimo de fuentes externas va de 1 a 6."));
        }
        if (Textos.vacio(entrada.original())) {
            errores.add(new Validacion.Error("original", "Escribe la fuente que estás juzgando."));
        }
        if (Textos.vacio(entrada.afirmacion())) {
            errores.add(new Validacion.Error("afirmacion", "Escribe lo que afirma esa fuente."));
        }
        List<Externa> externas = conNombre(entrada);
        if (externas.isEmpty() || externas.size() > 6) {
            errores.add(new Validacion.Error("externas", "Registra de una a seis fuentes externas."));
        }
        for (Externa e : externas) {
            if (Textos.vacio(e.dice()) || e.postura() == null) {
                errores.add(new Validacion.Error("externas", "Cada fuente externa necesita qué dice y su postura."));
                break;
            }
        }
        return new Validacion(errores);
    }

    @Override
    public Resultado<ResultadoLecturaLateral> ejecutar(Config config, Entrada entrada, Contexto ctx) {
        Validacion validacion = validar(config, entrada);
        if (!validacion.esValida()) {
            throw new IllegalArgumentException("Entrada inválida para " + ID + ": " + validacion.errores());
        }
        UUID afirmacionId = ctx.nuevoId().get();
        String afirmacion = entrada.afirmacion().strip();
        List<Externa> externas = conNombre(entrada);
        List<ResultadoLecturaLateral.ExternaEvaluada> evaluadas = new ArrayList<>();
        List<String> noCuentan = new ArrayList<>();
        int confirman = 0;
        int contradicen = 0;
        int noMencionan = 0;
        for (int i = 0; i < externas.size(); i++) {
            Externa e = externas.get(i);
            boolean cuenta = !config.exigirIndependencia() || e.independiente();
            if (cuenta) {
                switch (e.postura()) {
                    case CONFIRMA -> confirman++;
                    case CONTRADICE -> contradicen++;
                    case NO_MENCIONA -> noMencionan++;
                }
            } else {
                noCuentan.add(e.nombre().strip());
            }
            evaluadas.add(new ResultadoLecturaLateral.ExternaEvaluada("E" + (i + 1), e.nombre().strip(), e.dice().strip(), e.postura().texto(),
                    e.independiente(), cuenta));
        }
        int cuentan = confirman + contradicen + noMencionan;
        boolean indep = config.exigirIndependencia();
        String veredicto;
        String motivo;
        if (cuentan < config.minimo()) {
            veredicto = "faltan fuentes";
            motivo = "Cuentan " + Textos.contar(cuentan, fuente(indep), fuentes(indep)) + "; la configuración pide " + config.minimo() + ".";
        } else if (confirman > 0 && contradicen > 0) {
            veredicto = "dividida";
            motivo = confirman + (confirman == 1 ? " la confirma" : " la confirman") + " y " + contradicen
                    + (contradicen == 1 ? " la contradice" : " la contradicen") + ": busca el dato original.";
        } else if (contradicen > 0) {
            veredicto = "en duda";
            motivo = Textos.contar(contradicen, fuente(indep), fuentes(indep)) + (contradicen == 1 ? " la contradice" : " la contradicen")
                    + (noMencionan == 0 ? "" : " y " + noMencionan + (noMencionan == 1 ? " no la menciona" : " no la mencionan")) + ".";
        } else if (confirman > 0) {
            veredicto = "respaldada";
            motivo = Textos.contar(confirman, fuente(indep), fuentes(indep)) + (confirman == 1 ? " la confirma" : " la confirman")
                    + " y ninguna la contradice.";
        } else {
            veredicto = "sin eco";
            motivo = indep ? "Ninguna fuente independiente la menciona." : "Ninguna fuente externa la menciona.";
        }
        String nota = !indep || noCuentan.isEmpty() ? null
                : (noCuentan.size() == 1 ? "No cuenta por no ser independiente: " : "No cuentan por no ser independientes: ") + Textos.enumerar(noCuentan) + ".";
        List<Pendiente> pendientes = new ArrayList<>();
        switch (veredicto) {
            case "faltan fuentes" -> pendientes.add(new Pendiente(TipoPendiente.VERIFICACION, Optional.of(afirmacionId), Optional.empty(),
                    (indep ? "Buscar otra fuente externa independiente sobre: " : "Buscar otra fuente externa sobre: ") + afirmacion));
            case "respaldada" -> { }
            default -> pendientes.add(new Pendiente(TipoPendiente.VERIFICACION, Optional.of(afirmacionId), Optional.empty(),
                    "Rastrear el origen de: " + afirmacion));
        }
        String resumen = Textos.mayusculaInicial(veredicto) + " · " + cuentan + " de " + externas.size() + " fuentes externas cuentan.";
        List<AfirmacionConRol> afirmaciones = List.of(new AfirmacionConRol(afirmacionId, afirmacion, TipoAfirmacion.HECHO, RolAfirmacion.HIPOTESIS,
                SentidoAfirmacion.PRODUCIDA, OrigenAfirmacion.USUARIO));
        ResultadoLecturaLateral valor = new ResultadoLecturaLateral(entrada.original().strip(), afirmacion, evaluadas, veredicto, motivo, nota, cuentan,
                config.minimo(), resumen);
        return new Resultado<>(VERSION_ESQUEMA, valor, afirmaciones, pendientes, resumen);
    }

    private static String fuente(boolean independiente) {
        return independiente ? "fuente independiente" : "fuente externa";
    }

    private static String fuentes(boolean independiente) {
        return independiente ? "fuentes independientes" : "fuentes externas";
    }

    private static List<Externa> conNombre(Entrada entrada) {
        return entrada.externas().stream().filter(e -> e != null && !Textos.vacio(e.nombre())).toList();
    }

    @Override
    public ResultadoLecturaLateral migrar(Json datosViejos, int desdeVersion) {
        throw new IllegalArgumentException(ID + " no tiene versiones anteriores a la " + VERSION_ESQUEMA
                + "; se pidió migrar desde la " + desdeVersion);
    }
}
