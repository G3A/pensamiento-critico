package pensamiento.tecnicas.f5;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
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
 * T24 · Razonamiento bayesiano (Bayes 1763; Laplace 1812). Parte de un prior y lo actualiza con cada evidencia, sin
 * redondeos intermedios: posterior = P × Π(si es cierta) / (P × Π(si es cierta) + (100 − P) × Π(si es falsa)). No usa IA.
 * Las reglas están en docs/ejemplos/T24.md.
 */
@Component
public class EjecutorBayes implements Ejecutor<EjecutorBayes.Config, EjecutorBayes.Entrada, ResultadoBayes> {

    public static final IdTecnica ID = IdTecnica.de("T24");
    public static final int VERSION_ESQUEMA = 1;

    public enum Formato {
        PORCENTAJE, ODDS;

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    /** Configuración de T24, versión de esquema 1. */
    public record Config(int priorInicial, Formato formato, int maxEvidencias) {
    }

    /**
     * @param siCierta qué tan probable es ver la evidencia si la afirmación es cierta (0 a 100)
     * @param siFalsa  qué tan probable es verla si es falsa (0 a 100)
     */
    public record Evidencia(String texto, Integer siCierta, Integer siFalsa) {
    }

    /** @param prior de 1 a 99; nulo usa el de la configuración */
    public record Entrada(String afirmacion, Integer prior, List<Evidencia> evidencias) {
        public Entrada {
            evidencias = evidencias == null ? List.of() : List.copyOf(evidencias);
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
    public Tipos<Config, Entrada, ResultadoBayes> tipos() {
        return new Tipos<>(Config.class, Entrada.class, ResultadoBayes.class);
    }

    @Override
    public Validacion validar(Config config, Entrada entrada) {
        List<Validacion.Error> errores = new ArrayList<>();
        if (config.priorInicial() < 1 || config.priorInicial() > 99) {
            errores.add(new Validacion.Error("config.priorInicial", "El prior inicial va de 1 a 99."));
        }
        if (config.formato() == null) {
            errores.add(new Validacion.Error("config.formato", "Elige el formato."));
        }
        if (config.maxEvidencias() < 1 || config.maxEvidencias() > 8) {
            errores.add(new Validacion.Error("config.maxEvidencias", "El máximo de evidencias va de 1 a 8."));
        }
        if (Textos.vacio(entrada.afirmacion())) {
            errores.add(new Validacion.Error("afirmacion", "Escribe la afirmación que quieres actualizar."));
        }
        if (entrada.prior() != null && (entrada.prior() < 1 || entrada.prior() > 99)) {
            errores.add(new Validacion.Error("prior", "El prior va de 1 a 99: con 0% o 100% ninguna evidencia te mueve."));
        }
        if (entrada.evidencias().isEmpty()) {
            errores.add(new Validacion.Error("evidencias", "Escribe al menos una evidencia."));
        } else if (entrada.evidencias().size() > config.maxEvidencias()) {
            errores.add(new Validacion.Error("evidencias", "Tu configuración admite como máximo " + config.maxEvidencias() + " evidencias."));
        }
        for (int i = 0; i < entrada.evidencias().size(); i++) {
            Evidencia e = entrada.evidencias().get(i);
            String campo = "evidencias[" + i + "]";
            if (Textos.vacio(e.texto())) {
                errores.add(new Validacion.Error(campo + ".texto", "Escribe la evidencia E" + (i + 1) + "."));
            }
            if (e.siCierta() == null || e.siCierta() < 0 || e.siCierta() > 100) {
                errores.add(new Validacion.Error(campo + ".siCierta", "Escribe de 0 a 100 qué tan probable es E" + (i + 1) + " si es cierta."));
            }
            if (e.siFalsa() == null || e.siFalsa() < 0 || e.siFalsa() > 100) {
                errores.add(new Validacion.Error(campo + ".siFalsa", "Escribe de 0 a 100 qué tan probable es E" + (i + 1) + " si es falsa."));
            }
            if (e.siCierta() != null && e.siFalsa() != null && e.siCierta() == 0 && e.siFalsa() == 0) {
                errores.add(new Validacion.Error(campo + ".siFalsa", "E" + (i + 1) + " no puede ser imposible en los dos casos."));
            }
        }
        return new Validacion(errores);
    }

    @Override
    public Resultado<ResultadoBayes> ejecutar(Config config, Entrada entrada, Contexto ctx) {
        Validacion validacion = validar(config, entrada);
        if (!validacion.esValida()) {
            throw new IllegalArgumentException("Entrada inválida para " + ID + ": " + validacion.errores());
        }
        boolean porDefecto = entrada.prior() == null;
        int prior = porDefecto ? config.priorInicial() : entrada.prior();
        boolean odds = config.formato() == Formato.ODDS;
        BigInteger n = BigInteger.valueOf(prior);
        BigInteger f = BigInteger.valueOf(100L - prior);
        List<String> avisos = new ArrayList<>();
        if (porDefecto) {
            avisos.add("Usaste el prior por defecto (" + prior + "%): si sabes cada cuántos casos pasa, escribe ese porcentaje.");
        }
        List<ResultadoBayes.Paso> pasos = new ArrayList<>();
        int anterior = porcentaje(n, n.add(f));
        String oddsPrior = odds ? odds(n, f) : null;
        int mayorMovimiento = 0;
        String queMasMovio = null;
        for (int i = 0; i < entrada.evidencias().size(); i++) {
            Evidencia e = entrada.evidencias().get(i);
            n = n.multiply(BigInteger.valueOf(e.siCierta()));
            f = f.multiply(BigInteger.valueOf(e.siFalsa()));
            int posterior = porcentaje(n, n.add(f));
            if (e.siCierta().equals(e.siFalsa())) {
                avisos.add("E" + (i + 1) + " no distingue: es igual de probable si la afirmación es cierta o falsa.");
            }
            int movimiento = Math.abs(posterior - anterior);
            if (movimiento > mayorMovimiento) {
                mayorMovimiento = movimiento;
                queMasMovio = e.texto().strip();
            }
            pasos.add(new ResultadoBayes.Paso("E" + (i + 1), e.texto().strip(), e.siCierta(), e.siFalsa(), razon(e.siCierta(), e.siFalsa()), posterior,
                    odds ? odds(n, f) : null));
            anterior = posterior;
        }
        int inicial = porcentaje(BigInteger.valueOf(prior), BigInteger.valueOf(100));
        int finalP = pasos.getLast().posterior();
        String resumen = "De " + inicial + "%" + (odds ? " (" + oddsPrior + ")" : "") + " a " + finalP + "%"
                + (odds ? " (" + pasos.getLast().odds() + ")" : "") + " con " + Textos.contar(pasos.size(), "evidencia", "evidencias") + ".";
        List<Pendiente> pendientes = queMasMovio == null ? List.of()
                : List.of(new Pendiente(TipoPendiente.VERIFICACION, Optional.empty(), Optional.empty(), "Verificar la evidencia que más te movió: " + queMasMovio));
        String afirmacion = entrada.afirmacion().strip();
        List<AfirmacionConRol> afirmaciones = List.of(new AfirmacionConRol(ctx.nuevoId().get(), afirmacion, TipoAfirmacion.HECHO, RolAfirmacion.HIPOTESIS,
                SentidoAfirmacion.PRODUCIDA, OrigenAfirmacion.USUARIO));
        ResultadoBayes valor = new ResultadoBayes(afirmacion, prior, porDefecto, oddsPrior, pasos, config.formato().toString(), avisos, resumen);
        return new Resultado<>(VERSION_ESQUEMA, valor, afirmaciones, pendientes, resumen);
    }

    /** a / b en porcentaje entero, la mitad hacia arriba. */
    static int porcentaje(BigInteger a, BigInteger b) {
        return new BigDecimal(a.multiply(BigInteger.valueOf(100))).divide(new BigDecimal(b), 0, RoundingMode.HALF_UP).intValueExact();
    }

    /** "0,4", "2", "0,5"; "solo si es cierta" si la evidencia es imposible cuando la afirmación es falsa. */
    static String razon(int siCierta, int siFalsa) {
        if (siFalsa == 0) {
            return "solo si es cierta";
        }
        return decimal(BigDecimal.valueOf(siCierta).divide(BigDecimal.valueOf(siFalsa), 2, RoundingMode.HALF_UP));
    }

    /** "3 a 1" o "1 a 1,1": a favor contra en contra, con un decimal y sin ",0". */
    static String odds(BigInteger aFavor, BigInteger enContra) {
        if (enContra.signum() == 0) {
            return "sin odds: es seguro";
        }
        if (aFavor.signum() == 0) {
            return "sin odds: es imposible";
        }
        if (aFavor.compareTo(enContra) >= 0) {
            return decimal(new BigDecimal(aFavor).divide(new BigDecimal(enContra), 1, RoundingMode.HALF_UP)) + " a 1";
        }
        return "1 a " + decimal(new BigDecimal(enContra).divide(new BigDecimal(aFavor), 1, RoundingMode.HALF_UP));
    }

    private static String decimal(BigDecimal valor) {
        BigDecimal limpio = valor.stripTrailingZeros();
        if (limpio.scale() < 0) {
            limpio = limpio.setScale(0);
        }
        return limpio.toPlainString().replace('.', ',');
    }

    @Override
    public ResultadoBayes migrar(Json datosViejos, int desdeVersion) {
        throw new IllegalArgumentException(ID + " no tiene versiones anteriores a la " + VERSION_ESQUEMA
                + "; se pidió migrar desde la " + desdeVersion);
    }
}
