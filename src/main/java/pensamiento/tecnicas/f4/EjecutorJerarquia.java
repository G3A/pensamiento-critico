package pensamiento.tecnicas.f4;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import pensamiento.nucleo.AfirmacionConRol;
import pensamiento.nucleo.Contexto;
import pensamiento.nucleo.Ejecutor;
import pensamiento.nucleo.Evidencia;
import pensamiento.nucleo.Fuente;
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
import pensamiento.nucleo.reglas.R01FuerzaEvidencia;
import pensamiento.nucleo.reglas.R02FuerzaNeta;
import pensamiento.tecnicas.comun.Textos;

/**
 * T23 · Jerarquía de evidencia (Sackett 1996; GRADE 2004): fuerza de cada evidencia por diseño del estudio o tipo de fuente
 * (R01, con los pesos base de la configuración) y fuerza neta de la afirmación (R02). No usa IA. Las reglas están en
 * docs/ejemplos/T23.md.
 */
@Component
public class EjecutorJerarquia implements Ejecutor<EjecutorJerarquia.Config, EjecutorJerarquia.Entrada, ResultadoJerarquia> {

    public static final IdTecnica ID = IdTecnica.de("T23");
    public static final int VERSION_ESQUEMA = 1;

    /** Configuración de T23, versión de esquema 1: los pesos base de R01 por diseño del estudio y por tipo de fuente. */
    public record Config(int revisionSistematica, int ensayoControlado, int observacional, int opinionExperto, int testimonio, int primaria,
                         int secundaria, int terciaria) {

        public static Config v1() {
            return new Config(4, 3, 2, 1, 0, 2, 1, 0);
        }

        List<Integer> pesos() {
            return List.of(revisionSistematica, ensayoControlado, observacional, opinionExperto, testimonio, primaria, secundaria, terciaria);
        }

        R01FuerzaEvidencia.Parametros parametros() {
            R01FuerzaEvidencia.Parametros v1 = R01FuerzaEvidencia.Parametros.v1();
            Map<Fuente.DisenoEstudio, Integer> porDiseno = new EnumMap<>(Fuente.DisenoEstudio.class);
            porDiseno.put(Fuente.DisenoEstudio.REVISION_SISTEMATICA, revisionSistematica);
            porDiseno.put(Fuente.DisenoEstudio.ENSAYO_CONTROLADO, ensayoControlado);
            porDiseno.put(Fuente.DisenoEstudio.OBSERVACIONAL, observacional);
            porDiseno.put(Fuente.DisenoEstudio.OPINION_EXPERTO, opinionExperto);
            porDiseno.put(Fuente.DisenoEstudio.TESTIMONIO, testimonio);
            Map<Fuente.TipoFuente, Integer> porTipo = new EnumMap<>(Fuente.TipoFuente.class);
            porTipo.put(Fuente.TipoFuente.PRIMARIA, primaria);
            porTipo.put(Fuente.TipoFuente.SECUNDARIA, secundaria);
            porTipo.put(Fuente.TipoFuente.TERCIARIA, terciaria);
            return new R01FuerzaEvidencia.Parametros(porDiseno, porTipo, v1.bonoReciente(), v1.aniosParaReciente(), v1.bonoIndependiente(),
                    v1.bonoAccesoOriginal(), v1.bonoCraap(), v1.umbralCraap(), v1.maximo());
        }
    }

    /**
     * @param tipoFuente primaria, secundaria o terciaria
     * @param diseno     no_aplica o uno de los cinco diseños del estudio
     * @param fecha      AAAA-MM-DD, opcional
     * @param postura    apoya, contradice o matiza
     */
    public record EvidenciaRegistrada(String descripcion, String tipoFuente, String diseno, String fecha, boolean independiente, boolean original,
                                      Integer craap, String postura) {
    }

    public record Entrada(String afirmacion, String tipo, List<EvidenciaRegistrada> evidencias) {
        public Entrada {
            evidencias = evidencias == null ? List.of() : List.copyOf(evidencias);
        }
    }

    private static final Map<Fuente.DisenoEstudio, String> NIVELES_DISENO = new EnumMap<>(Map.of(
            Fuente.DisenoEstudio.REVISION_SISTEMATICA, "revisión sistemática",
            Fuente.DisenoEstudio.ENSAYO_CONTROLADO, "ensayo controlado",
            Fuente.DisenoEstudio.OBSERVACIONAL, "observacional",
            Fuente.DisenoEstudio.OPINION_EXPERTO, "opinión de experto",
            Fuente.DisenoEstudio.TESTIMONIO, "testimonio"));
    private static final String SIN_DISENO = "sin diseño";

    @Override
    public IdTecnica id() {
        return ID;
    }

    @Override
    public int versionEsquema() {
        return VERSION_ESQUEMA;
    }

    @Override
    public Tipos<Config, Entrada, ResultadoJerarquia> tipos() {
        return new Tipos<>(Config.class, Entrada.class, ResultadoJerarquia.class);
    }

    @Override
    public Validacion validar(Config config, Entrada entrada) {
        List<Validacion.Error> errores = new ArrayList<>();
        if (config.pesos().stream().anyMatch(p -> p < 0 || p > 4)) {
            errores.add(new Validacion.Error("config.revisionSistematica", "Cada peso base va de 0 a 4."));
        }
        if (Textos.vacio(entrada.afirmacion())) {
            errores.add(new Validacion.Error("afirmacion", "Escribe la afirmación que quieres pesar."));
        }
        Optional<TipoAfirmacion> tipo = tipoAfirmacion(entrada.tipo());
        if (tipo.isEmpty()) {
            errores.add(new Validacion.Error("tipo", "Elige el tipo de la afirmación."));
        } else if (tipo.get() == TipoAfirmacion.JUICIO_DE_VALOR || tipo.get() == TipoAfirmacion.DEFINICION) {
            errores.add(new Validacion.Error("tipo", "La jerarquía de evidencia no aplica a un juicio de valor ni a una definición: no se verifican con fuentes."));
        }
        List<EvidenciaRegistrada> evidencias = conDescripcion(entrada);
        if (evidencias.isEmpty() || evidencias.size() > 8) {
            errores.add(new Validacion.Error("evidencias", "Registra de una a ocho evidencias."));
        }
        for (EvidenciaRegistrada e : evidencias) {
            if (tipoFuente(e.tipoFuente()).isEmpty() || postura(e.postura()).isEmpty()) {
                errores.add(new Validacion.Error("evidencias", "Cada evidencia necesita su tipo de fuente y su postura."));
                break;
            }
            if (!Textos.vacio(e.fecha())) {
                try {
                    LocalDate.parse(e.fecha());
                } catch (DateTimeParseException ex) {
                    errores.add(new Validacion.Error("evidencias", "La fecha de una evidencia va como AAAA-MM-DD."));
                    break;
                }
            }
            if (e.craap() != null && (e.craap() < 0 || e.craap() > 25)) {
                errores.add(new Validacion.Error("evidencias", "El puntaje CRAAP va de 0 a 25."));
                break;
            }
        }
        return new Validacion(errores);
    }

    @Override
    public Resultado<ResultadoJerarquia> ejecutar(Config config, Entrada entrada, Contexto ctx) {
        Validacion validacion = validar(config, entrada);
        if (!validacion.esValida()) {
            throw new IllegalArgumentException("Entrada inválida para " + ID + ": " + validacion.errores());
        }
        TipoAfirmacion tipo = tipoAfirmacion(entrada.tipo()).orElseThrow();
        boolean porDiseno = tipo == TipoAfirmacion.DATO_ESTADISTICO || tipo == TipoAfirmacion.CAUSAL;
        UUID afirmacionId = ctx.nuevoId().get();
        LocalDate hoy = ctx.reloj().hoy();
        R01FuerzaEvidencia.Parametros r01 = config.parametros();
        List<EvidenciaRegistrada> registradas = conDescripcion(entrada);
        List<ResultadoJerarquia.EvidenciaPesada> pesadas = new ArrayList<>();
        List<Evidencia> paraR02 = new ArrayList<>();
        for (int i = 0; i < registradas.size(); i++) {
            EvidenciaRegistrada e = registradas.get(i);
            Optional<Fuente.DisenoEstudio> diseno = diseno(e.diseno());
            Fuente fuente = new Fuente(null, e.descripcion().strip(), tipoFuente(e.tipoFuente()).orElseThrow(), diseno,
                    Textos.vacio(e.fecha()) ? Optional.empty() : Optional.of(LocalDate.parse(e.fecha())), Optional.empty(), e.independiente(),
                    e.original(), Optional.ofNullable(e.craap()));
            int fuerza = R01FuerzaEvidencia.fuerza(tipo, fuente, hoy, r01);
            Evidencia.Postura postura = postura(e.postura()).orElseThrow();
            paraR02.add(new Evidencia(null, afirmacionId, fuente, e.descripcion().strip(), postura, fuerza, Evidencia.EtiquetadaPor.USUARIO, true));
            String nivel = porDiseno ? diseno.map(NIVELES_DISENO::get).orElse(SIN_DISENO) : fuente.tipo().name().toLowerCase();
            pesadas.add(new ResultadoJerarquia.EvidenciaPesada("E" + (i + 1), e.descripcion().strip(), nivel, postura.name().toLowerCase(), fuerza));
        }
        List<String> nombres = new ArrayList<>(porDiseno ? List.copyOf(NIVELES_DISENO.values()) : List.of("primaria", "secundaria", "terciaria"));
        if (porDiseno && pesadas.stream().anyMatch(p -> p.nivel().equals(SIN_DISENO))) {
            nombres.add(SIN_DISENO);
        }
        List<ResultadoJerarquia.Nivel> niveles = nombres.stream()
                .map(n -> new ResultadoJerarquia.Nivel(n, pesadas.stream().filter(p -> p.nivel().equals(n)).map(ResultadoJerarquia.EvidenciaPesada::codigo).toList()))
                .toList();
        R02FuerzaNeta.FuerzaNeta neta = R02FuerzaNeta.neta(paraR02, R02FuerzaNeta.Parametros.v1());
        String magnitud = switch (neta.magnitud()) {
            case DEBIL -> "débil";
            case MEDIA -> "media";
            case FUERTE -> "fuerte";
        };
        String sentido = neta.aFavor() ? "a favor" : neta.enContra() ? "en contra" : "";
        String afirmacion = entrada.afirmacion().strip();
        List<Pendiente> pendientes = new ArrayList<>();
        if (niveles.getFirst().evidencias().isEmpty()) {
            pendientes.add(new Pendiente(TipoPendiente.VERIFICACION, Optional.of(afirmacionId), Optional.empty(),
                    (porDiseno ? "Buscar una revisión sistemática sobre: " : "Buscar una fuente primaria sobre: ") + afirmacion));
        }
        String netaTexto = neta.valor() > 0 ? "+" + neta.valor() : String.valueOf(neta.valor());
        String resumen = "Fuerza neta " + netaTexto + " · " + magnitud + (sentido.isEmpty() ? "" : " " + sentido) + " (R02) · "
                + Textos.contar(pesadas.size(), "evidencia", "evidencias") + ".";
        List<AfirmacionConRol> afirmaciones = List.of(new AfirmacionConRol(afirmacionId, afirmacion, tipo, RolAfirmacion.HIPOTESIS,
                SentidoAfirmacion.PRODUCIDA, OrigenAfirmacion.USUARIO));
        ResultadoJerarquia valor = new ResultadoJerarquia(afirmacion, tipo.enBaseDeDatos(), porDiseno, niveles, pesadas, neta.valor(), magnitud, sentido,
                resumen);
        return new Resultado<>(VERSION_ESQUEMA, valor, afirmaciones, pendientes, resumen);
    }

    private static List<EvidenciaRegistrada> conDescripcion(Entrada entrada) {
        return entrada.evidencias().stream().filter(e -> e != null && !Textos.vacio(e.descripcion())).toList();
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

    private static Optional<Evidencia.Postura> postura(String valor) {
        for (Evidencia.Postura p : Evidencia.Postura.values()) {
            if (p.name().equalsIgnoreCase(valor)) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }

    @Override
    public ResultadoJerarquia migrar(Json datosViejos, int desdeVersion) {
        throw new IllegalArgumentException(ID + " no tiene versiones anteriores a la " + VERSION_ESQUEMA
                + "; se pidió migrar desde la " + desdeVersion);
    }
}
