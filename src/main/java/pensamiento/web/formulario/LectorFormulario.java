package pensamiento.web.formulario;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Convierte los parámetros de un formulario (nombres como "evidencias[1].celdas[2]") en un árbol de mapas y
 * listas según el lenguaje de campos, y aplica las acciones de filas: añadir, quitar, subir y bajar.
 */
public final class LectorFormulario {

    /** Los campos de texto de cada propuesta del modelo; "adoptada" es booleano. */
    public static final List<String> CAMPOS_PROPUESTA = List.of("codigo", "destino", "rotulo", "valor", "porque", "modelo", "digest", "prompt");

    private LectorFormulario() {
    }

    public static Map<String, Object> leer(List<Campo> campos, Map<String, List<String>> parametros, String prefijo) {
        Map<String, Object> valores = new LinkedHashMap<>();
        leerNivel(campos, parametros, prefijo, valores, valores);
        igualarCeldas(campos, valores);
        return valores;
    }

    /** Cada lista que se repite por las filas de otro campo queda con una posición por fila: nulos si faltan, sin sobrantes. */
    @SuppressWarnings("unchecked")
    public static void igualarCeldas(List<Campo> campos, Map<String, Object> valores) {
        for (Campo filas : campos) {
            if (filas.tipo() != Campo.Tipo.FILAS || !(valores.get(filas.nombre()) instanceof List<?> lista)) {
                continue;
            }
            for (Campo sub : filas.campos()) {
                if (sub.porCadaFilaDe() == null) {
                    continue;
                }
                int n = tamanio(valores.get(sub.porCadaFilaDe()));
                for (Object fila : lista) {
                    if (fila instanceof Map<?, ?> mapa) {
                        List<Object> celdas = (List<Object>) ((Map<String, Object>) mapa).computeIfAbsent(sub.nombre(), k -> new ArrayList<>());
                        while (celdas.size() < n) {
                            celdas.add(null);
                        }
                        while (celdas.size() > n) {
                            celdas.removeLast();
                        }
                    }
                }
            }
        }
    }

    private static void leerNivel(List<Campo> campos, Map<String, List<String>> p, String prefijo, Map<String, Object> destino, Map<String, Object> raiz) {
        for (Campo c : campos) {
            String nombre = prefijo + c.nombre();
            switch (c.tipo()) {
                case TEXTO, FECHA, OCULTO -> primero(p, nombre).filter(v -> !v.isBlank()).ifPresent(v -> destino.put(c.nombre(), v));
                case ENUMERACION -> {
                    if (c.porCadaFilaDe() != null) {
                        destino.put(c.nombre(), listaIndexada(p, nombre));
                    } else {
                        primero(p, nombre).filter(v -> !v.isBlank()).ifPresent(v -> destino.put(c.nombre(), v));
                    }
                }
                case ENTERO -> primero(p, nombre).filter(v -> !v.isBlank()).ifPresent(v -> destino.put(c.nombre(), entero(v)));
                case BOOLEANO -> destino.put(c.nombre(), p.getOrDefault(nombre, List.of()).contains("true"));
                case CONJUNTO -> destino.put(c.nombre(), new ArrayList<>(p.getOrDefault(nombre, List.of()).stream().filter(v -> !v.isBlank()).toList()));
                case LISTA_ORDENADA -> destino.put(c.nombre(), listaIndexada(p, nombre));
                case FILAS -> {
                    List<Object> filas = new ArrayList<>();
                    for (int i : indices(p, nombre)) {
                        Map<String, Object> fila = new LinkedHashMap<>();
                        leerNivel(c.campos(), p, nombre + "[" + i + "].", fila, raiz);
                        filas.add(fila);
                    }
                    destino.put(c.nombre(), filas);
                }
                case PROPUESTAS -> {
                    List<Object> propuestas = new ArrayList<>();
                    for (int i : indices(p, nombre)) {
                        String base = nombre + "[" + i + "].";
                        Map<String, Object> propuesta = new LinkedHashMap<>();
                        for (String sub : CAMPOS_PROPUESTA) {
                            propuesta.put(sub, primero(p, base + sub).orElse(""));
                        }
                        propuesta.put("adoptada", p.getOrDefault(base + "adoptada", List.of()).contains("true"));
                        propuestas.add(propuesta);
                    }
                    destino.put(c.nombre(), propuestas);
                }
            }
        }
    }

    /** "x[0]", "x[1]"... en orden de índice; los huecos quedan como nulos. */
    private static List<Object> listaIndexada(Map<String, List<String>> p, String nombre) {
        List<Object> lista = new ArrayList<>();
        Pattern patron = Pattern.compile("^" + Pattern.quote(nombre) + "\\[(\\d+)]$");
        Map<Integer, String> porIndice = new java.util.TreeMap<>();
        for (Map.Entry<String, List<String>> e : p.entrySet()) {
            Matcher m = patron.matcher(e.getKey());
            if (m.matches() && !e.getValue().isEmpty()) {
                porIndice.put(Integer.parseInt(m.group(1)), e.getValue().getLast());
            }
        }
        int tope = porIndice.isEmpty() ? -1 : ((java.util.TreeMap<Integer, String>) porIndice).lastKey();
        for (int i = 0; i <= Math.min(tope, 63); i++) {
            String v = porIndice.get(i);
            lista.add(v == null || v.isBlank() ? null : v);
        }
        return lista;
    }

    /** Índices de las filas presentes: "hipotesis[3].texto" aporta el 3. Como máximo 64 filas. */
    private static TreeSet<Integer> indices(Map<String, List<String>> p, String nombre) {
        TreeSet<Integer> indices = new TreeSet<>();
        Pattern patron = Pattern.compile("^" + Pattern.quote(nombre) + "\\[(\\d{1,2})]\\..+$");
        for (String clave : p.keySet()) {
            Matcher m = patron.matcher(clave);
            if (m.matches()) {
                indices.add(Integer.parseInt(m.group(1)));
            }
        }
        while (indices.size() > 64) {
            indices.pollLast();
        }
        return indices;
    }

    private static java.util.Optional<String> primero(Map<String, List<String>> p, String nombre) {
        List<String> v = p.get(nombre);
        return v == null || v.isEmpty() ? java.util.Optional.empty() : java.util.Optional.ofNullable(v.getLast()).map(String::trim);
    }

    /** Un número entero si se puede leer; si no, el texto tal cual para que la validación lo señale. */
    private static Object entero(String v) {
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return v;
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Acciones de filas y listas
    // ------------------------------------------------------------------------------------------------

    /** Aplica "anadir:campo", "quitar:campo:indice", "subir:campo:indice" o "bajar:campo:indice". Ignora lo desconocido. */
    @SuppressWarnings("unchecked")
    public static void aplicar(String accion, List<Campo> campos, Map<String, Object> valores, Map<String, Object> config) {
        if (accion == null || accion.isBlank()) {
            return;
        }
        String[] partes = accion.split(":");
        if (partes.length < 2) {
            return;
        }
        Campo campo = campos.stream().filter(c -> c.nombre().equals(partes[1])).findFirst().orElse(null);
        if (campo == null) {
            return;
        }
        List<Object> lista = (List<Object>) valores.computeIfAbsent(campo.nombre(), k -> new ArrayList<>());
        int indice = partes.length > 2 ? indiceSeguro(partes[2]) : -1;
        switch (partes[0]) {
            case "anadir" -> {
                if (campo.tipo() != Campo.Tipo.FILAS) {
                    return;
                }
                Integer maximo = campo.maximoCon(config);
                if (maximo != null && lista.size() >= maximo) {
                    return;
                }
                Map<String, Object> nueva = new LinkedHashMap<>();
                for (Campo sub : campo.campos()) {
                    if (sub.porCadaFilaDe() != null) {
                        List<Object> celdas = new ArrayList<>();
                        for (int i = 0; i < tamanio(valores.get(sub.porCadaFilaDe())); i++) {
                            celdas.add(null);
                        }
                        nueva.put(sub.nombre(), celdas);
                    }
                }
                lista.add(nueva);
                paraCadaDependiente(campos, campo.nombre(), valores, celdas -> celdas.add(null));
            }
            case "quitar" -> {
                if (campo.tipo() != Campo.Tipo.FILAS || indice < 0 || indice >= lista.size()) {
                    return;
                }
                lista.remove(indice);
                paraCadaDependiente(campos, campo.nombre(), valores, celdas -> {
                    if (indice < celdas.size()) {
                        celdas.remove(indice);
                    }
                });
            }
            case "subir", "bajar" -> {
                int otro = partes[0].equals("subir") ? indice - 1 : indice + 1;
                if (campo.tipo() == Campo.Tipo.LISTA_ORDENADA && indice >= 0 && otro >= 0 && indice < lista.size() && otro < lista.size()) {
                    java.util.Collections.swap(lista, indice, otro);
                }
            }
            default -> {
                // acción desconocida: no hace nada
            }
        }
    }

    /** Para cada enumeración que se repite por cada fila de "origen", aplica la operación a su lista de celdas. */
    @SuppressWarnings("unchecked")
    private static void paraCadaDependiente(List<Campo> campos, String origen, Map<String, Object> valores, java.util.function.Consumer<List<Object>> operacion) {
        for (Campo filas : campos) {
            if (filas.tipo() != Campo.Tipo.FILAS) {
                continue;
            }
            for (Campo sub : filas.campos()) {
                if (origen.equals(sub.porCadaFilaDe()) && valores.get(filas.nombre()) instanceof List<?> lista) {
                    for (Object fila : lista) {
                        if (fila instanceof Map<?, ?> mapa) {
                            Object celdas = ((Map<String, Object>) mapa).computeIfAbsent(sub.nombre(), k -> new ArrayList<>());
                            operacion.accept((List<Object>) celdas);
                        }
                    }
                }
            }
        }
    }

    static int tamanio(Object lista) {
        return lista instanceof List<?> l ? l.size() : 0;
    }

    private static int indiceSeguro(String texto) {
        try {
            return Integer.parseInt(texto);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
