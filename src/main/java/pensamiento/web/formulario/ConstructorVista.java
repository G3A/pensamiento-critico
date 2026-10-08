package pensamiento.web.formulario;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Arma las vistas de los campos a partir de campos, valores, configuración y errores. */
public final class ConstructorVista {

    private final String idFormulario;
    private final Map<String, Object> config;
    private final Map<String, String> errores;
    private final String accionUrl;

    /**
     * @param idFormulario prefijo de identificadores, por ejemplo "T28" o "cfg-T28"
     * @param accionUrl    hx-post de añadir, quitar, subir y bajar; vacío si el formulario no tiene acciones
     */
    public ConstructorVista(String idFormulario, Map<String, Object> config, Map<String, String> errores, String accionUrl) {
        this.idFormulario = idFormulario;
        this.config = config;
        this.errores = errores;
        this.accionUrl = accionUrl;
    }

    public List<VistaCampo> construir(List<Campo> campos, Map<String, Object> valores, String prefijoNombre, String prefijoParametro) {
        List<VistaCampo> vistas = new ArrayList<>();
        for (Campo c : campos) {
            if (!c.visibleCon(config)) {
                continue;
            }
            String ruta = prefijoNombre + c.nombre();
            Object valor = valores.get(c.nombre());
            if (c.tipo() == Campo.Tipo.FILAS) {
                vistas.add(filas(c, ruta, valor, valores, prefijoParametro));
            } else if (c.tipo() == Campo.Tipo.PROPUESTAS) {
                vistas.add(propuestas(c, ruta, valor, prefijoParametro));
            } else if (c.porCadaFilaDe() != null) {
                vistas.addAll(celdas(c, ruta, valor, prefijoParametro));
            } else {
                vistas.add(simple(c, ruta, c.etiqueta(), valor, prefijoParametro));
            }
        }
        return vistas;
    }

    private VistaCampo simple(Campo c, String ruta, String etiqueta, Object valor, String prefijoParametro) {
        List<String> valores = new ArrayList<>();
        if (valor instanceof List<?> lista) {
            lista.stream().filter(v -> v != null).forEach(v -> valores.add(v.toString()));
        }
        if (c.tipo() == Campo.Tipo.LISTA_ORDENADA && valores.isEmpty()) {
            c.opcionesCon(config).forEach(o -> valores.add(o.valor()));
        }
        String texto = valor == null || valor instanceof List<?> ? "" : valor.toString();
        return new VistaCampo(c.tipo(), prefijoParametro + ruta, id(ruta), etiqueta, c.ayuda(), c.obligatorio(), texto, valores,
                c.minimo(), c.maximoCon(config), c.largoMaximo(), c.opcionesCon(config), errores.get(ruta), c.nombre(), c.prefijo(), c.elemento(),
                List.of(), false, accionUrl, "#form-" + idFormulario, List.of());
    }

    /** Una enumeración que se repite por cada fila de otro campo: "Frente a H1", "Frente a H2"... */
    private List<VistaCampo> celdas(Campo c, String ruta, Object valor, String prefijoParametro) {
        List<VistaCampo> vistas = new ArrayList<>();
        List<?> lista = valor instanceof List<?> l ? l : List.of();
        int cuantas = Math.max(lista.size(), cantidadDe(c.porCadaFilaDe()));
        String prefijoOtro = prefijoDe(c.porCadaFilaDe());
        for (int j = 0; j < cuantas; j++) {
            Object celda = j < lista.size() ? lista.get(j) : null;
            vistas.add(simple(c, ruta + "[" + j + "]", c.etiqueta() + " " + prefijoOtro + (j + 1), celda, prefijoParametro));
        }
        return vistas;
    }

    @SuppressWarnings("unchecked")
    private VistaCampo filas(Campo c, String ruta, Object valor, Map<String, Object> hermanos, String prefijoParametro) {
        List<?> lista = valor instanceof List<?> l ? l : List.of();
        Integer maximo = c.maximoCon(config);
        int minimo = c.minimo() == null ? 0 : c.minimo();
        List<VistaCampo.Fila> filas = new ArrayList<>();
        for (int i = 0; i < lista.size(); i++) {
            Map<String, Object> fila = lista.get(i) instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
            List<VistaCampo> campos = construir(c.campos(), fila, ruta + "[" + i + "].", prefijoParametro);
            filas.add(new VistaCampo.Fila(i, (c.prefijo() == null ? "" : c.prefijo()) + (i + 1), campos, lista.size() > minimo));
        }
        return new VistaCampo(c.tipo(), prefijoParametro + ruta, id(ruta), c.etiqueta(), c.ayuda(), c.obligatorio(), "", List.of(),
                c.minimo(), maximo, null, List.of(), errores.get(ruta), c.nombre(), c.prefijo(), c.elemento(), filas,
                maximo == null || lista.size() < maximo, accionUrl, "#form-" + idFormulario, List.of());
    }

    /** Las propuestas del modelo que trae la entrada, en orden; cada una viaja en campos ocultos. */
    private VistaCampo propuestas(Campo c, String ruta, Object valor, String prefijoParametro) {
        List<?> lista = valor instanceof List<?> l ? l : List.of();
        List<VistaCampo.VistaPropuesta> vistas = new ArrayList<>();
        for (int i = 0; i < lista.size(); i++) {
            Map<?, ?> m = lista.get(i) instanceof Map<?, ?> mapa ? mapa : Map.of();
            vistas.add(new VistaCampo.VistaPropuesta(i, texto(m, "codigo"), texto(m, "destino"), texto(m, "rotulo"), texto(m, "valor"),
                    texto(m, "porque"), Boolean.TRUE.equals(m.get("adoptada")) || "true".equals(String.valueOf(m.get("adoptada"))),
                    texto(m, "modelo"), texto(m, "digest"), texto(m, "prompt")));
        }
        return new VistaCampo(c.tipo(), prefijoParametro + ruta, id(ruta), c.etiqueta(), c.ayuda(), false, "", List.of(),
                null, null, null, List.of(), errores.get(ruta), c.nombre(), c.prefijo(), c.elemento(), List.of(), false, accionUrl,
                "#form-" + idFormulario, vistas);
    }

    private static String texto(Map<?, ?> m, String clave) {
        Object v = m.get(clave);
        return v == null ? "" : v.toString();
    }

    private final java.util.Map<String, Integer> cantidades = new java.util.HashMap<>();
    private final java.util.Map<String, String> prefijos = new java.util.HashMap<>();

    /** Registra cuántas filas tiene y qué prefijo usa un campo de filas, para las celdas que se repiten por él. */
    public ConstructorVista conFilas(String campo, int cantidad, String prefijo) {
        cantidades.put(campo, cantidad);
        prefijos.put(campo, prefijo == null ? "" : prefijo);
        return this;
    }

    private int cantidadDe(String campo) {
        return cantidades.getOrDefault(campo, 0);
    }

    private String prefijoDe(String campo) {
        return prefijos.getOrDefault(campo, "");
    }

    private String id(String ruta) {
        return idFormulario + "-" + ruta.replaceAll("[\\[\\].]+", "-").replaceAll("-$", "");
    }

    /** Prepara un constructor que conoce las filas de primer nivel (para las celdas por fila). */
    public static ConstructorVista para(String idFormulario, List<Campo> campos, Map<String, Object> valores, Map<String, Object> config,
                                        Map<String, String> errores, String accionUrl) {
        ConstructorVista cv = new ConstructorVista(idFormulario, config, errores, accionUrl);
        for (Campo c : campos) {
            if (c.tipo() == Campo.Tipo.FILAS) {
                cv.conFilas(c.nombre(), LectorFormulario.tamanio(valores.get(c.nombre())), c.prefijo());
            }
        }
        return cv;
    }
}
