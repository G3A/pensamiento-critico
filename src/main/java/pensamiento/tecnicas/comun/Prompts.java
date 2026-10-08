package pensamiento.tecnicas.comun;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Los prompts del modelo local viven versionados en el repositorio, en src/main/resources/prompts, no sueltos en el
 * código: "t34-steelman.v1.txt" es la versión 1 del prompt del steelman. Cada uno está en español y trae sus
 * ejemplos. Tiene dos partes separadas por la línea "=== pedido ===": el mensaje de sistema (la tarea, las reglas y
 * los ejemplos) y el pedido con los datos de la persona, donde las marcas {{nombre}} se reemplazan. La versión
 * ("t34-steelman.v1") queda guardada con cada ejecución (RNF-07).
 */
public final class Prompts {

    static final String SEPARADOR = "=== pedido ===";

    /** Un prompt leído: su versión y las plantillas del mensaje de sistema y del pedido. */
    public record Prompt(String version, String plantillaSistema, String plantillaPedido) {

        public String sistema(Map<String, String> valores) {
            return reemplazar(plantillaSistema, valores);
        }

        public String pedido(Map<String, String> valores) {
            return reemplazar(plantillaPedido, valores);
        }

        /** Cada {{clave}} reemplazada; una marca sin valor es un error del código, no de la persona. */
        private String reemplazar(String plantilla, Map<String, String> valores) {
            String texto = plantilla;
            for (Map.Entry<String, String> e : valores.entrySet()) {
                texto = texto.replace("{{" + e.getKey() + "}}", e.getValue() == null ? "" : e.getValue());
            }
            if (texto.contains("{{")) {
                throw new IllegalStateException("El prompt " + version + " tiene marcas sin reemplazar");
            }
            return texto;
        }
    }

    private static final Map<String, Prompt> LEIDOS = new ConcurrentHashMap<>();

    private Prompts() {
    }

    public static Prompt de(String nombre, int version) {
        return LEIDOS.computeIfAbsent(nombre + ".v" + version, Prompts::leer);
    }

    private static Prompt leer(String clave) {
        String ruta = "/prompts/" + clave + ".txt";
        try (InputStream in = Prompts.class.getResourceAsStream(ruta)) {
            if (in == null) {
                throw new IllegalStateException("No existe el prompt " + ruta);
            }
            String texto = new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
            int corte = texto.indexOf("\n" + SEPARADOR + "\n");
            if (corte < 0) {
                throw new IllegalStateException("El prompt " + ruta + " no tiene la línea " + SEPARADOR);
            }
            return new Prompt(clave, texto.substring(0, corte).strip(), texto.substring(corte + SEPARADOR.length() + 2).strip());
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el prompt " + ruta, e);
        }
    }
}
