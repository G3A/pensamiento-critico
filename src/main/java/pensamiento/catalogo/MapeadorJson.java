package pensamiento.catalogo;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.json.JsonMapper;

import pensamiento.nucleo.Json;

/**
 * El único JsonMapper de los datos de las técnicas (configuración, entrada y resultado en JSONB, ejemplos
 * del catálogo). Los enums viajan en minúscula por su toString ("cin", "alto"), igual que en el catálogo.
 */
public final class MapeadorJson {

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(EnumFeature.WRITE_ENUMS_USING_TO_STRING)
            .enable(EnumFeature.READ_ENUMS_USING_TO_STRING)
            .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .build();

    private MapeadorJson() {
    }

    public static JsonMapper mapper() {
        return MAPPER;
    }

    public static <T> T leer(Json json, Class<T> tipo) {
        return MAPPER.readValue(json.texto(), tipo);
    }

    public static Json escribir(Object valor) {
        return new Json(MAPPER.writeValueAsString(valor));
    }
}
