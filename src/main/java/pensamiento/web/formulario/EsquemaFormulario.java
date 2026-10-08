package pensamiento.web.formulario;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import com.networknt.schema.path.NodePath;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import pensamiento.catalogo.MapeadorJson;

/**
 * Validación en el servidor: deriva un JSON Schema (draft 2020-12) de los campos y la configuración vigente,
 * lo valida con networknt json-schema-validator y traduce cada error a un mensaje en español junto a su campo,
 * con la misma ruta que usa el formulario ("evidencias[0].celdas[1]").
 */
public final class EsquemaFormulario {

    /** Tope de propuestas del modelo en una entrada: una por oración del texto más largo que se clasifica. */
    public static final int MAXIMO_PROPUESTAS = 40;

    private static final SchemaRegistry REGISTRO = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);

    private EsquemaFormulario() {
    }

    /** El JSON Schema que exigen estos campos con esta configuración. */
    public static ObjectNode esquema(List<Campo> campos, Map<String, Object> config) {
        ObjectNode raiz = MapeadorJson.mapper().createObjectNode();
        raiz.put("$schema", "https://json-schema.org/draft/2020-12/schema");
        objeto(raiz, campos, config);
        return raiz;
    }

    /** Errores por ruta de campo, en el orden del formulario; vacío si los valores cumplen el esquema. */
    public static Map<String, String> validar(List<Campo> campos, Map<String, Object> config, Map<String, Object> valores) {
        Schema schema = REGISTRO.getSchema(esquema(campos, config).toString());
        JsonNode instancia = MapeadorJson.mapper().valueToTree(valores);
        Map<String, String> errores = new LinkedHashMap<>();
        for (Error e : schema.validate(instancia)) {
            String ruta = ruta(e.getInstanceLocation());
            if ("required".equals(e.getKeyword()) && e.getProperty() != null) {
                ruta = ruta.isEmpty() ? e.getProperty() : ruta + "." + e.getProperty();
            }
            Campo campo = LenguajeCampos.buscar(campos, ruta.replaceAll("\\[\\d+]", ""));
            errores.putIfAbsent(ruta, mensaje(e.getKeyword(), campo, config));
        }
        return errores;
    }

    private static void objeto(ObjectNode nodo, List<Campo> campos, Map<String, Object> config) {
        nodo.put("type", "object");
        ObjectNode propiedades = nodo.putObject("properties");
        ArrayNode requeridos = nodo.putArray("required");
        for (Campo c : campos) {
            if (!c.visibleCon(config)) {
                continue;
            }
            propiedades.set(c.nombre(), propiedad(c, config));
            if (c.obligatorio() || (c.tipo() == Campo.Tipo.FILAS && c.minimo() != null && c.minimo() > 0)) {
                requeridos.add(c.nombre());
            }
        }
    }

    private static ObjectNode propiedad(Campo c, Map<String, Object> config) {
        ObjectNode p = MapeadorJson.mapper().createObjectNode();
        switch (c.tipo()) {
            case TEXTO -> {
                p.put("type", "string");
                if (c.obligatorio()) {
                    p.put("pattern", "\\S");
                }
                if (c.largoMaximo() != null) {
                    p.put("maxLength", c.largoMaximo());
                }
            }
            case FECHA -> p.put("type", "string").put("pattern", "^\\d{4}-\\d{2}-\\d{2}$");
            case OCULTO -> p.put("type", "string").put("maxLength", 200);
            case PROPUESTAS -> {
                p.put("type", "array").put("maxItems", MAXIMO_PROPUESTAS);
                ObjectNode item = p.putObject("items");
                item.put("type", "object");
                ObjectNode props = item.putObject("properties");
                props.putObject("codigo").put("type", "string").put("pattern", "^IA\\d{1,2}$");
                for (String sub : LectorFormulario.CAMPOS_PROPUESTA) {
                    if (!sub.equals("codigo")) {
                        props.putObject(sub).put("type", "string").put("maxLength", 1200);
                    }
                }
                props.putObject("adoptada").put("type", "boolean");
                item.putArray("required").add("codigo");
            }
            case ENTERO -> {
                p.put("type", "integer");
                if (c.minimo() != null) {
                    p.put("minimum", c.minimo());
                }
                if (c.maximo() != null) {
                    p.put("maximum", c.maximo());
                }
            }
            case BOOLEANO -> p.put("type", "boolean");
            case ENUMERACION -> {
                if (c.porCadaFilaDe() != null) {
                    p.put("type", "array");
                    enumeracion(p.putObject("items"), c, config);
                } else {
                    enumeracion(p, c, config);
                }
            }
            case CONJUNTO -> {
                p.put("type", "array").put("uniqueItems", true);
                enumeracion(p.putObject("items"), c, config);
            }
            case LISTA_ORDENADA -> {
                p.put("type", "array").put("uniqueItems", true);
                enumeracion(p.putObject("items"), c, config);
            }
            case FILAS -> {
                p.put("type", "array");
                if (c.minimo() != null) {
                    p.put("minItems", c.minimo());
                }
                Integer maximo = c.maximoCon(config);
                if (maximo != null) {
                    p.put("maxItems", maximo);
                }
                objeto(p.putObject("items"), c.campos(), config);
            }
        }
        return p;
    }

    private static void enumeracion(ObjectNode nodo, Campo c, Map<String, Object> config) {
        ArrayNode valores = nodo.putArray("enum");
        c.opcionesCon(config).forEach(o -> valores.add(o.valor()));
    }

    /** "$.evidencias[0].celdas[1]" pasa a "evidencias[0].celdas[1]". */
    static String ruta(NodePath camino) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < camino.getNameCount(); i++) {
            Object elemento = camino.getElement(i);
            if (elemento instanceof Integer indice) {
                sb.append('[').append(indice).append(']');
            } else {
                if (!sb.isEmpty()) {
                    sb.append('.');
                }
                sb.append(elemento);
            }
        }
        return sb.toString();
    }

    private static String mensaje(String palabra, Campo campo, Map<String, Object> config) {
        String etiqueta = campo == null ? "Este campo" : campo.etiqueta();
        if (campo != null && campo.porCadaFilaDe() != null) {
            return "Marca esta celda: elige una de las opciones.";
        }
        return switch (palabra) {
            case "required", "pattern" -> campo != null && campo.tipo() == Campo.Tipo.FILAS
                    ? "Agrega al menos " + campo.minimo() + "." : etiqueta + ": este campo es obligatorio.";
            case "maxLength" -> etiqueta + ": como máximo " + campo.largoMaximo() + " caracteres.";
            case "minItems" -> etiqueta + ": agrega al menos " + campo.minimo() + ".";
            case "maxItems" -> etiqueta + ": como máximo " + campo.maximoCon(config) + ".";
            case "minimum", "maximum" -> etiqueta + ": debe estar entre " + campo.minimo() + " y " + campo.maximo() + ".";
            case "type" -> campo != null && campo.tipo() == Campo.Tipo.ENTERO ? etiqueta + ": escribe un número entero." : etiqueta + ": valor no válido.";
            case "enum" -> etiqueta + ": elige una de las opciones.";
            default -> etiqueta + ": valor no válido.";
        };
    }
}
