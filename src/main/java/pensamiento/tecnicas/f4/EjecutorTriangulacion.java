package pensamiento.tecnicas.f4;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import org.springframework.stereotype.Component;

import pensamiento.nucleo.AfirmacionConRol;
import pensamiento.nucleo.ConModelo;
import pensamiento.nucleo.Contexto;
import pensamiento.nucleo.Ejecutor;
import pensamiento.nucleo.EstadoAfirmacion;
import pensamiento.nucleo.Evidencia;
import pensamiento.nucleo.Fuente;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Json;
import pensamiento.nucleo.OrigenAfirmacion;
import pensamiento.nucleo.Pendiente;
import pensamiento.nucleo.Propuesta;
import pensamiento.nucleo.Resultado;
import pensamiento.nucleo.RolAfirmacion;
import pensamiento.nucleo.SentidoAfirmacion;
import pensamiento.nucleo.TipoAfirmacion;
import pensamiento.nucleo.TipoPendiente;
import pensamiento.nucleo.Validacion;
import pensamiento.nucleo.puertos.Clasificacion;
import pensamiento.nucleo.reglas.R01FuerzaEvidencia;
import pensamiento.nucleo.reglas.R02FuerzaNeta;
import pensamiento.nucleo.reglas.R03EstadoAfirmacion;
import pensamiento.tecnicas.comun.ModeloLocal;
import pensamiento.tecnicas.comun.Prompts;
import pensamiento.tecnicas.comun.Textos;

/**
 * T22 · Triangulación (Denzin 1978). Aplica R01, R02 y R03 de la sección 5b, versión 1, sobre las fuentes que la persona
 * registra a mano: "verificada" o "refutada" exige al menos dos grupos de origen distintos. El modelo puede etiquetar
 * los pasajes sin etiquetar; esa etiqueta no cuenta hasta adoptarse. Reglas en docs/ejemplos/T22.md.
 */
@Component
public class EjecutorTriangulacion implements Ejecutor<EjecutorTriangulacion.Config, EjecutorTriangulacion.Entrada, ResultadoTriangulacion>,
        ConModelo<EjecutorTriangulacion.Config, EjecutorTriangulacion.Entrada> {

    public static final IdTecnica ID = IdTecnica.de("T22");
    public static final int VERSION_ESQUEMA = 1;
    public static final String PROMPT = "t22-postura";
    public static final int VERSION_PROMPT = 1;
    public static final String SIN_ETIQUETAR = "sin_etiquetar";
    public static final String IRRELEVANTE = "irrelevante";
    public static final List<String> ETIQUETAS = List.of("apoya", "contradice", "matiza", IRRELEVANTE);

    public enum Modo {
        PLANTILLAS, PLANTILLAS_Y_MODELO;

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    /** Configuración de T22, versión de esquema 1: el mínimo de grupos de origen distintos de R03. */
    public record Config(int fuentesMinimas, Modo modo) {
    }

    /**
     * @param tipoFuente   primaria, secundaria o terciaria
     * @param diseno       no_aplica o uno de los cinco diseños del estudio
     * @param fecha        AAAA-MM-DD, opcional
     * @param postura      apoya, contradice, matiza, irrelevante o sin_etiquetar
     * @param etiquetadaPor "modelo" si la postura vino de una propuesta adoptada
     */
    public record FuenteRegistrada(String titulo, String tipoFuente, String diseno, String fecha, String grupo, boolean independiente,
                                   boolean original, Integer craap, String pasaje, String postura, String etiquetadaPor) {
    }

    public record Entrada(String afirmacion, String tipo, List<FuenteRegistrada> fuentes, List<Propuesta> propuestas) {
        public Entrada {
            fuentes = fuentes == null ? List.of() : List.copyOf(fuentes);
            propuestas = propuestas == null ? List.of() : List.copyOf(propuestas);
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
    public Tipos<Config, Entrada, ResultadoTriangulacion> tipos() {
        return new Tipos<>(Config.class, Entrada.class, ResultadoTriangulacion.class);
    }

    @Override
    public Validacion validar(Config config, Entrada entrada) {
        List<Validacion.Error> errores = new ArrayList<>();
        if (config.fuentesMinimas() < 2 || config.fuentesMinimas() > 4) {
            errores.add(new Validacion.Error("config.fuentesMinimas", "El mínimo de grupos de origen va de 2 a 4."));
        }
        if (config.modo() == null) {
            errores.add(new Validacion.Error("config.modo", "Elige el modo."));
        }
        if (Textos.vacio(entrada.afirmacion())) {
            errores.add(new Validacion.Error("afirmacion", "Escribe la afirmación que quieres triangular."));
        }
        if (tipoAfirmacion(entrada.tipo()).isEmpty()) {
            errores.add(new Validacion.Error("tipo", "Elige el tipo de la afirmación."));
        }
        List<FuenteRegistrada> fuentes = conTitulo(entrada);
        if (fuentes.isEmpty() || fuentes.size() > 6) {
            errores.add(new Validacion.Error("fuentes", "Registra de una a seis fuentes."));
        }
        for (FuenteRegistrada f : fuentes) {
            if (tipoFuente(f.tipoFuente()).isEmpty() || Textos.vacio(f.pasaje()) || postura(f.postura()).isEmpty()) {
                errores.add(new Validacion.Error("fuentes", "Cada fuente necesita su tipo, el pasaje copiado tal cual y su postura."));
                break;
            }
            if (!Textos.vacio(f.fecha())) {
                try {
                    LocalDate.parse(f.fecha());
                } catch (DateTimeParseException e) {
                    errores.add(new Validacion.Error("fuentes", "La fecha de una fuente va como AAAA-MM-DD."));
                    break;
                }
            }
            if (f.craap() != null && (f.craap() < 0 || f.craap() > 25)) {
                errores.add(new Validacion.Error("fuentes", "El puntaje CRAAP va de 0 a 25."));
                break;
            }
        }
        for (Propuesta p : entrada.propuestas()) {
            if (!Propuesta.codigoValido(p.codigo()) || !ETIQUETAS.contains(p.valor()) || numero(p.destino()) < 1 || numero(p.destino()) > fuentes.size()) {
                errores.add(new Validacion.Error("propuestas", "Las propuestas del modelo no corresponden a estas fuentes: vuelve a pedirlas."));
                break;
            }
        }
        return new Validacion(errores);
    }

    @Override
    public Resultado<ResultadoTriangulacion> ejecutar(Config config, Entrada entrada, Contexto ctx) {
        Validacion validacion = validar(config, entrada);
        if (!validacion.esValida()) {
            throw new IllegalArgumentException("Entrada inválida para " + ID + ": " + validacion.errores());
        }
        TipoAfirmacion tipo = tipoAfirmacion(entrada.tipo()).orElseThrow();
        UUID afirmacionId = ctx.nuevoId().get();
        LocalDate hoy = ctx.reloj().hoy();
        List<FuenteRegistrada> fuentes = conTitulo(entrada);
        Set<Integer> conPropuestaPendiente = new HashSet<>();
        entrada.propuestas().stream().filter(p -> !p.adoptada()).forEach(p -> conPropuestaPendiente.add(numero(p.destino())));
        List<ResultadoTriangulacion.EvidenciaEvaluada> evaluadas = new ArrayList<>();
        List<Evidencia> cuentan = new ArrayList<>();
        Set<String> grupos = new HashSet<>();
        for (int i = 0; i < fuentes.size(); i++) {
            FuenteRegistrada f = fuentes.get(i);
            Fuente fuente = new Fuente(ctx.nuevoId().get(), f.titulo().strip(), tipoFuente(f.tipoFuente()).orElseThrow(), diseno(f.diseno()),
                    Textos.vacio(f.fecha()) ? Optional.empty() : Optional.of(LocalDate.parse(f.fecha())),
                    Textos.vacio(f.grupo()) ? Optional.empty() : Optional.of(f.grupo().strip().toLowerCase()), f.independiente(), f.original(),
                    Optional.ofNullable(f.craap()));
            int fuerza = R01FuerzaEvidencia.fuerza(tipo, fuente, hoy, R01FuerzaEvidencia.Parametros.v1());
            String postura = f.postura();
            boolean delModelo = "modelo".equals(f.etiquetadaPor());
            Optional<Evidencia.Postura> p = postura(postura).flatMap(x -> x);
            String detalle = null;
            if (SIN_ETIQUETAR.equals(postura)) {
                detalle = conPropuestaPendiente.contains(i + 1) ? "etiquetada por el modelo · sin adoptar · no cuenta" : "sin etiquetar · no cuenta";
            } else if (IRRELEVANTE.equals(postura)) {
                detalle = "irrelevante · no cuenta";
            }
            boolean cuenta = p.isPresent();
            if (cuenta) {
                Evidencia e = new Evidencia(ctx.nuevoId().get(), afirmacionId, fuente, f.pasaje().strip(), p.get(), fuerza,
                        delModelo ? Evidencia.EtiquetadaPor.MODELO : Evidencia.EtiquetadaPor.USUARIO, true);
                cuentan.add(e);
                if (p.get() != Evidencia.Postura.MATIZA) {
                    grupos.add(fuente.grupoOrigen().orElse("fuente:" + i));
                }
            }
            evaluadas.add(new ResultadoTriangulacion.EvidenciaEvaluada("F" + (i + 1), f.titulo().strip(), Textos.vacio(f.grupo()) ? null : f.grupo().strip(),
                    f.pasaje().strip(), postura, fuerza, cuenta, delModelo ? "modelo" : "usuario", detalle));
        }
        R02FuerzaNeta.FuerzaNeta neta = R02FuerzaNeta.neta(cuentan, R02FuerzaNeta.Parametros.v1());
        R03EstadoAfirmacion.Parametros r03 = new R03EstadoAfirmacion.Parametros(R03EstadoAfirmacion.Parametros.v1().umbralFuerte(), config.fuentesMinimas());
        EstadoAfirmacion estado = R03EstadoAfirmacion.estado(tipo, cuentan, neta, r03);
        String magnitud = switch (neta.magnitud()) {
            case DEBIL -> "débil";
            case MEDIA -> "media";
            case FUERTE -> "fuerte";
        };
        String netaTexto = neta.valor() > 0 ? "+" + neta.valor() : String.valueOf(neta.valor());
        int gruposAFavor = gruposEn(cuentan, Evidencia.Postura.APOYA);
        int gruposEnContra = gruposEn(cuentan, Evidencia.Postura.CONTRADICE);
        String motivo = switch (estado) {
            case NO_VERIFICABLE -> "Es un juicio de valor o una definición: no se verifica con fuentes.";
            case SIN_VERIFICAR -> "Ningún pasaje cuenta todavía: etiqueta su postura.";
            case DISPUTADA -> "Hay evidencia fuerte (6 o más) a favor y en contra: revisa en qué difieren las fuentes.";
            case VERIFICADA -> gruposAFavor + " grupos de origen distintos a favor y fuerza neta " + netaTexto + " (fuerte) (regla R03).";
            case REFUTADA -> gruposEnContra + " grupos de origen distintos en contra y fuerza neta " + netaTexto + " (fuerte) (regla R03).";
            case EN_VERIFICACION -> neta.magnitud() == R02FuerzaNeta.Magnitud.FUERTE
                    ? "Fuerza neta " + netaTexto + " (fuerte), pero las fuentes " + (neta.aFavor() ? "a favor" : "en contra") + " son de "
                    + Textos.contar(neta.aFavor() ? gruposAFavor : gruposEnContra, "grupo", "grupos") + " de origen: falta una fuente independiente (regla R03)."
                    : "Fuerza neta " + netaTexto + " (" + magnitud + "): todavía no alcanza para verificada ni refutada.";
        };
        String afirmacion = entrada.afirmacion().strip();
        List<Pendiente> pendientes = new ArrayList<>();
        if (estado == EstadoAfirmacion.EN_VERIFICACION) {
            pendientes.add(new Pendiente(TipoPendiente.VERIFICACION, Optional.of(afirmacionId), Optional.empty(), "Buscar una fuente independiente para: " + afirmacion));
        } else if (estado == EstadoAfirmacion.DISPUTADA) {
            pendientes.add(new Pendiente(TipoPendiente.REVISION, Optional.of(afirmacionId), Optional.empty(), "Revisar la evidencia en conflicto sobre: " + afirmacion));
        }
        String resumen = Textos.mayusculaInicial(texto(estado)) + " · fuerza neta " + netaTexto + " (" + magnitud + ") · "
                + Textos.contar(fuentes.size(), "fuente", "fuentes") + ", " + cuentan.size() + (cuentan.size() == 1 ? " cuenta." : " cuentan.");
        List<AfirmacionConRol> afirmaciones = List.of(new AfirmacionConRol(afirmacionId, afirmacion, tipo, RolAfirmacion.HIPOTESIS,
                SentidoAfirmacion.PRODUCIDA, OrigenAfirmacion.USUARIO));
        ResultadoTriangulacion valor = new ResultadoTriangulacion(afirmacion, tipo.enBaseDeDatos(), evaluadas, neta.valor(), magnitud,
                estado.name().toLowerCase(), motivo, cuentan.size(), grupos.size(), entrada.propuestas(), resumen);
        return new Resultado<>(VERSION_ESQUEMA, valor, afirmaciones, pendientes, resumen, List.of(), Resultado.registroDe(entrada.propuestas()),
                Optional.empty());
    }

    /** "verificada", "en verificación", "sin verificar"… */
    public static String texto(EstadoAfirmacion estado) {
        return switch (estado) {
            case NO_VERIFICABLE -> "no verificable";
            case SIN_VERIFICAR -> "sin verificar";
            case DISPUTADA -> "disputada";
            case VERIFICADA -> "verificada";
            case REFUTADA -> "refutada";
            case EN_VERIFICACION -> "en verificación";
        };
    }

    private static int gruposEn(List<Evidencia> evidencias, Evidencia.Postura postura) {
        Set<String> grupos = new HashSet<>();
        for (Evidencia e : evidencias) {
            if (e.postura() == postura) {
                grupos.add(e.fuente().grupoOrigen().orElse("fuente:" + e.fuente().id()));
            }
        }
        return grupos.size();
    }

    @Override
    public ResultadoTriangulacion migrar(Json datosViejos, int desdeVersion) {
        throw new IllegalArgumentException(ID + " no tiene versiones anteriores a la " + VERSION_ESQUEMA
                + "; se pidió migrar desde la " + desdeVersion);
    }

    // ---------------------------------------------------------------------------------------------
    // El modelo propone (RF-14)
    // ---------------------------------------------------------------------------------------------

    @Override
    public boolean usaModelo(Config config) {
        return config.modo() == Modo.PLANTILLAS_Y_MODELO;
    }

    @Override
    public List<Propuesta> propuestas(Entrada entrada) {
        return entrada.propuestas();
    }

    /** Una llamada por pasaje sin etiquetar, contra apoya, contradice, matiza o irrelevante. */
    @Override
    public Propuestas proponer(Config config, Entrada entrada, Contexto ctx, Consumer<String> provisional, int primerNumero) {
        if (Textos.vacio(entrada.afirmacion())) {
            return Propuestas.cayo("Escribe primero la afirmación: el modelo etiqueta cada pasaje frente a ella.");
        }
        List<FuenteRegistrada> fuentes = conTitulo(entrada);
        Prompts.Prompt prompt = Prompts.de(PROMPT, VERSION_PROMPT);
        return ModeloLocal.conCaida(ctx, ia -> {
            List<Propuesta> nuevas = new ArrayList<>();
            for (int i = 0; i < fuentes.size(); i++) {
                FuenteRegistrada f = fuentes.get(i);
                if (!SIN_ETIQUETAR.equals(f.postura()) || Textos.vacio(f.pasaje())) {
                    continue;
                }
                provisional.accept("F" + (i + 1) + "… ");
                Clasificacion c = ModeloLocal.clasificar(ia, prompt.sistema(Map.of()),
                        prompt.pedido(Map.of("afirmacion", entrada.afirmacion().strip(), "pasaje", f.pasaje().strip())), ETIQUETAS);
                nuevas.add(new Propuesta(Propuesta.codigo(primerNumero + nuevas.size()), String.valueOf(i + 1),
                        "F" + (i + 1) + " · " + f.titulo().strip(), c.etiqueta(), c.porQue(), false, c.modelo(), c.digest(), prompt.version()));
            }
            return nuevas;
        });
    }

    /** Adoptar pone la postura propuesta en el pasaje, etiquetado por el modelo; irrelevante lo deja fuera del cálculo. */
    @Override
    public Entrada adoptar(Entrada entrada, String codigo) {
        List<Propuesta> propuestas = Propuesta.adoptar(entrada.propuestas(), codigo);
        Propuesta p = Propuesta.buscar(propuestas, codigo);
        List<FuenteRegistrada> fuentes = new ArrayList<>(conTitulo(entrada));
        int i = numero(p.destino()) - 1;
        if (i < 0 || i >= fuentes.size()) {
            throw new IllegalArgumentException("La propuesta " + codigo + " no corresponde a estas fuentes");
        }
        FuenteRegistrada f = fuentes.get(i);
        fuentes.set(i, new FuenteRegistrada(f.titulo(), f.tipoFuente(), f.diseno(), f.fecha(), f.grupo(), f.independiente(), f.original(), f.craap(),
                f.pasaje(), p.valor(), "modelo"));
        return new Entrada(entrada.afirmacion(), entrada.tipo(), fuentes, propuestas);
    }

    private static List<FuenteRegistrada> conTitulo(Entrada entrada) {
        return entrada.fuentes().stream().filter(f -> f != null && !Textos.vacio(f.titulo())).toList();
    }

    private static Optional<TipoAfirmacion> tipoAfirmacion(String valor) {
        for (TipoAfirmacion t : TipoAfirmacion.values()) {
            if (t.enBaseDeDatos().equals(valor)) {
                return Optional.of(t);
            }
        }
        return Optional.empty();
    }

    private static Optional<Fuente.TipoFuente> tipoFuente(String valor) {
        for (Fuente.TipoFuente t : Fuente.TipoFuente.values()) {
            if (t.name().equalsIgnoreCase(valor)) {
                return Optional.of(t);
            }
        }
        return Optional.empty();
    }

    private static Optional<Fuente.DisenoEstudio> diseno(String valor) {
        for (Fuente.DisenoEstudio d : Fuente.DisenoEstudio.values()) {
            if (d.name().equalsIgnoreCase(valor)) {
                return Optional.of(d);
            }
        }
        return Optional.empty();
    }

    /** Vacío si el valor no es una postura válida; presente con vacío si es válida pero no cuenta (sin etiquetar, irrelevante). */
    private static Optional<Optional<Evidencia.Postura>> postura(String valor) {
        if (SIN_ETIQUETAR.equals(valor) || IRRELEVANTE.equals(valor)) {
            return Optional.of(Optional.empty());
        }
        for (Evidencia.Postura p : Evidencia.Postura.values()) {
            if (p.name().equalsIgnoreCase(valor)) {
                return Optional.of(Optional.of(p));
            }
        }
        return Optional.empty();
    }

    private static int numero(String texto) {
        try {
            return Integer.parseInt(texto);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
