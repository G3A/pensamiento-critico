package pensamiento.nucleo;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Una propuesta del modelo local dentro de la entrada de una técnica. El modelo propone y nunca califica: la
 * propuesta tiene origen "modelo", nace sin adoptar y no cuenta hasta que la persona la adopta con una acción
 * explícita. Qué significan destino y valor lo decide cada técnica (una oración, un elemento, una fila).
 *
 * @param codigo  IA1, IA2… en el orden en que llegaron
 * @param destino a qué parte de la entrada se refiere (por ejemplo "2" para la oración 2, o "implicaciones")
 * @param rotulo  cómo se lee en pantalla, por ejemplo "Oración 2 · relación causal"
 * @param valor   lo propuesto: una etiqueta de un enum cerrado o un texto corto
 * @param porque  el "por qué" del modelo, para que la persona juzgue
 * @param modelo  registro de reproducibilidad (RNF-07), junto con digest y versión del prompt
 */
public record Propuesta(String codigo, String destino, String rotulo, String valor, String porque, boolean adoptada,
                        String modelo, String digest, String prompt) {

    /** Las que fija el adaptador de Ollama para toda llamada. */
    public static final double TEMPERATURA = 0.0;
    public static final long SEMILLA = 42L;

    private static final Pattern CODIGO = Pattern.compile("^IA\\d{1,2}$");

    public Propuesta {
        destino = destino == null ? "" : destino;
        rotulo = rotulo == null ? "" : rotulo;
        valor = valor == null ? "" : valor;
        porque = porque == null ? "" : porque;
        modelo = modelo == null ? "" : modelo;
        digest = digest == null ? "" : digest;
        prompt = prompt == null ? "" : prompt;
    }

    public static boolean codigoValido(String codigo) {
        return codigo != null && CODIGO.matcher(codigo).matches();
    }

    public static String codigo(int numero) {
        return "IA" + numero;
    }

    /** El siguiente número libre para un código IA, después de los que ya hay. */
    public static int siguiente(List<Propuesta> existentes) {
        int maximo = 0;
        for (Propuesta p : existentes) {
            if (codigoValido(p.codigo())) {
                maximo = Math.max(maximo, Integer.parseInt(p.codigo().substring(2)));
            }
        }
        return maximo + 1;
    }

    public Propuesta adoptadaYa() {
        return new Propuesta(codigo, destino, rotulo, valor, porque, true, modelo, digest, prompt);
    }

    /** El registro de reproducibilidad de la ejecución, con temperatura 0 y la semilla fija del adaptador. */
    public Ejecucion.RegistroModelo registro() {
        return new Ejecucion.RegistroModelo(modelo, digest, prompt, TEMPERATURA, SEMILLA);
    }

    /**
     * Marca adoptada la propuesta con ese código. Lanza IllegalArgumentException si no existe o ya estaba
     * adoptada: adoptar es una acción explícita, una vez por propuesta.
     */
    public static List<Propuesta> adoptar(List<Propuesta> propuestas, String codigo) {
        Propuesta elegida = buscar(propuestas, codigo);
        if (elegida.adoptada()) {
            throw new IllegalArgumentException("La propuesta " + codigo + " ya estaba adoptada");
        }
        return propuestas.stream().map(p -> p == elegida ? p.adoptadaYa() : p).toList();
    }

    public static Propuesta buscar(List<Propuesta> propuestas, String codigo) {
        return propuestas.stream().filter(p -> p.codigo().equals(codigo)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No hay una propuesta " + codigo));
    }
}
