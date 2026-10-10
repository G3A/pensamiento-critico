package pensamiento.tecnicas.f8;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import pensamiento.nucleo.Contexto;
import pensamiento.nucleo.Ejecutor;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Json;
import pensamiento.nucleo.Resultado;
import pensamiento.nucleo.Validacion;
import pensamiento.tecnicas.comun.Textos;

/**
 * T49 · Repetición espaciada (Ebbinghaus 1885; Wozniak 1987). Con los repasos hechos, programa el próximo de cada concepto
 * con SM-2 y muestra el calendario de los próximos 7 días, la racha y la facilidad media. No es una técnica de pensamiento
 * crítico: fija el vocabulario. No usa IA. Las reglas están en docs/ejemplos/T49.md.
 */
@Component
public class EjecutorRepeticion implements Ejecutor<EjecutorRepeticion.Config, EjecutorRepeticion.Entrada, ResultadoRepeticion> {

    public static final IdTecnica ID = IdTecnica.de("T49");
    public static final int VERSION_ESQUEMA = 1;
    public static final int TOPE_REPASOS = 60;
    static final List<String> FACILIDADES = List.of("1.3", "2.0", "2.5", "3.0");
    private static final String[] DIAS = {"lun", "mar", "mié", "jue", "vie", "sáb", "dom"};

    /** Configuración de T49, versión de esquema 1. @param facilidadInicial "1.3", "2.0", "2.5" o "3.0" */
    public record Config(int retosPorDia, String facilidadInicial) {
        public BigDecimal facilidad() {
            return new BigDecimal(facilidadInicial);
        }
    }

    /** @param fecha AAAA-MM-DD */
    public record Repaso(TemaDojo tema, String concepto, String fecha, EjecutorBloom.ResultadoIntento resultado) {
    }

    public record Entrada(List<Repaso> repasos) {
        public Entrada {
            repasos = repasos == null ? List.of() : List.copyOf(repasos);
        }
    }

    /** Un repaso ya leído, para la regla: el Dojo los arma con sus intentos. */
    public record Hecho(TemaDojo tema, String concepto, LocalDate fecha, boolean acierto) {
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
    public Tipos<Config, Entrada, ResultadoRepeticion> tipos() {
        return new Tipos<>(Config.class, Entrada.class, ResultadoRepeticion.class);
    }

    @Override
    public Validacion validar(Config config, Entrada entrada) {
        List<Validacion.Error> errores = new ArrayList<>();
        if (config.retosPorDia() < 1 || config.retosPorDia() > 50) {
            errores.add(new Validacion.Error("config.retosPorDia", "Los retos por día van de 1 a 50."));
        }
        if (!FACILIDADES.contains(config.facilidadInicial())) {
            errores.add(new Validacion.Error("config.facilidadInicial", "Elige la facilidad inicial: 1,3, 2,0, 2,5 o 3,0."));
        }
        if (entrada.repasos().size() > TOPE_REPASOS) {
            errores.add(new Validacion.Error("repasos", "Caben como máximo " + TOPE_REPASOS + " repasos."));
        }
        for (int i = 0; i < entrada.repasos().size(); i++) {
            Repaso r = entrada.repasos().get(i);
            String campo = "repasos[" + i + "]";
            if (r.tema() == null) {
                errores.add(new Validacion.Error(campo + ".tema", "Elige el tema del repaso " + (i + 1) + "."));
            }
            if (Textos.vacio(r.concepto())) {
                errores.add(new Validacion.Error(campo + ".concepto", "Escribe el concepto del repaso " + (i + 1) + "."));
            }
            if (Textos.vacio(r.fecha())) {
                errores.add(new Validacion.Error(campo + ".fecha", "Escribe la fecha del repaso " + (i + 1) + "."));
            } else {
                try {
                    LocalDate.parse(r.fecha().strip());
                } catch (DateTimeParseException e) {
                    errores.add(new Validacion.Error(campo + ".fecha", "La fecha va como AAAA-MM-DD."));
                }
            }
            if (r.resultado() == null) {
                errores.add(new Validacion.Error(campo + ".resultado", "Marca si el repaso " + (i + 1) + " fue acierto o error."));
            }
        }
        return new Validacion(errores);
    }

    @Override
    public Resultado<ResultadoRepeticion> ejecutar(Config config, Entrada entrada, Contexto ctx) {
        Validacion validacion = validar(config, entrada);
        if (!validacion.esValida()) {
            throw new IllegalArgumentException("Entrada inválida para " + ID + ": " + validacion.errores());
        }
        List<Hecho> hechos = entrada.repasos().stream()
                .map(r -> new Hecho(r.tema(), r.concepto().strip(), LocalDate.parse(r.fecha().strip()), r.resultado() == EjecutorBloom.ResultadoIntento.ACIERTO))
                .toList();
        ResultadoRepeticion valor = calcular(config, hechos, ctx.reloj().hoy());
        return new Resultado<>(VERSION_ESQUEMA, valor, List.of(), List.of(), valor.resumen());
    }

    /** Un concepto con su estado de SM-2. */
    public record ConceptoEnCurso(TemaDojo tema, String concepto, Sm2.Estado estado) {
    }

    /**
     * SM-2 de cada concepto con sus repasos ordenados por fecha (los del mismo día, en el orden en que llegan), en el orden en
     * que aparece cada concepto. La usan este ejecutor y el Dojo.
     */
    public static List<ConceptoEnCurso> estados(BigDecimal facilidad, List<Hecho> hechos) {
        List<Hecho> ordenados = new ArrayList<>(hechos);
        ordenados.sort(Comparator.comparing(Hecho::fecha));
        Map<String, ConceptoEnCurso> porClave = new LinkedHashMap<>();
        for (Hecho h : ordenados) {
            String clave = h.tema() + "|" + EjecutorCambiosOpinion.clave(h.concepto());
            ConceptoEnCurso c = porClave.getOrDefault(clave, new ConceptoEnCurso(h.tema(), h.concepto(), Sm2.inicial(facilidad)));
            porClave.put(clave, new ConceptoEnCurso(c.tema(), c.concepto(), Sm2.responder(c.estado(), h.acierto(), h.fecha())));
        }
        return List.copyOf(porClave.values());
    }

    /** Días seguidos con al menos un repaso, hasta hoy si hoy hubo alguno o hasta ayer si no. */
    public static int racha(Set<LocalDate> dias, LocalDate hoy) {
        LocalDate d = dias.contains(hoy) ? hoy : hoy.minusDays(1);
        int racha = 0;
        while (dias.contains(d)) {
            racha++;
            d = d.minusDays(1);
        }
        return racha;
    }

    /** "hoy", "mañana", "en 5 días" o "atrasado 3 días". */
    public static String cuando(LocalDate proximo, LocalDate hoy) {
        long dias = ChronoUnit.DAYS.between(hoy, proximo);
        if (dias < 0) {
            return "atrasado " + Textos.contar((int) -dias, "día", "días");
        }
        if (dias == 0) {
            return "hoy";
        }
        return dias == 1 ? "mañana" : "en " + dias + " días";
    }

    /** La regla de T49, sin tope de repasos: también la usa el calendario del Dojo con los intentos guardados. */
    public static ResultadoRepeticion calcular(Config config, List<Hecho> hechos, LocalDate hoy) {
        List<ConceptoEnCurso> enCurso = estados(config.facilidad(), hechos);
        List<ResultadoRepeticion.Dia> calendario = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate d = hoy.plusDays(i);
            boolean esHoy = i == 0;
            int n = (int) enCurso.stream().filter(c -> esHoy ? !c.estado().proximo().isAfter(d) : c.estado().proximo().equals(d)).count();
            calendario.add(new ResultadoRepeticion.Dia(d.toString(), DIAS[d.getDayOfWeek().getValue() - 1] + " " + d.getDayOfMonth(), n));
        }
        int deHoy = calendario.getFirst().repasos();
        Map<TemaDojo, Integer> porTema = new EnumMap<>(TemaDojo.class);
        enCurso.stream().filter(c -> !c.estado().proximo().isAfter(hoy)).forEach(c -> porTema.merge(c.tema(), 1, Integer::sum));
        List<ResultadoRepeticion.Concepto> conceptos = enCurso.stream()
                .sorted(Comparator.comparing((ConceptoEnCurso c) -> c.estado().proximo()).thenComparing(ConceptoEnCurso::tema)
                        .thenComparing(c -> EjecutorCambiosOpinion.clave(c.concepto())))
                .map(c -> new ResultadoRepeticion.Concepto(c.tema(), c.concepto(), c.estado().repasos(), Textos.decimal(c.estado().facilidad(), false),
                        c.estado().intervalo(), c.estado().proximo().toString(), cuando(c.estado().proximo(), hoy)))
                .toList();
        Set<LocalDate> dias = new HashSet<>();
        hechos.forEach(h -> dias.add(h.fecha()));
        int racha = racha(dias, hoy);
        String media = enCurso.isEmpty() ? "—" : Textos.decimal(enCurso.stream().map(c -> c.estado().facilidad()).reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(new BigDecimal(enCurso.size()), 1, RoundingMode.HALF_UP), false);
        List<String> avisos = new ArrayList<>();
        if (deHoy > config.retosPorDia()) {
            int sobran = deHoy - config.retosPorDia();
            avisos.add("Hoy te tocan " + deHoy + " repasos y tu límite es " + config.retosPorDia() + " por día: "
                    + Textos.contar(sobran, "repaso pasa", "repasos pasan") + " a mañana.");
        }
        String resumen;
        if (enCurso.isEmpty()) {
            resumen = "Todavía no hay repasos · algoritmo SM-2.";
        } else if (deHoy > 0) {
            List<String> temas = porTema.entrySet().stream().map(e -> e.getKey().contar(e.getValue())).toList();
            resumen = "Hoy, " + Textos.contar(deHoy, "repaso", "repasos") + ": " + String.join(", ", temas) + " · algoritmo SM-2 · facilidad media "
                    + media + " · racha " + racha + ".";
        } else {
            LocalDate proximo = conceptos.getFirst().proximo().transform(LocalDate::parse);
            resumen = "Hoy no te toca ningún repaso; el próximo es " + cuando(proximo, hoy) + " · algoritmo SM-2 · facilidad media " + media
                    + " · racha " + racha + ".";
        }
        return new ResultadoRepeticion(calendario, deHoy, conceptos, racha, media, avisos, resumen);
    }

    @Override
    public ResultadoRepeticion migrar(Json datosViejos, int desdeVersion) {
        throw new IllegalArgumentException(ID + " no tiene versiones anteriores a la " + VERSION_ESQUEMA
                + "; se pidió migrar desde la " + desdeVersion);
    }
}
