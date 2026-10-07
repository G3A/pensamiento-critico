package pensamiento.web.formulario;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import tools.jackson.core.type.TypeReference;

import pensamiento.catalogo.MapeadorJson;
import pensamiento.nucleo.Json;

/** Lee los esquemas del catálogo ({"campos": [...]}) y la configuración como mapas simples. */
public final class LenguajeCampos {

    record Esquema(List<Campo> campos) {
    }

    private LenguajeCampos() {
    }

    public static List<Campo> campos(Json esquema) {
        Esquema leido = MapeadorJson.leer(esquema, Esquema.class);
        return leido.campos() == null ? List.of() : leido.campos();
    }

    public static Map<String, Object> mapa(Json json) {
        Map<String, Object> mapa = MapeadorJson.mapper().readValue(json.texto(), new TypeReference<LinkedHashMap<String, Object>>() { });
        return mapa == null ? new LinkedHashMap<>() : mapa;
    }

    public static Json json(Map<String, Object> mapa) {
        return MapeadorJson.escribir(mapa);
    }

    /** Busca un campo por su ruta sin índices: "evidencias.celdas". */
    public static Campo buscar(List<Campo> campos, String rutaSinIndices) {
        String[] partes = rutaSinIndices.split("\\.");
        List<Campo> nivel = campos;
        Campo encontrado = null;
        for (String parte : partes) {
            encontrado = nivel.stream().filter(c -> c.nombre().equals(parte)).findFirst().orElse(null);
            if (encontrado == null) {
                return null;
            }
            nivel = encontrado.campos();
        }
        return encontrado;
    }
}
