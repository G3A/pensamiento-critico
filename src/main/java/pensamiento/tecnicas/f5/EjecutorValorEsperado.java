package pensamiento.tecnicas.f5;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

import pensamiento.nucleo.AfirmacionConRol;
import pensamiento.nucleo.Contexto;
import pensamiento.nucleo.Ejecutor;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Json;
import pensamiento.nucleo.OrigenAfirmacion;
import pensamiento.nucleo.Resultado;
import pensamiento.nucleo.RolAfirmacion;
import pensamiento.nucleo.SentidoAfirmacion;
import pensamiento.nucleo.TipoAfirmacion;
import pensamiento.nucleo.Validacion;
import pensamiento.tecnicas.comun.Textos;

/**
 * T27 · Valor esperado (Bernoulli 1738). Suma probabilidad por impacto de los escenarios de cada opción y las ordena; con
 * aversión a pérdidas, los impactos negativos cuentan el doble. No usa IA. Las reglas están en docs/ejemplos/T27.md.
 */
@Component
public class EjecutorValorEsperado implements Ejecutor<EjecutorValorEsperado.Config, EjecutorValorEsperado.Entrada, ResultadoValorEsperado> {

    public static final IdTecnica ID = IdTecnica.de("T27");
    public static final int VERSION_ESQUEMA = 1;
    static final int TOPE_OPCIONES = 6;
    /** λ de la aversión a pérdidas (Kahneman y Tversky 1979). */
    static final int LAMBDA = 2;

    /** Configuración de T27, versión de esquema 1. */
    public record Config(String unidad, boolean aversionPerdidas) {
    }

    /** Tres escenarios fijos por opción, con probabilidad (0 a 100, suman 100) e impacto entero en la unidad. */
    public record Opcion(String texto, Integer pBueno, Integer iBueno, Integer pMedio, Integer iMedio, Integer pMalo, Integer iMalo) {
    }

    public record Entrada(String pregunta, List<Opcion> opciones) {
        public Entrada {
            opciones = opciones == null ? List.of() : List.copyOf(opciones);
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
    public Tipos<Config, Entrada, ResultadoValorEsperado> tipos() {
        return new Tipos<>(Config.class, Entrada.class, ResultadoValorEsperado.class);
    }

    @Override
    public Validacion validar(Config config, Entrada entrada) {
        List<Validacion.Error> errores = new ArrayList<>();
        if (Textos.vacio(config.unidad())) {
            errores.add(new Validacion.Error("config.unidad", "Escribe la unidad del impacto: meses de gastos, millones de pesos…"));
        }
        if (Textos.vacio(entrada.pregunta())) {
            errores.add(new Validacion.Error("pregunta", "Escribe la decisión."));
        }
        if (entrada.opciones().size() < 2) {
            errores.add(new Validacion.Error("opciones", "Escribe al menos dos opciones para comparar."));
        } else if (entrada.opciones().size() > TOPE_OPCIONES) {
            errores.add(new Validacion.Error("opciones", "Caben como máximo " + TOPE_OPCIONES + " opciones."));
        }
        for (int i = 0; i < entrada.opciones().size(); i++) {
            Opcion o = entrada.opciones().get(i);
            String campo = "opciones[" + i + "]";
            if (Textos.vacio(o.texto())) {
                errores.add(new Validacion.Error(campo + ".texto", "Escribe la opción O" + (i + 1) + "."));
            }
            int suma = 0;
            for (Integer p : List.of(nulo(o.pBueno()), nulo(o.pMedio()), nulo(o.pMalo()))) {
                if (p < 0 || p > 100) {
                    errores.add(new Validacion.Error(campo + ".pBueno", "Las probabilidades de O" + (i + 1) + " van de 0 a 100."));
                }
                suma += p;
            }
            if (suma != 100) {
                errores.add(new Validacion.Error(campo + ".pMalo", "Las probabilidades de O" + (i + 1) + " suman " + suma + ": tienen que sumar 100."));
            }
        }
        return new Validacion(errores);
    }

    private static int nulo(Integer v) {
        return v == null ? 0 : v;
    }

    @Override
    public Resultado<ResultadoValorEsperado> ejecutar(Config config, Entrada entrada, Contexto ctx) {
        Validacion validacion = validar(config, entrada);
        if (!validacion.esValida()) {
            throw new IllegalArgumentException("Entrada inválida para " + ID + ": " + validacion.errores());
        }
        record Calculada(int indice, String texto, long centesimas, long sinAversion, Integer peorCaso) {
        }
        List<Calculada> calculadas = new ArrayList<>();
        for (int i = 0; i < entrada.opciones().size(); i++) {
            Opcion o = entrada.opciones().get(i);
            int[][] escenarios = {{nulo(o.pBueno()), nulo(o.iBueno())}, {nulo(o.pMedio()), nulo(o.iMedio())}, {nulo(o.pMalo()), nulo(o.iMalo())}};
            long con = 0;
            long sin = 0;
            Integer peor = null;
            for (int[] e : escenarios) {
                sin += (long) e[0] * e[1];
                con += (long) e[0] * (config.aversionPerdidas() && e[1] < 0 ? LAMBDA * e[1] : e[1]);
                if (e[0] > 0 && (peor == null || e[1] < peor)) {
                    peor = e[1];
                }
            }
            calculadas.add(new Calculada(i, o.texto().strip(), con, sin, peor));
        }
        List<Calculada> orden = calculadas.stream().sorted(Comparator.comparingLong(Calculada::centesimas).reversed()
                .thenComparingInt(Calculada::indice)).toList();
        List<ResultadoValorEsperado.Fila> ranking = new ArrayList<>();
        for (Calculada c : orden) {
            long iguales = calculadas.stream().filter(x -> x.centesimas() == c.centesimas()).count();
            int puesto = 1 + (int) calculadas.stream().filter(x -> x.centesimas() > c.centesimas()).count();
            ranking.add(new ResultadoValorEsperado.Fila(puesto, c.texto(), valor(c.centesimas()), c.centesimas(), c.peorCaso(), iguales > 1));
        }
        String unidad = config.unidad().strip();
        List<String> avisos = new ArrayList<>();
        if (config.aversionPerdidas()) {
            Calculada primeraSin = calculadas.stream().sorted(Comparator.comparingLong(Calculada::sinAversion).reversed()
                    .thenComparingInt(Calculada::indice)).findFirst().orElseThrow();
            if (primeraSin.indice() != orden.getFirst().indice()) {
                avisos.add("Con la aversión a pérdidas el orden cambia: sin ella, primero iría " + primeraSin.texto() + " ("
                        + valor(primeraSin.sinAversion()) + ").");
            }
        }
        List<ResultadoValorEsperado.Fila> primeros = ranking.stream().filter(f -> f.puesto() == 1).toList();
        String cola = " " + unidad + (config.aversionPerdidas() ? ", con aversión a pérdidas" : "") + ") · "
                + Textos.contar(ranking.size(), "opción", "opciones") + ".";
        String resumen = primeros.size() > 1
                ? "Empate en el 1º lugar: " + Textos.enumerar(primeros.stream().map(ResultadoValorEsperado.Fila::opcion).toList()) + " ("
                + primeros.getFirst().valor() + cola
                : "1º " + primeros.getFirst().opcion() + " (" + primeros.getFirst().valor() + cola;
        List<AfirmacionConRol> afirmaciones = new ArrayList<>();
        for (Calculada c : calculadas) {
            afirmaciones.add(new AfirmacionConRol(ctx.nuevoId().get(), c.texto(), TipoAfirmacion.JUICIO_DE_VALOR, RolAfirmacion.OPCION,
                    SentidoAfirmacion.PRODUCIDA, OrigenAfirmacion.USUARIO));
        }
        ResultadoValorEsperado valor = new ResultadoValorEsperado(entrada.pregunta().strip(), unidad, config.aversionPerdidas(), ranking, avisos, resumen);
        return new Resultado<>(VERSION_ESQUEMA, valor, afirmaciones, List.of(), resumen);
    }

    /** Centésimas (probabilidad × impacto) a un decimal con signo: 720 → "+7,2", −50 → "−0,5", 0 → "0,0". */
    static String valor(long centesimas) {
        return Textos.decimal(BigDecimal.valueOf(centesimas).movePointLeft(2).setScale(1, java.math.RoundingMode.HALF_UP), true);
    }

    @Override
    public ResultadoValorEsperado migrar(Json datosViejos, int desdeVersion) {
        throw new IllegalArgumentException(ID + " no tiene versiones anteriores a la " + VERSION_ESQUEMA
                + "; se pidió migrar desde la " + desdeVersion);
    }
}
