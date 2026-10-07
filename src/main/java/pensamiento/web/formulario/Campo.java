package pensamiento.web.formulario;

import java.util.List;
import java.util.Map;

/**
 * Un campo del lenguaje de ocho tipos (sección 7, corrección 10). JTE es tipado y no recorre JSON Schema: el
 * esquema de configuración y de entrada de cada técnica se escribe con estos campos, la plantilla sabe pintar
 * cada tipo y el JSON Schema de validación se deriva de ellos.
 *
 * @param visibleSi        nombre de un booleano de la configuración: el campo solo existe si es verdadero
 * @param maximoDesdeConfig nombre de un entero de la configuración que fija el máximo de filas
 * @param porCadaFilaDe    para una enumeración dentro de filas: se repite una vez por cada fila de ese otro campo
 * @param opcionesSegun    nombre de una enumeración de la configuración que elige las opciones en opcionesPor
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
        List<Campo> campos) {

    /** Los ocho tipos de campo y cómo se pintan. */
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
        FECHA;

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

    /** Las opciones vigentes: las propias o, si dependen de la configuración, las del valor elegido. */
    public List<Opcion> opcionesCon(Map<String, Object> config) {
        if (opcionesSegun == null) {
            return opciones;
        }
        Object elegido = config.get(opcionesSegun);
        return opcionesPor.getOrDefault(elegido == null ? "" : elegido.toString(), List.of());
    }

    public boolean visibleCon(Map<String, Object> config) {
        return visibleSi == null || Boolean.TRUE.equals(config.get(visibleSi));
    }

    /** Máximo de filas o de valor: el propio o el que fija la configuración. */
    public Integer maximoCon(Map<String, Object> config) {
        if (maximoDesdeConfig != null && config.get(maximoDesdeConfig) instanceof Number n) {
            return n.intValue();
        }
        return maximo;
    }
}
