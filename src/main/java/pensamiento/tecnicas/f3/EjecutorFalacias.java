package pensamiento.tecnicas.f3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import pensamiento.nucleo.ConModelo;
import pensamiento.nucleo.Contexto;
import pensamiento.nucleo.Ejecutor;
import pensamiento.nucleo.Esquema;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Json;
import pensamiento.nucleo.Pendiente;
import pensamiento.nucleo.Propuesta;
import pensamiento.nucleo.Resultado;
import pensamiento.nucleo.TipoPendiente;
import pensamiento.nucleo.Validacion;
import pensamiento.nucleo.puertos.Clasificacion;
import pensamiento.nucleo.puertos.RepositorioEsquemas;
import pensamiento.tecnicas.comun.ModeloLocal;
import pensamiento.tecnicas.comun.Prompts;

/**
 * T13 · Falacias como esquemas fallidos (Hamblin 1970; Walton 1995). Las reglas léxicas proponen el esquema y la
 * pregunta crítica sin responder; con "reglas y modelo", el modelo clasifica las oraciones que las reglas no
 * marcaron contra un enum cerrado de preguntas críticas, y su propuesta no cuenta hasta que la persona la adopta. La
 * etiqueta de falacia la confirma siempre la persona (R06). Las reglas de cálculo están en docs/ejemplos/T13.md.
 */
@Component
public class EjecutorFalacias implements Ejecutor<ConfigFalacias, EntradaFalacias, ResultadoFalacias>, ConModelo<ConfigFalacias, EntradaFalacias> {

    public static final IdTecnica ID = IdTecnica.de("T13");
    public static final int VERSION_ESQUEMA = 1;
    public static final int LARGO_TEXTO = 2000;
    public static final String PROMPT = "t13-esquema";
    public static final int VERSION_PROMPT = 1;
    public static final String NINGUNA = "ninguna";
    /** Decisión: a lo sumo 12 oraciones por pedido, para que la espera no pase de unos minutos en CPU. */
    public static final int ORACIONES_MAXIMAS = 12;

    private static final Pattern CODIGO = Pattern.compile("^(M|IA)\\d{1,2}$");

    private final RepositorioEsquemas esquemas;

    public EjecutorFalacias(RepositorioEsquemas esquemas) {
        this.esquemas = esquemas;
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
    public Tipos<ConfigFalacias, EntradaFalacias, ResultadoFalacias> tipos() {
        return new Tipos<>(ConfigFalacias.class, EntradaFalacias.class, ResultadoFalacias.class);
    }

    @Override
    public Validacion validar(ConfigFalacias config, EntradaFalacias entrada) {
        List<Validacion.Error> errores = new ArrayList<>();
        if (config.esquemas().isEmpty()) {
            errores.add(new Validacion.Error("esquemas", "Activa al menos un esquema."));
        }
        for (String id : config.esquemas()) {
            if (!ReglasFalacias.ESQUEMAS.contains(id) || esquemas.porId(id).isEmpty()) {
                errores.add(new Validacion.Error("esquemas", "El esquema «" + id + "» no está en el catálogo."));
                break;
            }
        }
        if (config.sensibilidad() == null) {
            errores.add(new Validacion.Error("sensibilidad", "Elige la sensibilidad."));
        }
        if (entrada.texto() == null || entrada.texto().isBlank()) {
            errores.add(new Validacion.Error("texto", "Escribe o pega el texto que quieres revisar."));
        } else if (entrada.texto().length() > LARGO_TEXTO) {
            errores.add(new Validacion.Error("texto", "Como máximo " + LARGO_TEXTO + " caracteres."));
        }
        for (String codigo : entrada.confirmadas()) {
            if (codigo == null || !CODIGO.matcher(codigo).matches()) {
                errores.add(new Validacion.Error("confirmadas", "Las confirmaciones son códigos de marca como M1 o IA1."));
                break;
            }
        }
        int oraciones = entrada.texto() == null ? 0 : ReglasFalacias.oraciones(entrada.texto()).size();
        for (Propuesta p : entrada.propuestas()) {
            if (!Propuesta.codigoValido(p.codigo()) || etiqueta(p.valor()).isEmpty() || numero(p.destino()) < 1 || numero(p.destino()) > oraciones) {
                errores.add(new Validacion.Error("propuestas", "Las propuestas del modelo no corresponden a este texto: vuelve a pedirlas."));
                break;
            }
        }
        return new Validacion(errores);
    }

    @Override
    public Resultado<ResultadoFalacias> ejecutar(ConfigFalacias config, EntradaFalacias entrada, Contexto ctx) {
        Validacion validacion = validar(config, entrada);
        if (!validacion.esValida()) {
            throw new IllegalArgumentException("Entrada inválida para " + ID + ": " + validacion.errores());
        }
        Set<String> confirmadas = new HashSet<>(entrada.confirmadas());
        List<ResultadoFalacias.Marca> marcas = new ArrayList<>();
        for (ReglasFalacias.Hallazgo h : ReglasFalacias.buscar(entrada.texto(), new HashSet<>(config.esquemas()))) {
            String codigo = "M" + (marcas.size() + 1);
            marcas.add(marca(codigo, h.oracion(), h.regla().esquema(), h.regla().pregunta(), h.porque(), confirmadas.contains(codigo), "reglas"));
        }
        List<ReglasFalacias.Oracion> oraciones = ReglasFalacias.oraciones(entrada.texto());
        for (Propuesta p : entrada.propuestas()) {
            if (p.adoptada()) {
                String[] e = etiqueta(p.valor()).orElseThrow();
                marcas.add(marca(p.codigo(), oraciones.get(numero(p.destino()) - 1), e[0], Integer.parseInt(e[1]),
                        "Según el modelo: " + p.porque(), confirmadas.contains(p.codigo()), "modelo"));
            }
        }
        marcas.sort(Comparator.comparingInt(ResultadoFalacias.Marca::inicio));
        List<Pendiente> pendientes = new ArrayList<>();
        for (ResultadoFalacias.Marca m : marcas) {
            if (!m.confirmada()) {
                pendientes.add(new Pendiente(TipoPendiente.REVISION, Optional.empty(), Optional.empty(),
                        "Responder la pregunta crítica de " + m.codigo() + " (" + m.esquemaNombre() + "): " + m.preguntaTexto()));
            }
        }
        int nConfirmadas = (int) marcas.stream().filter(ResultadoFalacias.Marca::confirmada).count();
        String resumen = resumen(marcas, nConfirmadas);
        ResultadoFalacias valor = new ResultadoFalacias(entrada.texto(), config.mostrarPregunta(), marcas, nConfirmadas,
                marcas.size() - nConfirmadas, resumen, entrada.propuestas());
        return new Resultado<>(VERSION_ESQUEMA, valor, List.of(), pendientes, resumen, List.of(), Resultado.registroDe(entrada.propuestas()),
                Optional.empty());
    }

    private ResultadoFalacias.Marca marca(String codigo, ReglasFalacias.Oracion oracion, String esquemaId, int numero, String porque,
                                          boolean confirmada, String origen) {
        Esquema esquema = esquemas.porId(esquemaId).orElseThrow();
        Esquema.PreguntaCritica pregunta = esquema.pregunta(numero).orElseThrow(
                () -> new IllegalStateException("El esquema " + esquema.id() + " no tiene la pregunta " + numero));
        ResultadoFalacias.Estado estado = confirmada ? ResultadoFalacias.Estado.CONFIRMADA : ResultadoFalacias.Estado.PROPUESTA;
        return new ResultadoFalacias.Marca(codigo, oracion.inicio(), oracion.fin(), oracion.texto(), esquema.id(), esquema.nombre(), pregunta.numero(),
                pregunta.texto(), pregunta.falacia(), pregunta.comoResponder(), porque, estado, origen);
    }

    @Override
    public ResultadoFalacias migrar(Json datosViejos, int desdeVersion) {
        throw new IllegalArgumentException(ID + " no tiene versiones anteriores a la " + VERSION_ESQUEMA
                + "; se pidió migrar desde la " + desdeVersion);
    }

    private static String resumen(List<ResultadoFalacias.Marca> marcas, int confirmadas) {
        if (marcas.isEmpty()) {
            return "Sin marcas: las reglas no reconocieron ningún esquema.";
        }
        int propuestas = marcas.size() - confirmadas;
        String inicio = marcas.size() + (marcas.size() == 1 ? " marca: " : " marcas: ");
        Set<String> etiquetas = new LinkedHashSet<>();
        marcas.stream().filter(ResultadoFalacias.Marca::confirmada).forEach(m -> etiquetas.add(m.falacia()));
        String falacias = switch (confirmadas) {
            case 0 -> "ninguna falacia confirmada";
            case 1 -> "1 falacia confirmada (" + String.join(", ", etiquetas) + ")";
            default -> confirmadas + " falacias confirmadas (" + String.join(", ", etiquetas) + ")";
        };
        String abiertas = switch (propuestas) {
            case 0 -> "ningún esquema con preguntas sin responder";
            case 1 -> "1 esquema con preguntas sin responder";
            default -> propuestas + " esquemas con preguntas sin responder";
        };
        return inicio + falacias + " y " + abiertas + ".";
    }

    // ---------------------------------------------------------------------------------------------
    // El modelo propone (RF-14)
    // ---------------------------------------------------------------------------------------------

    @Override
    public boolean usaModelo(ConfigFalacias config) {
        return config.sensibilidad() == ConfigFalacias.Sensibilidad.REGLAS_Y_MODELO;
    }

    @Override
    public List<Propuesta> propuestas(EntradaFalacias entrada) {
        return entrada.propuestas();
    }

    /** Las etiquetas del enum cerrado: "esquema:pregunta" por cada pregunta crítica de los esquemas activos, más "ninguna". */
    public List<String> etiquetas(ConfigFalacias config) {
        List<String> etiquetas = new ArrayList<>();
        for (String id : ReglasFalacias.ESQUEMAS) {
            if (config.esquemas().contains(id)) {
                esquemas.porId(id).ifPresent(e -> e.preguntas().forEach(p -> etiquetas.add(id + ":" + p.numero())));
            }
        }
        etiquetas.add(NINGUNA);
        return etiquetas;
    }

    /** El catálogo de esquemas y preguntas, en llano, para el prompt. */
    String catalogo(ConfigFalacias config) {
        StringBuilder sb = new StringBuilder();
        for (String id : ReglasFalacias.ESQUEMAS) {
            if (config.esquemas().contains(id)) {
                Esquema e = esquemas.porId(id).orElseThrow();
                for (Esquema.PreguntaCritica p : e.preguntas()) {
                    sb.append("- ").append(id).append(':').append(p.numero()).append(" · ").append(e.nombre()).append(": ")
                            .append(p.texto()).append(" (si queda sin responder: ").append(p.falacia()).append(")\n");
                }
            }
        }
        return sb.toString().strip();
    }

    /**
     * Una llamada por oración sin marca de las reglas (una tarea por llamada), en el orden del texto y a lo sumo
     * {@link #ORACIONES_MAXIMAS}. Cada etiqueta distinta de "ninguna" es una propuesta sin adoptar.
     */
    @Override
    public Propuestas proponer(ConfigFalacias config, EntradaFalacias entrada, Contexto ctx, Consumer<String> provisional, int primerNumero) {
        if (entrada.texto() == null || entrada.texto().isBlank()) {
            return Propuestas.cayo("Escribe primero el texto: el modelo clasifica sus oraciones.");
        }
        Set<Integer> conMarca = new HashSet<>();
        List<ReglasFalacias.Oracion> oraciones = ReglasFalacias.oraciones(entrada.texto());
        for (ReglasFalacias.Hallazgo h : ReglasFalacias.buscar(entrada.texto(), new HashSet<>(config.esquemas()))) {
            conMarca.add(oraciones.indexOf(h.oracion()));
        }
        List<Integer> aClasificar = new ArrayList<>();
        for (int i = 0; i < oraciones.size() && aClasificar.size() < ORACIONES_MAXIMAS; i++) {
            if (!conMarca.contains(i)) {
                aClasificar.add(i);
            }
        }
        Prompts.Prompt prompt = Prompts.de(PROMPT, VERSION_PROMPT);
        List<String> etiquetas = etiquetas(config);
        String sistema = prompt.sistema(Map.of("catalogo", catalogo(config)));
        return ModeloLocal.conCaida(ctx, ia -> {
            List<Propuesta> nuevas = new ArrayList<>();
            int hechas = 0;
            for (int i : aClasificar) {
                provisional.accept("Oración " + (i + 1) + " de " + oraciones.size() + "… ");
                Clasificacion c = ModeloLocal.clasificar(ia, sistema, prompt.pedido(Map.of("oracion", oraciones.get(i).texto())), etiquetas);
                hechas++;
                if (!NINGUNA.equals(c.etiqueta())) {
                    String[] e = etiqueta(c.etiqueta()).orElseThrow();
                    String nombre = esquemas.porId(e[0]).map(Esquema::nombre).orElse(e[0]);
                    nuevas.add(new Propuesta(Propuesta.codigo(primerNumero + nuevas.size()), String.valueOf(i + 1),
                            "Oración " + (i + 1) + " · " + nombre + ", pregunta " + e[1], c.etiqueta(), c.porQue(), false, c.modelo(), c.digest(),
                            prompt.version()));
                }
            }
            provisional.accept(pensamiento.tecnicas.comun.Textos.contar(hechas, "oración clasificada.", "oraciones clasificadas."));
            return nuevas;
        });
    }

    /** Adoptar vuelve la propuesta una marca más, con su código IA; para que cuente como falacia, además hay que confirmarla. */
    @Override
    public EntradaFalacias adoptar(EntradaFalacias entrada, String codigo) {
        return new EntradaFalacias(entrada.texto(), entrada.confirmadas(), Propuesta.adoptar(entrada.propuestas(), codigo));
    }

    /** "autoridad:2" → ["autoridad", "2"], si el esquema existe en las reglas y el número es una pregunta. */
    private Optional<String[]> etiqueta(String valor) {
        if (valor == null || !valor.matches("^[a-z_]+:\\d$")) {
            return Optional.empty();
        }
        String[] partes = valor.split(":");
        boolean existe = ReglasFalacias.ESQUEMAS.contains(partes[0])
                && esquemas.porId(partes[0]).flatMap(e -> e.pregunta(Integer.parseInt(partes[1]))).isPresent();
        return existe ? Optional.of(partes) : Optional.empty();
    }

    private static int numero(String texto) {
        try {
            return Integer.parseInt(texto);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
