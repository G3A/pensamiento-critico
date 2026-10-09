package pensamiento.catalogo;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * El banco ramificado de catalogo/preguntas-socraticas.json: tipos socráticos, elementos de Paul-Elder con su tipo, el
 * orden de los elementos por modo de sesión, las listas de marcas, las preguntas por elemento y rama, la de cierre y las
 * de los modos escalera y sombreros. Es contenido versionado, no código: el código elige qué preguntar y saca de aquí el
 * texto. Reglas en docs/ejemplos/T08.md, T10.md, T12.md y T35.md.
 */
public record BancoSocratico(String descripcion, int version, List<Tipo> tipos, List<Elemento> elementos,
                             Map<String, List<String>> ordenes, Map<String, List<String>> marcas,
                             Map<String, List<Pregunta>> preguntas, String cierre, List<Peldano> escalera,
                             List<Sombrero> sombreros, String sintesis) {

    public record Tipo(String id, String nombre, String descripcion) {
    }

    public record Elemento(String id, String nombre, String tipo) {
    }

    /** Una pregunta de un elemento: su rama ("cifra", "general"…) y el texto con {{marca}} si la rama la usa. */
    public record Pregunta(String rama, String texto) {
    }

    public record Peldano(String id, String nombre, String pregunta) {
    }

    /** @param mira lo que mira el sombrero (hechos, emociones…) */
    public record Sombrero(String id, String nombre, String mira, String pregunta) {
    }

    private static final String RUTA = "/catalogo/preguntas-socraticas.json";
    private static volatile BancoSocratico leido;

    public BancoSocratico {
        tipos = List.copyOf(tipos);
        elementos = List.copyOf(elementos);
        ordenes = Map.copyOf(ordenes);
        marcas = Map.copyOf(marcas);
        preguntas = Map.copyOf(preguntas);
        escalera = List.copyOf(escalera);
        sombreros = List.copyOf(sombreros);
    }

    /** El banco del catálogo, leído una vez. */
    public static BancoSocratico delCatalogo() {
        BancoSocratico b = leido;
        if (b == null) {
            b = leer();
            leido = b;
        }
        return b;
    }

    private static BancoSocratico leer() {
        JsonMapper mapper = JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
        try (InputStream in = BancoSocratico.class.getResourceAsStream(RUTA)) {
            if (in == null) {
                throw new IllegalStateException("No existe " + RUTA);
            }
            return mapper.readValue(in, BancoSocratico.class);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer " + RUTA, e);
        }
    }

    public List<String> marcas(String lista) {
        List<String> m = marcas.get(lista);
        if (m == null) {
            throw new IllegalArgumentException("El banco no tiene la lista de marcas " + lista);
        }
        return m;
    }

    public List<Pregunta> preguntas(String elemento) {
        List<Pregunta> p = preguntas.get(elemento);
        if (p == null || p.isEmpty()) {
            throw new IllegalArgumentException("El banco no tiene preguntas para " + elemento);
        }
        return p;
    }

    public List<String> orden(String modo) {
        List<String> o = ordenes.get(modo);
        if (o == null) {
            throw new IllegalArgumentException("El banco no tiene el orden del modo " + modo);
        }
        return o;
    }

    public Tipo tipo(String id) {
        return tipos.stream().filter(t -> t.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Tipo socrático desconocido: " + id));
    }

    public Elemento elemento(String id) {
        return elementos.stream().filter(e -> e.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Elemento desconocido: " + id));
    }

    public Peldano peldano(String id) {
        return escalera.stream().filter(p -> p.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Peldaño desconocido: " + id));
    }

    public Sombrero sombrero(String id) {
        return sombreros.stream().filter(s -> s.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Sombrero desconocido: " + id));
    }
}
