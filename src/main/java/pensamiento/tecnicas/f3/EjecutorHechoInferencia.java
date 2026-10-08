package pensamiento.tecnicas.f3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import org.springframework.stereotype.Component;

import pensamiento.nucleo.AfirmacionConRol;
import pensamiento.nucleo.ConModelo;
import pensamiento.nucleo.Contexto;
import pensamiento.nucleo.Ejecutor;
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
import pensamiento.tecnicas.comun.ModeloLocal;
import pensamiento.tecnicas.comun.Prompts;
import pensamiento.tecnicas.comun.Textos;

/**
 * T17 · Hecho, inferencia, juicio (Hayakawa 1939). La persona etiqueta cada oración con uno de los ocho tipos de
 * afirmación; los tipos se agrupan en hecho, inferencia y juicio, y la tarjeta dice qué se puede hacer con cada una.
 * El modelo puede proponer el tipo de las oraciones sin etiquetar; no cuenta hasta adoptarse. Reglas en
 * docs/ejemplos/T17.md.
 */
@Component
public class EjecutorHechoInferencia implements Ejecutor<EjecutorHechoInferencia.Config, EjecutorHechoInferencia.Entrada, ResultadoHechoInferencia>,
        ConModelo<EjecutorHechoInferencia.Config, EjecutorHechoInferencia.Entrada> {

    public static final IdTecnica ID = IdTecnica.de("T17");
    public static final int VERSION_ESQUEMA = 1;
    public static final String PROMPT = "t17-tipo";
    public static final int VERSION_PROMPT = 1;
    public static final String SIN_ETIQUETAR = "sin_etiquetar";

    public enum Modo {
        MANUAL, MANUAL_Y_MODELO;

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    /** Configuración de T17, versión de esquema 1: los tipos activos, en minúscula ("dato_estadistico"). */
    public record Config(List<String> tipos, Modo modo) {
        public Config {
            tipos = tipos == null ? List.of() : List.copyOf(tipos);
        }
    }

    /** @param origen "modelo" si el tipo vino de una propuesta adoptada */
    public record Oracion(String texto, String tipo, String origen) {
    }

    public record Entrada(List<Oracion> oraciones, List<Propuesta> propuestas) {
        public Entrada {
            oraciones = oraciones == null ? List.of() : List.copyOf(oraciones);
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
    public Tipos<Config, Entrada, ResultadoHechoInferencia> tipos() {
        return new Tipos<>(Config.class, Entrada.class, ResultadoHechoInferencia.class);
    }

    @Override
    public Validacion validar(Config config, Entrada entrada) {
        List<Validacion.Error> errores = new ArrayList<>();
        if (config.tipos().isEmpty() || config.tipos().stream().anyMatch(t -> tipo(t).isEmpty())) {
            errores.add(new Validacion.Error("config.tipos", "Activa al menos uno de los ocho tipos."));
        }
        if (config.modo() == null) {
            errores.add(new Validacion.Error("config.modo", "Elige el modo."));
        }
        List<Oracion> conTexto = conTexto(entrada);
        if (conTexto.isEmpty() || conTexto.size() > 12) {
            errores.add(new Validacion.Error("oraciones", "Escribe de una a doce oraciones, una por fila."));
        }
        for (Oracion o : conTexto) {
            if (o.tipo() != null && !SIN_ETIQUETAR.equals(o.tipo()) && !config.tipos().contains(o.tipo())) {
                errores.add(new Validacion.Error("oraciones", "Una oración tiene un tipo que no está activo en tu configuración."));
                break;
            }
        }
        for (Propuesta p : entrada.propuestas()) {
            if (!Propuesta.codigoValido(p.codigo()) || tipo(p.valor()).isEmpty() || numero(p.destino()) < 1 || numero(p.destino()) > conTexto.size()) {
                errores.add(new Validacion.Error("propuestas", "Las propuestas del modelo no corresponden a estas oraciones: vuelve a pedirlas."));
                break;
            }
        }
        return new Validacion(errores);
    }

    @Override
    public Resultado<ResultadoHechoInferencia> ejecutar(Config config, Entrada entrada, Contexto ctx) {
        Validacion validacion = validar(config, entrada);
        if (!validacion.esValida()) {
            throw new IllegalArgumentException("Entrada inválida para " + ID + ": " + validacion.errores());
        }
        List<Oracion> oraciones = conTexto(entrada);
        StringBuilder texto = new StringBuilder();
        List<ResultadoHechoInferencia.OracionEtiquetada> etiquetadas = new ArrayList<>();
        List<AfirmacionConRol> afirmaciones = new ArrayList<>();
        List<Pendiente> pendientes = new ArrayList<>();
        int hechos = 0;
        int inferencias = 0;
        int juicios = 0;
        int sinEtiquetar = 0;
        for (int i = 0; i < oraciones.size(); i++) {
            Oracion o = oraciones.get(i);
            String t = o.texto().strip();
            if (!texto.isEmpty()) {
                texto.append(' ');
            }
            int inicio = texto.length();
            texto.append(t);
            Optional<TipoAfirmacion> tipo = SIN_ETIQUETAR.equals(o.tipo()) || o.tipo() == null ? Optional.empty() : tipo(o.tipo());
            boolean delModelo = tipo.isPresent() && "modelo".equals(o.origen());
            String linea;
            String grupo = null;
            if (tipo.isEmpty()) {
                sinEtiquetar++;
                linea = "Oración " + (i + 1) + " · sin etiquetar: elige un tipo o pide una propuesta.";
            } else {
                grupo = grupo(tipo.get());
                switch (grupo) {
                    case "hecho" -> hechos++;
                    case "inferencia" -> inferencias++;
                    default -> juicios++;
                }
                linea = "Oración " + (i + 1) + " · " + nombre(tipo.get()) + " (" + grupo + "): " + queDice(grupo) + ".";
                afirmaciones.add(new AfirmacionConRol(ctx.nuevoId().get(), t, tipo.get(), RolAfirmacion.PREMISA, SentidoAfirmacion.PRODUCIDA,
                        delModelo ? OrigenAfirmacion.MODELO : OrigenAfirmacion.USUARIO, true));
                if ("inferencia".equals(grupo)) {
                    pendientes.add(new Pendiente(TipoPendiente.VERIFICACION, Optional.empty(), Optional.empty(), "Buscar evidencia para la inferencia: " + t));
                }
            }
            etiquetadas.add(new ResultadoHechoInferencia.OracionEtiquetada(i + 1, t, tipo.map(TipoAfirmacion::enBaseDeDatos).orElse(null), grupo, linea,
                    delModelo, inicio, texto.length()));
        }
        List<String> partes = new ArrayList<>();
        if (hechos > 0) {
            partes.add(Textos.contar(hechos, "hecho", "hechos"));
        }
        if (inferencias > 0) {
            partes.add(Textos.contar(inferencias, "inferencia", "inferencias"));
        }
        if (juicios > 0) {
            partes.add(Textos.contar(juicios, "juicio", "juicios"));
        }
        if (sinEtiquetar > 0 && !partes.isEmpty()) {
            partes.add(sinEtiquetar + " sin etiquetar");
        }
        String cuantas = Textos.contar(oraciones.size(), "oración", "oraciones");
        String resumen = partes.isEmpty() ? cuantas + " sin etiquetar." : cuantas + ": " + Textos.enumerar(partes) + ".";
        ResultadoHechoInferencia valor = new ResultadoHechoInferencia(texto.toString(), etiquetadas, entrada.propuestas(), resumen);
        return new Resultado<>(VERSION_ESQUEMA, valor, afirmaciones, pendientes, resumen, List.of(), Resultado.registroDe(entrada.propuestas()),
                Optional.empty());
    }

    @Override
    public ResultadoHechoInferencia migrar(Json datosViejos, int desdeVersion) {
        throw new IllegalArgumentException(ID + " no tiene versiones anteriores a la " + VERSION_ESQUEMA
                + "; se pidió migrar desde la " + desdeVersion);
    }

    // ---------------------------------------------------------------------------------------------
    // El modelo propone (RF-14)
    // ---------------------------------------------------------------------------------------------

    @Override
    public boolean usaModelo(Config config) {
        return config.modo() == Modo.MANUAL_Y_MODELO;
    }

    @Override
    public List<Propuesta> propuestas(Entrada entrada) {
        return entrada.propuestas();
    }

    /** Una llamada por oración sin etiquetar, contra el enum de los tipos activos. */
    @Override
    public Propuestas proponer(Config config, Entrada entrada, Contexto ctx, Consumer<String> provisional, int primerNumero) {
        List<Oracion> oraciones = conTexto(entrada);
        List<Integer> sinTipo = new ArrayList<>();
        for (int i = 0; i < oraciones.size(); i++) {
            if (oraciones.get(i).tipo() == null || SIN_ETIQUETAR.equals(oraciones.get(i).tipo())) {
                sinTipo.add(i);
            }
        }
        if (sinTipo.isEmpty()) {
            return Propuestas.de(List.of());
        }
        Prompts.Prompt prompt = Prompts.de(PROMPT, VERSION_PROMPT);
        return ModeloLocal.conCaida(ctx, ia -> {
            List<Propuesta> nuevas = new ArrayList<>();
            for (int i : sinTipo) {
                provisional.accept("Oración " + (i + 1) + "… ");
                String texto = oraciones.get(i).texto().strip();
                Clasificacion c = ModeloLocal.clasificar(ia, prompt.sistema(Map.of()), prompt.pedido(Map.of("oracion", texto)), config.tipos(), nuevas.isEmpty());
                nuevas.add(new Propuesta(Propuesta.codigo(primerNumero + nuevas.size()), String.valueOf(i + 1),
                        "Oración " + (i + 1) + " · " + nombre(tipo(c.etiqueta()).orElseThrow()), c.etiqueta(), c.porQue(), false, c.modelo(), c.digest(),
                        prompt.version()));
            }
            return nuevas;
        });
    }

    /** Adoptar pone el tipo propuesto en la oración, con origen modelo. */
    @Override
    public Entrada adoptar(Entrada entrada, String codigo) {
        List<Propuesta> propuestas = Propuesta.adoptar(entrada.propuestas(), codigo);
        Propuesta p = Propuesta.buscar(propuestas, codigo);
        List<Oracion> oraciones = new ArrayList<>(conTexto(entrada));
        int indice = numero(p.destino()) - 1;
        if (indice < 0 || indice >= oraciones.size()) {
            throw new IllegalArgumentException("La propuesta " + codigo + " no corresponde a estas oraciones");
        }
        oraciones.set(indice, new Oracion(oraciones.get(indice).texto(), p.valor(), "modelo"));
        return new Entrada(oraciones, propuestas);
    }

    private static List<Oracion> conTexto(Entrada entrada) {
        return entrada.oraciones().stream().filter(o -> o != null && !Textos.vacio(o.texto())).toList();
    }

    static Optional<TipoAfirmacion> tipo(String valor) {
        if (valor == null) {
            return Optional.empty();
        }
        for (TipoAfirmacion t : TipoAfirmacion.values()) {
            if (t.enBaseDeDatos().equals(valor)) {
                return Optional.of(t);
            }
        }
        return Optional.empty();
    }

    static String grupo(TipoAfirmacion t) {
        return switch (t) {
            case HECHO, DATO_ESTADISTICO, TESTIMONIO -> "hecho";
            case CAUSAL, GENERALIZACION, PREDICCION -> "inferencia";
            case JUICIO_DE_VALOR, DEFINICION -> "juicio";
        };
    }

    static String nombre(TipoAfirmacion t) {
        return switch (t) {
            case HECHO -> "hecho";
            case DATO_ESTADISTICO -> "dato estadístico";
            case TESTIMONIO -> "testimonio";
            case CAUSAL -> "relación causal";
            case GENERALIZACION -> "generalización";
            case PREDICCION -> "predicción";
            case JUICIO_DE_VALOR -> "juicio de valor";
            case DEFINICION -> "definición";
        };
    }

    private static String queDice(String grupo) {
        return switch (grupo) {
            case "hecho" -> "puede verificarse";
            case "inferencia" -> "necesita evidencia";
            default -> "no es verificable";
        };
    }

    private static int numero(String texto) {
        try {
            return Integer.parseInt(texto);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
