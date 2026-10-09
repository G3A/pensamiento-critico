package pensamiento.catalogo;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * El banco de ataques de catalogo/ataques.json: un ataque por pregunta crítica de cada esquema de Walton y el genérico
 * para una razón sin esquema. El código identifica la debilidad y saca de aquí el texto (docs/ejemplos/T36.md).
 */
public record BancoAtaques(String descripcion, int version, String sinEsquema, List<Ataque> ataques) {

    public record Ataque(String esquema, int pregunta, String texto) {
    }

    private static final String RUTA = "/catalogo/ataques.json";
    private static volatile BancoAtaques leido;

    public BancoAtaques {
        ataques = List.copyOf(ataques);
    }

    public static BancoAtaques delCatalogo() {
        BancoAtaques b = leido;
        if (b == null) {
            b = leer();
            leido = b;
        }
        return b;
    }

    private static BancoAtaques leer() {
        JsonMapper mapper = JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
        try (InputStream in = BancoAtaques.class.getResourceAsStream(RUTA)) {
            if (in == null) {
                throw new IllegalStateException("No existe " + RUTA);
            }
            return mapper.readValue(in, BancoAtaques.class);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer " + RUTA, e);
        }
    }

    /** El ataque del banco para esa pregunta crítica de ese esquema. */
    public String texto(String esquema, int pregunta) {
        return ataques.stream().filter(a -> a.esquema().equals(esquema) && a.pregunta() == pregunta).map(Ataque::texto).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("El banco no tiene ataque para " + esquema + " pregunta " + pregunta));
    }
}
