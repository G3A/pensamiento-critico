package pensamiento.tecnicas.f8;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import pensamiento.nucleo.Contexto;
import pensamiento.nucleo.Ejecutor;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Json;
import pensamiento.nucleo.Resultado;
import pensamiento.nucleo.Validacion;
import pensamiento.tecnicas.comun.Textos;

/**
 * T45 · Diario de razonamiento (Dewey 1933; Schön 1983). Reúne las ejecuciones de todas las técnicas en una línea de tiempo
 * por semanas, con el resumen de cada semana y cuántas hubo de cada familia, para notar patrones en cómo razonas. No usa IA.
 * Las reglas están en docs/ejemplos/T45.md.
 */
@Component
public class EjecutorDiarioRazonamiento implements Ejecutor<EjecutorDiarioRazonamiento.Config, EjecutorDiarioRazonamiento.Entrada,
        ResultadoDiarioRazonamiento> {

    public static final IdTecnica ID = IdTecnica.de("T45");
    public static final int VERSION_ESQUEMA = 1;
    public static final int TOPE_REGISTROS = 60;
    static final List<String> FAMILIAS = List.of("F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8");
    private static final Pattern CODIGO = Pattern.compile("^T(0[1-9]|[1-4][0-9])$");

    /** Configuración de T45, versión de esquema 1. */
    public record Config(List<String> familias, boolean resumenSemanal, int semanas) {
        public Config {
            familias = familias == null ? List.of() : List.copyOf(familias);
        }
    }

    /** @param fecha AAAA-MM-DD; @param tecnica T01 a T49; @param expediente opcional; @param cambios de 0 a 9 */
    public record Registro(String fecha, String tecnica, String resumen, String expediente, Integer cambios) {
    }

    public record Entrada(List<Registro> registros) {
        public Entrada {
            registros = registros == null ? List.of() : List.copyOf(registros);
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
    public Tipos<Config, Entrada, ResultadoDiarioRazonamiento> tipos() {
        return new Tipos<>(Config.class, Entrada.class, ResultadoDiarioRazonamiento.class);
    }

    @Override
    public Validacion validar(Config config, Entrada entrada) {
        List<Validacion.Error> errores = new ArrayList<>();
        if (config.familias().isEmpty() || !FAMILIAS.containsAll(config.familias())) {
            errores.add(new Validacion.Error("config.familias", "Elige al menos una familia, de F1 a F8."));
        }
        if (config.semanas() < 1 || config.semanas() > 12) {
            errores.add(new Validacion.Error("config.semanas", "El diario mira de 1 a 12 semanas hacia atrás."));
        }
        if (entrada.registros().isEmpty()) {
            errores.add(new Validacion.Error("registros", "Agrega al menos un registro: una ejecución con su fecha."));
        } else if (entrada.registros().size() > TOPE_REGISTROS) {
            errores.add(new Validacion.Error("registros", "Caben como máximo " + TOPE_REGISTROS + " registros."));
        }
        for (int i = 0; i < entrada.registros().size(); i++) {
            Registro r = entrada.registros().get(i);
            String campo = "registros[" + i + "]";
            if (Textos.vacio(r.fecha())) {
                errores.add(new Validacion.Error(campo + ".fecha", "Escribe la fecha del registro " + (i + 1) + "."));
            } else {
                try {
                    LocalDate.parse(r.fecha().strip());
                } catch (DateTimeParseException e) {
                    errores.add(new Validacion.Error(campo + ".fecha", "La fecha va como AAAA-MM-DD."));
                }
            }
            if (r.tecnica() == null || !CODIGO.matcher(r.tecnica().strip()).matches()) {
                errores.add(new Validacion.Error(campo + ".tecnica", "Escribe el código de la técnica, de T01 a T49."));
            }
            if (Textos.vacio(r.resumen())) {
                errores.add(new Validacion.Error(campo + ".resumen", "Escribe el resumen del registro " + (i + 1) + "."));
            }
            if (r.cambios() != null && (r.cambios() < 0 || r.cambios() > 9)) {
                errores.add(new Validacion.Error(campo + ".cambios", "Los cambios de opinión van de 0 a 9."));
            }
        }
        return new Validacion(errores);
    }

    @Override
    public Resultado<ResultadoDiarioRazonamiento> ejecutar(Config config, Entrada entrada, Contexto ctx) {
        Validacion validacion = validar(config, entrada);
        if (!validacion.esValida()) {
            throw new IllegalArgumentException("Entrada inválida para " + ID + ": " + validacion.errores());
        }
        ResultadoDiarioRazonamiento valor = calcular(config, entrada.registros(), ctx.reloj().hoy());
        return new Resultado<>(VERSION_ESQUEMA, valor, List.of(), List.of(), valor.resumen());
    }

    /** La regla de T45, sin tope de registros: también la usa el diario de P20 con las ejecuciones guardadas. */
    public static ResultadoDiarioRazonamiento calcular(Config config, List<Registro> registros, LocalDate hoy) {
        LocalDate lunesDeHoy = hoy.with(DayOfWeek.MONDAY);
        LocalDate inicio = lunesDeHoy.minusWeeks(config.semanas() - 1L);
        int antes = 0;
        int futuros = 0;
        TreeSet<String> familiasFuera = new TreeSet<>();
        int fueraPorFamilia = 0;
        // Cada semana, por su lunes, con sus registros en el orden en que llegaron.
        TreeMap<LocalDate, List<Registro>> porSemana = new TreeMap<>(Comparator.reverseOrder());
        Map<String, Integer> porFamilia = new LinkedHashMap<>();
        for (Registro r : registros) {
            LocalDate fecha = LocalDate.parse(r.fecha().strip());
            String familia = IdTecnica.de(r.tecnica().strip()).familia();
            if (fecha.isAfter(hoy)) {
                futuros++;
            } else if (fecha.isBefore(inicio)) {
                antes++;
            } else if (!config.familias().contains(familia)) {
                fueraPorFamilia++;
                familiasFuera.add(familia);
            } else {
                porSemana.computeIfAbsent(fecha.with(DayOfWeek.MONDAY), k -> new ArrayList<>()).add(r);
                porFamilia.merge(familia, 1, Integer::sum);
            }
        }
        List<ResultadoDiarioRazonamiento.Semana> semanas = new ArrayList<>();
        int total = 0;
        int cambiosTotales = 0;
        for (Map.Entry<LocalDate, List<Registro>> e : porSemana.entrySet()) {
            List<Registro> dela = e.getValue();
            List<ResultadoDiarioRazonamiento.Registro> ordenados = new ArrayList<>();
            // Por fecha, de la más reciente; a igual fecha, primero el escrito después.
            for (int i = dela.size() - 1; i >= 0; i--) {
                Registro r = dela.get(i);
                LocalDate fecha = LocalDate.parse(r.fecha().strip());
                ordenados.add(new ResultadoDiarioRazonamiento.Registro(fecha.toString(), Textos.fecha(fecha), r.tecnica().strip(), r.resumen().strip(),
                        Textos.vacio(r.expediente()) ? null : r.expediente().strip(), cambios(r)));
            }
            ordenados.sort(Comparator.comparing(ResultadoDiarioRazonamiento.Registro::fecha).reversed());
            long expedientes = ordenados.stream().map(ResultadoDiarioRazonamiento.Registro::expediente).filter(Objects::nonNull).distinct().count();
            int cambios = ordenados.stream().mapToInt(ResultadoDiarioRazonamiento.Registro::cambios).sum();
            String resumen = config.resumenSemanal() ? Textos.contar(ordenados.size(), "ejecución", "ejecuciones") + ", "
                    + Textos.contar((int) expedientes, "expediente", "expedientes") + ", " + cambiosDeOpinion(cambios) + "." : null;
            semanas.add(new ResultadoDiarioRazonamiento.Semana(e.getKey().toString(), titulo(e.getKey()), resumen, ordenados));
            total += ordenados.size();
            cambiosTotales += cambios;
        }
        List<ResultadoDiarioRazonamiento.PorFamilia> familias = porFamilia.entrySet().stream()
                .map(e -> new ResultadoDiarioRazonamiento.PorFamilia(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingInt(ResultadoDiarioRazonamiento.PorFamilia::registros).reversed()
                        .thenComparing(ResultadoDiarioRazonamiento.PorFamilia::familia))
                .toList();
        List<String> sinUsar = config.familias().stream().filter(f -> !porFamilia.containsKey(f)).sorted().toList();
        List<String> avisos = new ArrayList<>();
        if (antes > 0) {
            avisos.add("Fuera de la ventana: " + Textos.contar(antes, "registro", "registros") + " de antes del " + Textos.fecha(inicio) + ".");
        }
        if (fueraPorFamilia > 0) {
            avisos.add("Fuera por familia: " + Textos.contar(fueraPorFamilia, "registro", "registros") + " de "
                    + Textos.enumerar(List.copyOf(familiasFuera)) + ".");
        }
        if (futuros > 0) {
            avisos.add("Con fecha posterior a hoy: " + Textos.contar(futuros, "registro que no cuenta", "registros que no cuentan") + ".");
        }
        String resumen = Textos.contar(total, "ejecución", "ejecuciones") + " en " + Textos.contar(semanas.size(), "semana", "semanas") + " · "
                + cambiosDeOpinion(cambiosTotales) + ".";
        return new ResultadoDiarioRazonamiento(semanas, familias, sinUsar, avisos, resumen);
    }

    private static int cambios(Registro r) {
        return r.cambios() == null ? 0 : r.cambios();
    }

    private static String cambiosDeOpinion(int n) {
        return Textos.contar(n, "cambio de opinión", "cambios de opinión");
    }

    /** "Semana del 5 al 11 de octubre de 2026", con los meses y los años que hagan falta. */
    static String titulo(LocalDate lunes) {
        LocalDate domingo = lunes.plusDays(6);
        if (lunes.getYear() != domingo.getYear()) {
            return "Semana del " + Textos.fecha(lunes) + " al " + Textos.fecha(domingo);
        }
        if (lunes.getMonth() != domingo.getMonth()) {
            return "Semana del " + Textos.fecha(lunes).replace(" de " + lunes.getYear(), "") + " al " + Textos.fecha(domingo);
        }
        return "Semana del " + lunes.getDayOfMonth() + " al " + Textos.fecha(domingo);
    }

    @Override
    public ResultadoDiarioRazonamiento migrar(Json datosViejos, int desdeVersion) {
        throw new IllegalArgumentException(ID + " no tiene versiones anteriores a la " + VERSION_ESQUEMA
                + "; se pidió migrar desde la " + desdeVersion);
    }
}
