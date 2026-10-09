package pensamiento.tecnicas.f4;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import pensamiento.nucleo.Contexto;
import pensamiento.nucleo.Ejecutor;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Json;
import pensamiento.nucleo.Resultado;
import pensamiento.nucleo.Validacion;
import pensamiento.tecnicas.comun.Textos;

/**
 * T21 · CRAAP (Blakeslee 2004): actualidad, relevancia, autoridad, exactitud y propósito, de 0 a 5 cada uno, con los pesos
 * de la configuración y un umbral de aprobación. El puntaje sigue siendo de 0 a 25 con cualquier peso. No usa IA ni
 * produce afirmaciones: califica fuentes. Las reglas están en docs/ejemplos/T21.md; la ficha de fuente usa el mismo
 * cálculo ({@link #puntaje}).
 */
@Component
public class EjecutorCraap implements Ejecutor<EjecutorCraap.Config, EjecutorCraap.Entrada, ResultadoCraap> {

    public static final IdTecnica ID = IdTecnica.de("T21");
    public static final int VERSION_ESQUEMA = 1;
    public static final List<String> CRITERIOS = List.of("actualidad", "relevancia", "autoridad", "exactitud", "propósito");

    /** Configuración de T21, versión de esquema 1: un peso de 0 a 5 por criterio y el umbral de aprobación. */
    public record Config(int pesoActualidad, int pesoRelevancia, int pesoAutoridad, int pesoExactitud, int pesoProposito, int umbral) {

        public static Config porDefecto() {
            return new Config(1, 1, 1, 1, 1, 18);
        }

        public List<Integer> pesos() {
            return List.of(pesoActualidad, pesoRelevancia, pesoAutoridad, pesoExactitud, pesoProposito);
        }
    }

    /** Los cinco criterios de una fuente, de 0 a 5; nulo si falta. */
    public record FuenteCraap(String titulo, Integer actualidad, Integer relevancia, Integer autoridad, Integer exactitud, Integer proposito,
                              String nota) {

        public List<Integer> valores() {
            return java.util.Arrays.asList(actualidad, relevancia, autoridad, exactitud, proposito);
        }
    }

    public record Entrada(String uso, List<FuenteCraap> fuentes) {
        public Entrada {
            fuentes = fuentes == null ? List.of() : List.copyOf(fuentes);
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
    public Tipos<Config, Entrada, ResultadoCraap> tipos() {
        return new Tipos<>(Config.class, Entrada.class, ResultadoCraap.class);
    }

    @Override
    public Validacion validar(Config config, Entrada entrada) {
        List<Validacion.Error> errores = new ArrayList<>();
        if (config.pesos().stream().anyMatch(p -> p < 0 || p > 5)) {
            errores.add(new Validacion.Error("config.pesoActualidad", "Cada peso va de 0 a 5."));
        } else if (config.pesos().stream().mapToInt(Integer::intValue).sum() == 0) {
            errores.add(new Validacion.Error("config.pesoActualidad", "Al menos un criterio necesita peso mayor que 0."));
        }
        if (config.umbral() < 0 || config.umbral() > 25) {
            errores.add(new Validacion.Error("config.umbral", "El umbral va de 0 a 25."));
        }
        List<FuenteCraap> fuentes = conTitulo(entrada);
        if (fuentes.isEmpty() || fuentes.size() > 6) {
            errores.add(new Validacion.Error("fuentes", "Califica de una a seis fuentes."));
        }
        for (FuenteCraap f : fuentes) {
            if (f.valores().stream().anyMatch(v -> v == null || v < 0 || v > 5)) {
                errores.add(new Validacion.Error("fuentes", "Cada criterio de cada fuente va de 0 a 5."));
                break;
            }
        }
        return new Validacion(errores);
    }

    @Override
    public Resultado<ResultadoCraap> ejecutar(Config config, Entrada entrada, Contexto ctx) {
        Validacion validacion = validar(config, entrada);
        if (!validacion.esValida()) {
            throw new IllegalArgumentException("Entrada inválida para " + ID + ": " + validacion.errores());
        }
        List<FuenteCraap> fuentes = conTitulo(entrada);
        List<Integer> pesos = config.pesos();
        List<ResultadoCraap.FuenteEvaluada> evaluadas = new ArrayList<>();
        int mejor = -1;
        for (int i = 0; i < fuentes.size(); i++) {
            FuenteCraap f = fuentes.get(i);
            List<Integer> valores = f.valores();
            int puntaje = puntaje(valores, pesos);
            boolean aprobada = puntaje >= config.umbral();
            List<ResultadoCraap.Criterio> criterios = new ArrayList<>();
            int debil = -1;
            for (int c = 0; c < CRITERIOS.size(); c++) {
                criterios.add(new ResultadoCraap.Criterio(CRITERIOS.get(c), valores.get(c), pesos.get(c)));
                if (pesos.get(c) > 0 && (debil < 0 || valores.get(c) < valores.get(debil))) {
                    debil = c;
                }
            }
            evaluadas.add(new ResultadoCraap.FuenteEvaluada("F" + (i + 1), f.titulo().strip(), criterios, puntaje, aprobada,
                    puntaje + " de 25 · " + (aprobada ? "aprobada" : "no aprobada"),
                    "Lo más débil: " + CRITERIOS.get(debil) + " (" + valores.get(debil) + " de 5).", Textos.vacio(f.nota()) ? null : f.nota().strip()));
            if (mejor < 0 || puntaje > evaluadas.get(mejor).puntaje()) {
                mejor = i;
            }
        }
        String resumen;
        String codigoMejor = null;
        if (evaluadas.size() == 1) {
            ResultadoCraap.FuenteEvaluada f = evaluadas.getFirst();
            resumen = f.titulo() + ": " + f.puntaje() + " de 25 · " + (f.aprobada() ? "aprobada" : "no aprobada") + " (umbral " + config.umbral() + ").";
        } else {
            ResultadoCraap.FuenteEvaluada m = evaluadas.get(mejor);
            codigoMejor = m.codigo();
            int aprobadas = (int) evaluadas.stream().filter(ResultadoCraap.FuenteEvaluada::aprobada).count();
            resumen = evaluadas.size() + " fuentes · " + (aprobadas == 0 ? "ninguna aprobada" : Textos.contar(aprobadas, "aprobada", "aprobadas"))
                    + " con umbral " + config.umbral() + " · la mejor: " + m.titulo() + " (" + m.puntaje() + " de 25).";
        }
        ResultadoCraap valor = new ResultadoCraap(Textos.vacio(entrada.uso()) ? null : entrada.uso().strip(), evaluadas, config.umbral(), codigoMejor,
                resumen);
        return new Resultado<>(VERSION_ESQUEMA, valor, List.of(), List.of(), resumen);
    }

    /**
     * 5 × Σ(criterio × peso) / Σ(pesos), redondeado al entero con la mitad hacia arriba: con los pesos en 1 es la suma de
     * los cinco criterios. Vacío si los pesos suman 0.
     */
    public static int puntaje(List<Integer> valores, List<Integer> pesos) {
        int suma = 0;
        int sumaPesos = 0;
        for (int c = 0; c < valores.size(); c++) {
            suma += valores.get(c) * pesos.get(c);
            sumaPesos += pesos.get(c);
        }
        if (sumaPesos == 0) {
            throw new IllegalArgumentException("Los pesos de CRAAP suman 0");
        }
        // Mitad hacia arriba sin decimales: floor(5·suma/Σpesos + 1/2) = floor((10·suma + Σpesos) / (2·Σpesos)).
        return Math.floorDiv(10 * suma + sumaPesos, 2 * sumaPesos);
    }

    /** El puntaje si están los cinco criterios; vacío si falta alguno. */
    public static Optional<Integer> puntajeSiCompleto(List<Integer> valores, List<Integer> pesos) {
        if (valores.stream().anyMatch(v -> v == null)) {
            return Optional.empty();
        }
        return Optional.of(puntaje(valores, pesos));
    }

    private static List<FuenteCraap> conTitulo(Entrada entrada) {
        return entrada.fuentes().stream().filter(f -> f != null && !Textos.vacio(f.titulo())).toList();
    }

    @Override
    public ResultadoCraap migrar(Json datosViejos, int desdeVersion) {
        throw new IllegalArgumentException(ID + " no tiene versiones anteriores a la " + VERSION_ESQUEMA
                + "; se pidió migrar desde la " + desdeVersion);
    }
}
