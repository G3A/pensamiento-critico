package pensamiento.tecnicas.f8;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import pensamiento.nucleo.AfirmacionConRol;
import pensamiento.nucleo.CambioOpinion;
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
 * T46 · Registro de cambios de opinión (Tetlock 2015). La línea de tiempo de los cambios de confianza con su causa, el
 * resumen del año, una lectura de si cambias por razones o por presión y las posturas sin revisar hace más de N meses.
 * Registra a mano un cambio nuevo, que se guarda con la ejecución (R05). No usa IA. Las reglas están en
 * docs/ejemplos/T46.md.
 */
@Component
public class EjecutorCambiosOpinion implements Ejecutor<EjecutorCambiosOpinion.Config, EjecutorCambiosOpinion.Entrada, ResultadoCambiosOpinion> {

    public static final IdTecnica ID = IdTecnica.de("T46");
    public static final int VERSION_ESQUEMA = 1;
    public static final int TOPE_FILAS = 60;
    private static final Pattern CODIGO = Pattern.compile("^T(0[1-9]|[1-4][0-9])$");

    /** Configuración de T46, versión de esquema 1. */
    public record Config(boolean registroAutomatico, List<CambioOpinion.Causa> causas, int mesesSinRevisar) {
        public Config {
            causas = causas == null ? List.of() : List.copyOf(causas);
        }
    }

    /** @param fecha AAAA-MM-DD; @param tecnica la que lo registró, T01 a T49 */
    public record Cambio(String fecha, String postura, Integer antes, Integer despues, CambioOpinion.Causa causa, String tecnica) {
    }

    /** @param desde la última vez que la trabajaste, AAAA-MM-DD */
    public record Postura(String postura, String desde) {
    }

    /** Lo ya registrado, las posturas y, opcional, un cambio nuevo que se registra a mano. */
    public record Entrada(List<Cambio> cambios, List<Postura> posturas, String nuevaPostura, Integer nuevaAntes, Integer nuevaDespues,
                          CambioOpinion.Causa nuevaCausa) {
        public Entrada {
            cambios = cambios == null ? List.of() : List.copyOf(cambios);
            posturas = posturas == null ? List.of() : List.copyOf(posturas);
        }

        boolean traeNuevo() {
            return !Textos.vacio(nuevaPostura) || nuevaAntes != null || nuevaDespues != null || nuevaCausa != null;
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
    public Tipos<Config, Entrada, ResultadoCambiosOpinion> tipos() {
        return new Tipos<>(Config.class, Entrada.class, ResultadoCambiosOpinion.class);
    }

    @Override
    public Validacion validar(Config config, Entrada entrada) {
        List<Validacion.Error> errores = new ArrayList<>();
        if (config.causas().isEmpty()) {
            errores.add(new Validacion.Error("config.causas", "Elige al menos una causa para registrar a mano."));
        }
        if (config.mesesSinRevisar() < 1 || config.mesesSinRevisar() > 60) {
            errores.add(new Validacion.Error("config.mesesSinRevisar", "Los meses para avisar van de 1 a 60."));
        }
        if (entrada.cambios().size() > TOPE_FILAS) {
            errores.add(new Validacion.Error("cambios", "Caben como máximo " + TOPE_FILAS + " cambios."));
        }
        if (entrada.posturas().size() > TOPE_FILAS) {
            errores.add(new Validacion.Error("posturas", "Caben como máximo " + TOPE_FILAS + " posturas."));
        }
        for (int i = 0; i < entrada.cambios().size(); i++) {
            Cambio c = entrada.cambios().get(i);
            String campo = "cambios[" + i + "]";
            fecha(errores, campo + ".fecha", c.fecha());
            if (Textos.vacio(c.postura())) {
                errores.add(new Validacion.Error(campo + ".postura", "Escribe la postura del cambio " + (i + 1) + "."));
            }
            confianzas(errores, campo + ".despues", c.antes(), c.despues());
            if (c.causa() == null) {
                errores.add(new Validacion.Error(campo + ".causa", "Elige la causa del cambio " + (i + 1) + "."));
            }
            if (c.tecnica() == null || !CODIGO.matcher(c.tecnica().strip()).matches()) {
                errores.add(new Validacion.Error(campo + ".tecnica", "Escribe el código de la técnica que lo registró, de T01 a T49."));
            }
        }
        for (int i = 0; i < entrada.posturas().size(); i++) {
            Postura p = entrada.posturas().get(i);
            String campo = "posturas[" + i + "]";
            if (Textos.vacio(p.postura())) {
                errores.add(new Validacion.Error(campo + ".postura", "Escribe la postura " + (i + 1) + "."));
            }
            fecha(errores, campo + ".desde", p.desde());
        }
        if (entrada.traeNuevo()) {
            if (Textos.vacio(entrada.nuevaPostura())) {
                errores.add(new Validacion.Error("nuevaPostura", "Escribe la postura que cambió."));
            }
            confianzas(errores, "nuevaDespues", entrada.nuevaAntes(), entrada.nuevaDespues());
            if (entrada.nuevaCausa() == null || !config.causas().contains(entrada.nuevaCausa())) {
                errores.add(new Validacion.Error("nuevaCausa", "Elige una causa de las que tienes activas."));
            }
        }
        return new Validacion(errores);
    }

    private static void fecha(List<Validacion.Error> errores, String campo, String valor) {
        if (Textos.vacio(valor)) {
            errores.add(new Validacion.Error(campo, "Escribe la fecha."));
            return;
        }
        try {
            LocalDate.parse(valor.strip());
        } catch (DateTimeParseException e) {
            errores.add(new Validacion.Error(campo, "La fecha va como AAAA-MM-DD."));
        }
    }

    private static void confianzas(List<Validacion.Error> errores, String campo, Integer antes, Integer despues) {
        if (antes == null || despues == null || antes < 0 || antes > 100 || despues < 0 || despues > 100) {
            errores.add(new Validacion.Error(campo, "Escribe tu confianza antes y después, de 0 a 100."));
        } else if (antes.equals(despues)) {
            errores.add(new Validacion.Error(campo, "No hay cambio de opinión si la confianza no cambió."));
        }
    }

    @Override
    public Resultado<ResultadoCambiosOpinion> ejecutar(Config config, Entrada entrada, Contexto ctx) {
        Validacion validacion = validar(config, entrada);
        if (!validacion.esValida()) {
            throw new IllegalArgumentException("Entrada inválida para " + ID + ": " + validacion.errores());
        }
        LocalDate hoy = ctx.reloj().hoy();
        List<Cambio> cambios = new ArrayList<>(entrada.cambios());
        List<AfirmacionConRol> afirmaciones = new ArrayList<>();
        List<CambioOpinion.Declarado> declarados = new ArrayList<>();
        if (entrada.traeNuevo()) {
            String postura = entrada.nuevaPostura().strip();
            cambios.add(new Cambio(hoy.toString(), postura, entrada.nuevaAntes(), entrada.nuevaDespues(), entrada.nuevaCausa(), ID.valor()));
            UUID afirmacion = ctx.nuevoId().get();
            afirmaciones.add(new AfirmacionConRol(afirmacion, postura, TipoAfirmacion.JUICIO_DE_VALOR, RolAfirmacion.POSTURA, SentidoAfirmacion.PRODUCIDA,
                    OrigenAfirmacion.USUARIO));
            declarados.add(new CambioOpinion.Declarado(ctx.nuevoId().get(), afirmacion, entrada.nuevaAntes(), entrada.nuevaDespues(),
                    entrada.nuevaCausa()));
        }
        ResultadoCambiosOpinion valor = calcular(config, cambios, entrada.posturas(), hoy);
        return new Resultado<>(VERSION_ESQUEMA, valor, afirmaciones, List.of(), valor.resumen(), List.of(), Optional.empty(), Optional.empty(),
                List.of(), declarados);
    }

    /** La regla de T46, sin tope de filas: también la usa P20 con los cambios y las posturas guardados. */
    public static ResultadoCambiosOpinion calcular(Config config, List<Cambio> cambios, List<Postura> posturas, LocalDate hoy) {
        List<ResultadoCambiosOpinion.Cambio> linea = new ArrayList<>();
        for (int i = cambios.size() - 1; i >= 0; i--) {
            Cambio c = cambios.get(i);
            LocalDate fecha = LocalDate.parse(c.fecha().strip());
            linea.add(new ResultadoCambiosOpinion.Cambio(fecha.toString(), Textos.fecha(fecha), c.postura().strip(), c.antes(), c.despues(), c.causa(),
                    c.tecnica().strip()));
        }
        // Del más reciente al más viejo; a igual fecha, primero el escrito después (la lista ya va al revés y el orden es estable).
        linea.sort(Comparator.comparing(ResultadoCambiosOpinion.Cambio::fecha).reversed());

        int anio = hoy.getYear();
        Map<CambioOpinion.Causa, Integer> porCausa = new LinkedHashMap<>();
        for (CambioOpinion.Causa causa : CambioOpinion.Causa.values()) {
            porCausa.put(causa, 0);
        }
        int total = 0;
        for (ResultadoCambiosOpinion.Cambio c : linea) {
            if (LocalDate.parse(c.fecha()).getYear() == anio) {
                porCausa.merge(c.causa(), 1, Integer::sum);
                total++;
            }
        }
        String lectura = lectura(total, porCausa);

        // Posturas: las de la entrada más las de los cambios, una por texto plegado, con su última fecha.
        Map<String, String> textoPorClave = new LinkedHashMap<>();
        Map<String, LocalDate> ultima = new LinkedHashMap<>();
        for (Postura p : posturas) {
            anotar(textoPorClave, ultima, p.postura(), LocalDate.parse(p.desde().strip()));
        }
        for (Cambio c : cambios) {
            anotar(textoPorClave, ultima, c.postura(), LocalDate.parse(c.fecha().strip()));
        }
        LocalDate limite = hoy.minusMonths(config.mesesSinRevisar());
        List<ResultadoCambiosOpinion.SinRevisar> sinRevisar = ultima.entrySet().stream().filter(e -> e.getValue().isBefore(limite))
                .sorted(Map.Entry.comparingByValue())
                .map(e -> new ResultadoCambiosOpinion.SinRevisar(textoPorClave.get(e.getKey()), e.getValue().toString(), Textos.fecha(e.getValue()),
                        (int) ChronoUnit.MONTHS.between(e.getValue(), hoy)))
                .toList();

        List<String> avisos = new ArrayList<>();
        if (!config.registroAutomatico()) {
            avisos.add("El registro automático está apagado: T08, el Consejero y la ficha de verificación no anotan tus cambios; solo cuentan los que"
                    + " registras a mano.");
        }
        String resumen = Textos.contar(total, "cambio de opinión", "cambios de opinión") + " en " + anio + " · "
                + Textos.contar(sinRevisar.size(), "postura sin revisar", "posturas sin revisar") + ".";
        List<ResultadoCambiosOpinion.PorCausa> causas = porCausa.entrySet().stream()
                .map(e -> new ResultadoCambiosOpinion.PorCausa(e.getKey(), e.getValue())).toList();
        return new ResultadoCambiosOpinion(linea, anio, total, causas, lectura, config.mesesSinRevisar(), sinRevisar, avisos, resumen);
    }

    private static void anotar(Map<String, String> textoPorClave, Map<String, LocalDate> ultima, String postura, LocalDate fecha) {
        String clave = clave(postura);
        textoPorClave.putIfAbsent(clave, postura.strip());
        ultima.merge(clave, fecha, (a, b) -> a.isAfter(b) ? a : b);
    }

    /** Sin mayúsculas, tildes, espacios repetidos ni el punto final: dos posturas así escritas son la misma. */
    public static String clave(String postura) {
        String t = Textos.plegar(postura.strip()).replaceAll("\\s+", " ");
        while (t.endsWith(".")) {
            t = t.substring(0, t.length() - 1).strip();
        }
        return t;
    }

    private static String lectura(int total, Map<CambioOpinion.Causa, Integer> porCausa) {
        int razones = porCausa.entrySet().stream().filter(e -> e.getKey().esRazon()).mapToInt(Map.Entry::getValue).sum();
        int presion = porCausa.get(CambioOpinion.Causa.PRESION);
        if (total == 0) {
            return "Este año todavía no registraste cambios de opinión.";
        }
        if (presion > 0 && presion >= razones) {
            return "Este año cambiaste de opinión por presión social tanto o más que por razones: revisa esos cambios con T34 · Steelmanning.";
        }
        if (presion == 0 && razones > 0) {
            return "Cambias de opinión por razones (evidencia, steelman, revisión o regla), no por presión social.";
        }
        if (presion > 0) {
            return "La mayoría de tus cambios tuvo una razón; por presión social: " + presion + ".";
        }
        return "Tus cambios de este año no dicen la causa: al registrarlos, elige qué te movió.";
    }

    @Override
    public ResultadoCambiosOpinion migrar(Json datosViejos, int desdeVersion) {
        throw new IllegalArgumentException(ID + " no tiene versiones anteriores a la " + VERSION_ESQUEMA
                + "; se pidió migrar desde la " + desdeVersion);
    }
}
