package pensamiento.catalogo;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import pensamiento.nucleo.Familia;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Json;
import pensamiento.nucleo.Tecnica;

/** Lee el catálogo del repo: src/main/resources/catalogo/*.json. Es la fuente de verdad de la semilla. */
public final class CatalogoJson {

    public static final List<String> ARCHIVOS = List.of("familias.json", "tecnicas.json", "relaciones.json", "reglas.json");

    public record Relacion(String origen, String destino, String tipo) {
    }

    public record ReglaVersion(String regla, int version, String nombre, Map<String, Object> parametros) {
    }

    record TecnicaJson(
            String id, String familia, String nombre, String nombreLlano, String usalaCuando, String definicion,
            String tipo, String operacion, String objeto, String modalidad, String patron, String origen,
            String requiereIa, Integer versionEsquema, Map<String, Object> esquemaConfig,
            Map<String, Object> esquemaEntrada, Map<String, Object> configDefault, String estado) {
    }

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final String carpeta;

    public CatalogoJson() {
        this("/catalogo/");
    }

    public CatalogoJson(String carpeta) {
        this.carpeta = carpeta;
    }

    public List<Familia> familias() {
        return leer("familias.json", new TypeReference<List<Familia>>() { });
    }

    public List<Tecnica> tecnicas() {
        return leer("tecnicas.json", new TypeReference<List<TecnicaJson>>() { }).stream().map(this::aTecnica).toList();
    }

    public List<Relacion> relaciones() {
        return leer("relaciones.json", new TypeReference<List<Relacion>>() { });
    }

    public List<ReglaVersion> reglas() {
        return leer("reglas.json", new TypeReference<List<ReglaVersion>>() { });
    }

    public String aJson(Map<String, Object> valor) {
        return valor == null ? "{}" : mapper.writeValueAsString(valor);
    }

    /** Huella del contenido de los cuatro archivos: la migración repeatable vuelve a correr cuando cambia. */
    public int huella() {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            for (String archivo : ARCHIVOS) {
                try (InputStream in = abrir(archivo)) {
                    sha.update(in.readAllBytes());
                }
            }
            byte[] resumen = sha.digest();
            return ((resumen[0] & 0xff) << 24) | ((resumen[1] & 0xff) << 16) | ((resumen[2] & 0xff) << 8) | (resumen[3] & 0xff);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private <T> T leer(String archivo, TypeReference<T> tipo) {
        try (InputStream in = abrir(archivo)) {
            return mapper.readValue(in, tipo);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer " + carpeta + archivo, e);
        }
    }

    private InputStream abrir(String archivo) throws IOException {
        InputStream in = CatalogoJson.class.getResourceAsStream(carpeta + archivo);
        if (in == null) {
            throw new IOException("No existe el recurso " + carpeta + archivo);
        }
        return in;
    }

    private Tecnica aTecnica(TecnicaJson t) {
        return new Tecnica(
                IdTecnica.de(t.id()),
                t.familia(),
                t.nombre(),
                t.nombreLlano(),
                t.usalaCuando(),
                t.definicion(),
                Tecnica.Tipo.valueOf(t.tipo().toUpperCase()),
                Tecnica.Operacion.valueOf(t.operacion().toUpperCase()),
                Tecnica.Objeto.valueOf(t.objeto().toUpperCase()),
                Tecnica.Modalidad.valueOf(t.modalidad().toUpperCase()),
                t.patron(),
                t.origen(),
                Tecnica.RequiereIa.valueOf(t.requiereIa().toUpperCase()),
                t.versionEsquema() == null ? 1 : t.versionEsquema(),
                new Json(aJson(t.esquemaConfig())),
                new Json(aJson(t.esquemaEntrada())),
                new Json(aJson(t.configDefault())),
                Tecnica.Estado.valueOf(t.estado().toUpperCase()));
    }
}
