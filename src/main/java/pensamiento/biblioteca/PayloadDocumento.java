package pensamiento.biblioteca;

import java.util.UUID;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import pensamiento.nucleo.Json;

/** Lo que llevan los trabajos de la biblioteca: de quién es el documento y cuál es. Los trabajos corren como esa persona. */
public record PayloadDocumento(UUID usuarioId, UUID institucionId, UUID documentoId) {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    public Json comoJson() {
        return new Json("{\"usuario\":\"" + usuarioId + "\",\"institucion\":\"" + institucionId + "\",\"documento\":\"" + documentoId + "\"}");
    }

    public static PayloadDocumento de(Json json) {
        JsonNode n = JSON.readTree(json.texto());
        return new PayloadDocumento(UUID.fromString(n.get("usuario").asText()), UUID.fromString(n.get("institucion").asText()),
                UUID.fromString(n.get("documento").asText()));
    }
}
