package pensamiento.web.formulario;

import java.util.List;
import java.util.Map;

/**
 * Un campo del lenguaje de ocho tipos (sección 7, corrección 10). JTE es tipado y no recorre JSON Schema: el
 * esquema de configuración y de entrada de cada técnica se escribe con estos campos, la plantilla sabe pintar
 * cada tipo y el JSON Schema de validación se deriva de ellos.
 *
 * @param visibleSi        nombre de un booleano de la configuración (el campo solo existe si es verdadero),
 *                         "campo=valor" para una enumeración (solo existe con ese valor) o "campo~valor" para un conjunto
 *                         (solo existe si el conjunto lo contiene)
 * @param maximoDesdeConfig nombre de un entero de la configuración que fija el máximo de filas
 * @param porCadaFilaDe    para una enumeración dentro de filas: se repite una vez por cada fila de ese otro campo
 * @param opcionesSegun    nombre de una enumeración de la configuración que elige las opciones en opcionesPor
 * @param opcionesDesde    nombre de un campo de la configuración que fija las opciones: si es una lista (un conjunto), solo
 *                         quedan esas opciones; si es un texto, cada parte separada por comas es una opción
 */
public record Campo(
        String nombre,
        Tipo tipo,
        String etiqueta,
        String ayuda,
        boolean obligatorio,
        Integer minimo,
        Integer maximo,
        Integer largoMaximo,
        List<Opcion> opciones,
        String prefijo,
        String elemento,
        String visibleSi,
        String maximoDesdeConfig,
        String porCadaFilaDe,
        String opcionesSegun,
        Map<String, List<Opcion>> opcionesPor,
        String opcionesDesde,
        List<Campo> campos) {

    /**
     * Los ocho tipos de campo de la sección 7 y cómo se pintan, más dos del hito 3 para el modelo local: oculto (el
     * origen de lo adoptado viaja sin mostrarse) y propuestas (la lista de propuestas del modelo con Adoptar).
     */
    public enum Tipo {
        /** Interruptor con etiqueta. */
        BOOLEANO,
        /** number más range, con el valor visible. */
        ENTERO,
        /** Radios si hay 4 opciones o menos; select si hay más. */
        ENUMERACION,
        /** Casillas. */
        CONJUNTO,
        /** Lista con botones subir y bajar, sin arrastre. */
        LISTA_ORDENADA,
        /** Tabla de filas; añadir y quitar por fila. */
        FILAS,
        /** input o textarea según la longitud máxima. */
        TEXTO,
        /** Campo de fecha nativo. */
        FECHA,
        /** input hidden: viaja con el formulario sin mostrarse, por ejemplo el origen "modelo" de una fila adoptada. */
        OCULTO,
        /** Propuestas del modelo: cada una con su rótulo, su por qué, si está adoptada y el botón Adoptar. */
        PROPUESTAS;

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    public record Opcion(String valor, String etiqueta) {
    }

    /** A partir de este largo máximo, el texto se pinta como textarea. */
    public static final int LARGO_TEXTAREA = 160;

    public Campo {
        opciones = opciones == null ? List.of() : List.copyOf(opciones);
        opcionesPor = opcionesPor == null ? Map.of() : Map.copyOf(opcionesPor);
        campos = campos == null ? List.of() : List.copyOf(campos);
        etiqueta = etiqueta == null ? nombre : etiqueta;
    }

    /**
     * Las opciones vigentes: las propias o, si dependen de la configuración, las del valor elegido; con opcionesDesde, las
     * que la configuración deja (un conjunto) o las que escribe (un texto separado por comas).
     */
    public List<Opcion> opcionesCon(Map<String, Object> config) {
        if (opcionesDesde != null) {
            Object desde = config.get(opcionesDesde);
            if (desde instanceof List<?> lista) {
                List<String> elegidos = lista.stream().map(String::valueOf).toList();
                return opciones.stream().filter(o -> elegidos.contains(o.valor())).toList();
            }
            return desde == null ? List.of() : partes(desde.toString()).stream().map(t -> new Opcion(t, t)).toList();
        }
        if (opcionesSegun == null) {
            return opciones;
        }
        Object elegido = config.get(opcionesSegun);
        return opcionesPor.getOrDefault(elegido == null ? "" : elegido.toString(), List.of());
    }

    /** Visible si el booleano visibleSi es verdadero, o si la configuración tiene ese valor ("modo=manual_y_modelo"). */
    public boolean visibleCon(Map<String, Object> config) {
        if (visibleSi == null) {
            return true;
        }
        int contiene = visibleSi.indexOf('~');
        if (contiene > 0) {
            return config.get(visibleSi.substring(0, contiene)) instanceof List<?> lista
                    && lista.stream().map(String::valueOf).anyMatch(v -> v.equals(visibleSi.substring(contiene + 1)));
        }
        int igual = visibleSi.indexOf('=');
        if (igual > 0) {
            Object valor = config.get(visibleSi.substring(0, igual));
            return valor != null && valor.toString().equals(visibleSi.substring(igual + 1));
        }
        return Boolean.TRUE.equals(config.get(visibleSi));
    }

    /** "Máquina, Método,  Material" pasa a [Máquina, Método, Material]: sin vacíos ni repetidos, en su orden. */
    public static List<String> partes(String texto) {
        return java.util.Arrays.stream(texto.split(",")).map(String::strip).filter(s -> !s.isEmpty()).distinct().toList();
    }

    /** Máximo de filas o de valor: el propio o el que fija la configuración. */
    public Integer maximoCon(Map<String, Object> config) {
        if (maximoDesdeConfig != null && config.get(maximoDesdeConfig) instanceof Number n) {
            return n.intValue();
        }
        return maximo;
    }
}
