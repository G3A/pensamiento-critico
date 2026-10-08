package pensamiento.tecnicas.f3;

import java.math.BigDecimal;
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
 * T18 · Correlación, causalidad y tasas base (Hill 1965; Kahneman y Tversky 1973). Cuenta los criterios de Hill que la
 * persona marca, avisa si falta temporalidad o si son pocos, y con tasa base, sensibilidad y especificidad calcula la
 * probabilidad real con frecuencias naturales. No usa IA. Las reglas están en docs/ejemplos/T18.md.
 */
@Component
public class EjecutorTasasBase implements Ejecutor<EjecutorTasasBase.Config, EjecutorTasasBase.Entrada, ResultadoTasasBase> {

    public static final IdTecnica ID = IdTecnica.de("T18");
    public static final int VERSION_ESQUEMA = 1;

    public static final String FALTA_TASA_BASE = "Falta la tasa base: ¿de cada cuántos pasa? No es lo mismo pasar de 2 a 3 que de 20 a 30.";

    /** Los nueve criterios de Hill, en orden, en llano. */
    public enum Criterio {
        FUERZA("Fuerza", "la relación es grande, no una diferencia mínima"),
        CONSISTENCIA("Consistencia", "se repite en otros lugares o momentos"),
        ESPECIFICIDAD("Especificidad", "la causa produce este efecto y no muchos otros"),
        TEMPORALIDAD("Temporalidad", "la causa vino antes que el efecto"),
        GRADIENTE("Gradiente", "más causa, más efecto"),
        PLAUSIBILIDAD("Plausibilidad", "hay un mecanismo que lo explique"),
        COHERENCIA("Coherencia", "no contradice lo que ya se sabe"),
        EXPERIMENTO("Experimento", "al quitar la causa, baja el efecto"),
        ANALOGIA("Analogía", "pasa algo parecido en casos parecidos");

        private final String nombre;
        private final String llano;

        Criterio(String nombre, String llano) {
            this.nombre = nombre;
            this.llano = llano;
        }

        public String nombre() {
            return nombre;
        }

        public String llano() {
            return llano;
        }

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    public enum Formato {
        FRECUENCIAS, PORCENTAJE;

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    /** Configuración de T18, versión de esquema 1. */
    public record Config(List<Criterio> criterios, boolean exigirTasaBase, Formato formato) {
        public Config {
            criterios = criterios == null ? List.of() : List.copyOf(criterios);
        }
    }

    /**
     * @param casos         la tasa base: casos de cada "deCada" personas
     * @param sensibilidad  porcentaje de los que tienen la condición que el examen detecta
     * @param especificidad porcentaje de los que no la tienen que el examen deja en negativo
     */
    public record Entrada(String afirmacion, boolean esRiesgo, Integer casos, Integer deCada, Integer sensibilidad, Integer especificidad,
                          List<Criterio> cumplidos) {
        public Entrada {
            cumplidos = cumplidos == null ? List.of() : List.copyOf(cumplidos);
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
    public Tipos<Config, Entrada, ResultadoTasasBase> tipos() {
        return new Tipos<>(Config.class, Entrada.class, ResultadoTasasBase.class);
    }

    @Override
    public Validacion validar(Config config, Entrada entrada) {
        List<Validacion.Error> errores = new ArrayList<>();
        if (config.criterios().isEmpty()) {
            errores.add(new Validacion.Error("config.criterios", "Activa al menos un criterio de Hill."));
        }
        if (config.formato() == null) {
            errores.add(new Validacion.Error("config.formato", "Elige el formato."));
        }
        if (Textos.vacio(entrada.afirmacion())) {
            errores.add(new Validacion.Error("afirmacion", "Escribe la afirmación que quieres revisar."));
        }
        if (entrada.deCada() != null && entrada.deCada() < 1) {
            errores.add(new Validacion.Error("deCada", "«De cada» tiene que ser 1 o más."));
        }
        if (entrada.casos() != null && entrada.deCada() != null && (entrada.casos() < 0 || entrada.casos() > entrada.deCada())) {
            errores.add(new Validacion.Error("casos", "Los casos van de 0 al número de «de cada»."));
        }
        if (entrada.cumplidos().stream().anyMatch(c -> !config.criterios().contains(c))) {
            errores.add(new Validacion.Error("cumplidos", "Marcaste un criterio que no está activo."));
        }
        return new Validacion(errores);
    }

    @Override
    public Resultado<ResultadoTasasBase> ejecutar(Config config, Entrada entrada, Contexto ctx) {
        Validacion validacion = validar(config, entrada);
        if (!validacion.esValida()) {
            throw new IllegalArgumentException("Entrada inválida para " + ID + ": " + validacion.errores());
        }
        String afirmacion = entrada.afirmacion().strip();
        List<ResultadoTasasBase.CriterioEvaluado> criterios = new ArrayList<>();
        for (Criterio c : Criterio.values()) {
            if (config.criterios().contains(c)) {
                criterios.add(new ResultadoTasasBase.CriterioEvaluado(c.toString(), c.nombre(), c.llano(), entrada.cumplidos().contains(c)));
            }
        }
        int cumplidos = (int) criterios.stream().filter(ResultadoTasasBase.CriterioEvaluado::cumplido).count();
        int activos = criterios.size();
        List<String> avisos = new ArrayList<>();
        if (config.criterios().contains(Criterio.TEMPORALIDAD) && !entrada.cumplidos().contains(Criterio.TEMPORALIDAD)) {
            avisos.add("Correlación sin temporalidad: no se sabe qué vino primero.");
        }
        if (2 * cumplidos < activos) {
            avisos.add("Con " + cumplidos + " de " + activos + " criterios, trátalo como correlación, no como causa.");
        }

        boolean hayTasa = entrada.casos() != null && entrada.deCada() != null;
        ResultadoTasasBase.Calculo calculo = hayTasa && entrada.sensibilidad() != null && entrada.especificidad() != null
                ? calcular(entrada.casos(), entrada.deCada(), entrada.sensibilidad(), entrada.especificidad()) : null;
        boolean faltaTasaBase = entrada.esRiesgo() && config.exigirTasaBase() && !hayTasa;
        String frase = null;
        if (calculo != null) {
            frase = config.formato() == Formato.FRECUENCIAS
                    ? "De cada " + calculo.positivos() + " positivos, " + calculo.detectados() + (calculo.detectados() == 1 ? " tiene" : " tienen")
                    + " la condición: " + calculo.probabilidad() + "%, no " + calculo.sensibilidad() + "%."
                    : "Probabilidad real si da positivo: " + calculo.probabilidad() + "%.";
        } else if (faltaTasaBase) {
            frase = FALTA_TASA_BASE;
        }
        List<Pendiente> pendientes = new ArrayList<>();
        if (faltaTasaBase) {
            pendientes.add(new Pendiente(TipoPendiente.VERIFICACION, Optional.empty(), Optional.empty(), "Buscar la tasa base de: " + afirmacion));
        }
        String primera = calculo != null ? "Probabilidad real " + calculo.probabilidad() + "%, no " + calculo.sensibilidad() + "%"
                : faltaTasaBase ? "Falta la tasa base" : "sin cálculo con tasa base";
        String resumen = Textos.mayusculaInicial(primera) + " · " + cumplidos + " de " + activos + " criterios de Hill.";
        List<AfirmacionConRol> afirmaciones = List.of(new AfirmacionConRol(ctx.nuevoId().get(), afirmacion,
                entrada.esRiesgo() ? TipoAfirmacion.DATO_ESTADISTICO : TipoAfirmacion.CAUSAL, RolAfirmacion.HIPOTESIS, SentidoAfirmacion.PRODUCIDA,
                OrigenAfirmacion.USUARIO));
        ResultadoTasasBase valor = new ResultadoTasasBase(afirmacion, calculo, frase, faltaTasaBase, criterios, cumplidos, avisos,
                config.formato().toString(), resumen);
        return new Resultado<>(VERSION_ESQUEMA, valor, afirmaciones, pendientes, resumen);
    }

    /** Frecuencias naturales (docs/ejemplos/T18.md, regla 4), con redondeo a entero, la mitad hacia arriba. */
    static ResultadoTasasBase.Calculo calcular(int casos, int deCada, int sensibilidad, int especificidad) {
        int enfermos = casos;
        int sanos = deCada - casos;
        int detectados = redondear((long) enfermos * sensibilidad, 100);
        int positivosFalsos = redondear((long) sanos * (100 - especificidad), 100);
        int positivos = detectados + positivosFalsos;
        int probabilidad = positivos == 0 ? 0 : redondear((long) detectados * 100, positivos);
        return new ResultadoTasasBase.Calculo(deCada, enfermos, sanos, detectados, enfermos - detectados, positivosFalsos, sanos - positivosFalsos,
                positivos, probabilidad, sensibilidad);
    }

    private static int redondear(long numerador, long denominador) {
        return BigDecimal.valueOf(numerador).divide(BigDecimal.valueOf(denominador), 0, RoundingMode.HALF_UP).intValueExact();
    }

    @Override
    public ResultadoTasasBase migrar(Json datosViejos, int desdeVersion) {
        throw new IllegalArgumentException(ID + " no tiene versiones anteriores a la " + VERSION_ESQUEMA
                + "; se pidió migrar desde la " + desdeVersion);
    }
}
