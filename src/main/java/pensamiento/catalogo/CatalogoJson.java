package pensamiento.catalogo;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import pensamiento.nucleo.Ejemplo;
import pensamiento.nucleo.Esquema;
import pensamiento.nucleo.Familia;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Json;
import pensamiento.nucleo.Tecnica;

/** Lee el catálogo del repo: src/main/resources/catalogo/*.json. Es la fuente de verdad de la semilla. */
public final class CatalogoJson {

    public static final List<String> ARCHIVOS = List.of("familias.json", "tecnicas.json", "relaciones.json", "reglas.json", "esquemas.json");

    public record Relacion(String origen, String destino, String tipo) {
    }

    public record ReglaVersion(String regla, int version, String nombre, Map<String, Object> parametros) {
    }

    /** Un ejemplo tal como está en catalogo/ejemplos/T##.json, con su técnica y su orden en el archivo. */
    public record EjemploJson(String tecnica, int orden, String ambito, String titulo, int versionEsquema,
                              Map<String, Object> config, Map<String, Object> datos, Map<String, Object> resultado, String nota) {
    }

    record EjemploArchivo(String ambito, String titulo, Integer versionEsquema, Map<String, Object> config,
                          Map<String, Object> datos, Map<String, Object> resultado, String nota) {
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

    /** El catálogo único de esquemas de Walton, en el orden del archivo. */
    public List<Esquema> esquemas() {
        return leer("esquemas.json", new TypeReference<List<Esquema>>() { });
    }

    /** Los ejemplos de todas las técnicas que tienen archivo en catalogo/ejemplos/, por técnica y orden. */
    public List<EjemploJson> ejemplos() {
        List<EjemploJson> todos = new ArrayList<>();
        for (String archivo : archivosDeEjemplos()) {
            String tecnica = archivo.substring(archivo.indexOf('/') + 1, archivo.indexOf('.'));
            List<EjemploArchivo> lista = leer(archivo, new TypeReference<List<EjemploArchivo>>() { });
            for (int i = 0; i < lista.size(); i++) {
                EjemploArchivo e = lista.get(i);
                todos.add(new EjemploJson(tecnica, i + 1, e.ambito(), e.titulo(), e.versionEsquema() == null ? 1 : e.versionEsquema().intValue(),
                        e.config(), e.datos(), e.resultado(), e.nota()));
            }
        }
        return todos;
    }

    /** Los ejemplos de una técnica como entidades del núcleo (identificador determinista por técnica y título). */
    public List<Ejemplo> ejemplosDe(IdTecnica tecnica) {
        return ejemplos().stream().filter(e -> e.tecnica().equals(tecnica.valor())).map(this::aEjemplo).toList();
    }

    public Ejemplo aEjemplo(EjemploJson e) {
        return new Ejemplo(UUID.nameUUIDFromBytes((e.tecnica() + "/" + e.titulo()).getBytes(StandardCharsets.UTF_8)),
                IdTecnica.de(e.tecnica()), e.orden(), e.versionEsquema(), Ejemplo.Ambito.valueOf(e.ambito().toUpperCase()), e.titulo(),
                new Json(aJson(e.config())), new Json(aJson(e.datos())), new Json(aJson(e.resultado())), e.nota());
    }

    /** ejemplos/T01.json a ejemplos/T49.json, solo los que existen. */
    List<String> archivosDeEjemplos() {
        List<String> archivos = new ArrayList<>();
        for (int n = 1; n <= IdTecnica.TOTAL; n++) {
            String archivo = String.format("ejemplos/T%02d.json", n);
            if (CatalogoJson.class.getResource(carpeta + archivo) != null) {
                archivos.add(archivo);
            }
        }
        return archivos;
    }

    public String aJson(Map<String, Object> valor) {
        return valor == null ? "{}" : mapper.writeValueAsString(valor);
    }

    public String aJsonLista(List<?> valor) {
        return mapper.writeValueAsString(valor);
    }

    /** Huella del contenido de los archivos del catálogo y los ejemplos: la migración repeatable vuelve a correr cuando cambia. */
    public int huella() {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            List<String> archivos = new ArrayList<>(ARCHIVOS);
            archivos.addAll(archivosDeEjemplos());
            for (String archivo : archivos) {
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
