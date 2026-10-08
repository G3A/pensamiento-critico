package pensamiento.web.formulario;

import java.util.List;

/**
 * Lo que la plantilla de un campo necesita, ya resuelto: nombre del parámetro, identificador, valor, opciones
 * vigentes, error y, para filas, sus filas. Las plantillas no recorren esquemas: solo pintan esto.
 *
 * @param nombre     nombre del parámetro del formulario, por ejemplo "evidencias[0].celdas[1]"
 * @param id         identificador único en la página, derivado del formulario y del nombre
 * @param valores    para conjunto y lista ordenada, los valores elegidos en orden
 * @param accionUrl  a dónde van añadir, quitar, subir y bajar (hx-post que devuelve el formulario)
 * @param destino    selector del formulario que esas acciones reemplazan
 */
public record VistaCampo(
        Campo.Tipo tipo,
        String nombre,
        String id,
        String etiqueta,
        String ayuda,
        boolean obligatorio,
        String valor,
        List<String> valores,
        Integer minimo,
        Integer maximo,
        Integer largoMaximo,
        List<Campo.Opcion> opciones,
        String error,
        String campoBase,
        String prefijo,
        String elemento,
        List<Fila> filas,
        boolean puedeAnadir,
        String accionUrl,
        String destino,
        List<VistaPropuesta> propuestas) {

    /**
     * Una propuesta del modelo en el formulario: viaja en campos ocultos y se pinta con su rótulo, su por qué y si
     * está adoptada. Adoptar es un botón por propuesta (una acción explícita, nunca una casilla que se marca sola).
     */
    public record VistaPropuesta(int indice, String codigo, String destino, String rotulo, String valor, String porque, boolean adoptada,
                                 String modelo, String digest, String prompt) {
        /** El valor de un campo por su nombre, para escribirlo oculto en el formulario. */
        public String campo(String nombre) {
            return switch (nombre) {
                case "codigo" -> codigo;
                case "destino" -> destino;
                case "rotulo" -> rotulo;
                case "valor" -> valor;
                case "porque" -> porque;
                case "modelo" -> modelo;
                case "digest" -> digest;
                case "prompt" -> prompt;
                default -> throw new IllegalArgumentException("Campo de propuesta desconocido: " + nombre);
            };
        }
    }

    /** hx-vals de Adoptar: {"_accion":"adoptar:IA1"}. */
    public String accionAdoptar(String codigo) {
        return "{\"_accion\":\"adoptar:" + codigo + "\"}";
    }

    /** Una fila de una tabla de filas repetibles, con su código ("H1", "E2") y sus campos. */
    public record Fila(int indice, String codigo, List<VistaCampo> campos, boolean puedeQuitar) {
    }

    public boolean tieneError() {
        return error != null && !error.isBlank();
    }

    public boolean tieneAyuda() {
        return ayuda != null && !ayuda.isBlank();
    }

    public String idError() {
        return id + "-error";
    }

    public String idAyuda() {
        return id + "-ayuda";
    }

    /** Valor de aria-describedby: ayuda y error, si existen; nulo si no hay ninguno (JTE omite el atributo). */
    public String describedBy() {
        String ayudaId = tieneAyuda() ? idAyuda() : "";
        String errorId = tieneError() ? idError() : "";
        String valor = (ayudaId + " " + errorId).trim();
        return valor.isEmpty() ? null : valor;
    }

    /** hx-vals con la acción de filas, en JSON: {"_accion":"quitar:hipotesis:1"}. */
    public String accion(String verbo, int indice) {
        return "{\"_accion\":\"" + verbo + ":" + campoBase + (indice < 0 ? "" : ":" + indice) + "\"}";
    }

    /** Radios si hay 4 opciones o menos; select si hay más (sección 7). */
    public boolean comoRadios() {
        return opciones.size() <= 4;
    }

    /** Texto largo como textarea. */
    public boolean comoTextarea() {
        return largoMaximo != null && largoMaximo > Campo.LARGO_TEXTAREA;
    }

    public boolean marcado() {
        return "true".equals(valor);
    }

    public boolean elegido(String opcion) {
        return opcion.equals(valor) || valores.contains(opcion);
    }

    /** Etiqueta de una opción por su valor, para la lista ordenada. */
    public String etiquetaDe(String opcion) {
        return opciones.stream().filter(o -> o.valor().equals(opcion)).map(Campo.Opcion::etiqueta).findFirst().orElse(opcion);
    }
}
