package pensamiento.evaluacion;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import pensamiento.biblioteca.BibliotecaPgvector;
import pensamiento.biblioteca.DetectorTipo;
import pensamiento.biblioteca.Troceador;
import pensamiento.ia.IaSpringAi;
import pensamiento.ia.SemaforoIa;
import pensamiento.nucleo.ConModelo;
import pensamiento.nucleo.Contexto;
import pensamiento.nucleo.Documento;
import pensamiento.nucleo.Fragmento;
import pensamiento.nucleo.Pasaje;
import pensamiento.nucleo.puertos.Biblioteca;
import pensamiento.nucleo.puertos.ExcepcionIa;
import pensamiento.nucleo.puertos.Ia;
import pensamiento.nucleo.puertos.PeticionEmbeddings;
import pensamiento.tecnicas.f4.EjecutorTriangulacion;
import pensamiento.testutil.BaseDatosDePrueba;
import pensamiento.testutil.Entorno;
import pensamiento.testutil.fakes.FakeReloj;

/**
 * Evaluación de la biblioteca del hito 6 (docs/evaluacion-modelo.md, umbrales escritos antes de medir): la búsqueda por texto
 * completo contra la semántica con bge-m3 sobre el banco de 20 consultas, y los 10 documentos adversarios importados, buscados
 * y etiquetados con el prompt de T22 como en la ficha. No es una prueba del perfil: corre a mano con EVALUACION_MODELO=true
 * contra el PostgreSQL y el Ollama del compose, con una institución de prueba que se borra al final, y deja los números en
 * target/evaluacion/biblioteca-{modelo}.json.
 */
@EnabledIfEnvironmentVariable(named = "EVALUACION_MODELO", matches = "true")
class EvaluacionBibliotecaIT {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final BaseDatosDePrueba bd = new BaseDatosDePrueba();
    private static final String MODELO = Optional.ofNullable(System.getenv("EVALUACION_MODELOS")).orElse("qwen3:4b-instruct-2507-q4_K_M").split(",")[0].strip();
    private static UUID institucion;
    private static UUID persona;

    private final BibliotecaPgvector biblioteca = new BibliotecaPgvector(bd.jdbcApp());
    private final Ia ia = new IaSpringAi(Entorno.urlOllama(), MODELO, "bge-m3", new SemaforoIa(1));

    @BeforeAll
    static void sembrar() {
        institucion = bd.crearInstitucion("evaluacion-biblioteca-" + UUID.randomUUID());
        persona = bd.crearUsuario(institucion, "evaluación");
    }

    @AfterAll
    static void limpiar() {
        bd.borrarInstitucion(institucion);
    }

    @Test
    void medir_la_biblioteca() throws IOException {
        Path carpeta = Path.of("target", "evaluacion");
        Files.createDirectories(carpeta);
        Map<String, Object> informe = new LinkedHashMap<>();
        informe.put("modelo", MODELO);
        informe.put("busqueda", busqueda());
        informe.put("adversarios", adversarios());
        String nombre = "biblioteca-" + MODELO.replaceAll("[^a-zA-Z0-9.-]", "_");
        Files.writeString(carpeta.resolve(nombre + ".json"), JSON.writerWithDefaultPrettyPrinter().writeValueAsString(informe));
        System.out.println("EVALUACION " + JSON.writeValueAsString(informe));
    }

    // ---------------------------------------------------------------------------------------------
    // Búsqueda: texto completo contra semántica
    // ---------------------------------------------------------------------------------------------

    private Map<String, Object> busqueda() throws IOException {
        JsonNode banco = leer("/banco-biblioteca.json");
        int primeros = banco.get("umbral").get("primeros").asInt();
        long inicioVectores = System.nanoTime();
        int fragmentos = 0;
        for (JsonNode ruta : banco.get("documentos")) {
            String archivo = ruta.asText();
            fragmentos += importarYVectorizar(archivo.substring(archivo.lastIndexOf('/') + 1), recurso("/" + archivo));
        }
        long vectorizarMs = (System.nanoTime() - inicioVectores) / 1_000_000;
        // Calentar la incrustación de consultas: no cuenta.
        incrustar("calentamiento");
        int aciertosTexto = 0;
        int aciertosSemantica = 0;
        int parafrasisTexto = 0;
        int parafrasisSemantica = 0;
        List<Long> latencias = new ArrayList<>();
        List<String> detalle = new ArrayList<>();
        for (JsonNode c : banco.get("consultas")) {
            String consulta = c.get("consulta").asText();
            boolean parafrasis = "parafrasis".equals(c.get("forma").asText());
            List<Pasaje> porTexto = bd.comoUsuario(persona, institucion, () -> biblioteca.buscarPorTexto(persona, consulta, primeros));
            long inicio = System.nanoTime();
            float[] vector = incrustar(consulta);
            List<Pasaje> porSimilitud = bd.comoUsuario(persona, institucion, () -> biblioteca.buscarPorVector(persona, vector, primeros));
            latencias.add((System.nanoTime() - inicio) / 1_000_000);
            boolean texto = acierta(porTexto, c.get("aceptables"));
            boolean semantica = acierta(porSimilitud, c.get("aceptables"));
            aciertosTexto += texto ? 1 : 0;
            aciertosSemantica += semantica ? 1 : 0;
            parafrasisTexto += parafrasis && texto ? 1 : 0;
            parafrasisSemantica += parafrasis && semantica ? 1 : 0;
            detalle.add(c.get("id").asText() + " (" + c.get("forma").asText() + "): texto " + (texto ? "sí" : "no") + ", semántica " + (semantica ? "sí" : "no")
                    + " · primero por similitud: " + porSimilitud.stream().findFirst().map(p -> p.documento() + " " + String.format("%.2f", p.puntaje())).orElse("—"));
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("consultas", banco.get("consultas").size());
        r.put("fragmentos", fragmentos);
        r.put("vectorizarMs", vectorizarMs);
        r.put("aciertosTextoCompleto", aciertosTexto);
        r.put("aciertosSemantica", aciertosSemantica);
        r.put("parafrasisTextoCompleto", parafrasisTexto);
        r.put("parafrasisSemantica", parafrasisSemantica);
        r.put("latenciaSemanticaP95Ms", p95(latencias));
        r.put("latenciaSemanticaMedianaMs", latencias.stream().sorted().toList().get(latencias.size() / 2));
        r.put("detalle", detalle);
        return r;
    }

    private static boolean acierta(List<Pasaje> pasajes, JsonNode aceptables) {
        for (JsonNode a : aceptables) {
            String documento = a.get("documento").asText();
            String contiene = a.get("contiene").asText();
            if (pasajes.stream().anyMatch(p -> p.documento().equals(documento) && p.texto().contains(contiene))) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------------------------------------
    // Adversarios en documentos: importados, buscados y etiquetados como en la ficha
    // ---------------------------------------------------------------------------------------------

    private Map<String, Object> adversarios() throws IOException {
        JsonNode banco = leer("/banco-adversarios-documentos.json");
        EjecutorTriangulacion t22 = new EjecutorTriangulacion();
        int aciertos = 0;
        int obedecidas = 0;
        int literales = 0;
        int primerosAdversarios = 0;
        int fallas = 0;
        List<Long> tiempos = new ArrayList<>();
        List<String> detalle = new ArrayList<>();
        for (JsonNode a : banco.get("documentos")) {
            String nombre = a.get("id").asText() + "-" + a.get("nombre").asText();
            String contenido = a.get("contenido").asText();
            UUID documento = importar(nombre, contenido.getBytes(StandardCharsets.UTF_8));
            List<Pasaje> pasajes = bd.comoUsuario(persona, institucion, () -> biblioteca.buscarPorTexto(persona, a.get("consulta").asText(), 5));
            Optional<Pasaje> delDocumento = pasajes.stream().filter(p -> p.documentoId().equals(documento)).findFirst();
            if (!pasajes.isEmpty() && pasajes.getFirst().documentoId().equals(documento)) {
                primerosAdversarios++;
            }
            if (delDocumento.isEmpty()) {
                fallas++;
                detalle.add(a.get("id").asText() + ": la búsqueda no devolvió su pasaje");
                continue;
            }
            Pasaje pasaje = delDocumento.get();
            boolean literal = fragmentoTalCual(documento, pasaje) && sinAlterar(contenido, pasaje.texto());
            literales += literal ? 1 : 0;
            String esperada = a.get("esperada").asText();
            String ordena = a.get("ordena").asText();
            EjecutorTriangulacion.Entrada entrada = new EjecutorTriangulacion.Entrada(a.get("afirmacion").asText(), "hecho",
                    List.of(new EjecutorTriangulacion.FuenteRegistrada(nombre, "primaria", "no_aplica", null, null, false, false, null, pasaje.texto(),
                            EjecutorTriangulacion.SIN_ETIQUETAR, null)), List.of(), null);
            Contexto ctx = new Contexto(persona, institucion, Optional.empty(), new FakeReloj(), Optional.of(ia), UUID::randomUUID);
            long inicio = System.nanoTime();
            ConModelo.Propuestas p = t22.proponer(new EjecutorTriangulacion.Config(2, EjecutorTriangulacion.Modo.PLANTILLAS_Y_MODELO), entrada, ctx, t -> { }, 1);
            tiempos.add((System.nanoTime() - inicio) / 1_000_000);
            if (p.caida().isPresent() || p.nuevas().isEmpty()) {
                fallas++;
                detalle.add(a.get("id").asText() + ": sin respuesta del modelo · " + p.caida().orElse(""));
                continue;
            }
            String etiqueta = p.nuevas().getFirst().valor();
            aciertos += etiqueta.equals(esperada) ? 1 : 0;
            obedecidas += etiqueta.equals(ordena) && !ordena.equals(esperada) ? 1 : 0;
            detalle.add(a.get("id").asText() + ": " + etiqueta + " (esperada " + esperada + ", ordena " + ordena + ")" + (literal ? "" : " · PASAJE ALTERADO")
                    + " · " + p.nuevas().getFirst().porque());
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("documentos", banco.get("documentos").size());
        r.put("aciertos", aciertos);
        r.put("obedecidas", obedecidas);
        r.put("pasajesLiterales", literales);
        r.put("pasajeAdversarioPrimero", primerosAdversarios);
        r.put("fallas", fallas);
        r.put("etiquetarP95Ms", p95(tiempos));
        r.put("detalle", detalle);
        return r;
    }

    /** El pasaje que llega a la persona es, tal cual, el texto de un fragmento del documento. */
    private boolean fragmentoTalCual(UUID documento, Pasaje pasaje) {
        List<Fragmento> fragmentos = bd.comoUsuario(persona, institucion, () -> biblioteca.fragmentos(persona, documento));
        return fragmentos.stream().anyMatch(f -> f.id().equals(pasaje.fragmentoId()) && f.texto().equals(pasaje.texto()));
    }

    /**
     * El texto del documento llega sin cambios: en Markdown y texto, el pasaje está tal cual en el archivo; en CSV, el troceado
     * escribe cada fila como «columna: valor · columna: valor», y cuenta como literal si alguna fila tiene todos sus valores, tal
     * cual, en el pasaje.
     */
    static boolean sinAlterar(String contenido, String pasaje) {
        if (normalizado(contenido).contains(normalizado(pasaje))) {
            return true;
        }
        Optional<Character> separador = DetectorTipo.separador(contenido);
        return separador.isPresent() && contenido.lines().skip(1).filter(l -> !l.isBlank())
                .anyMatch(fila -> java.util.Arrays.stream(fila.split(java.util.regex.Pattern.quote(String.valueOf(separador.get()))))
                        .allMatch(valor -> pasaje.contains(": " + valor.strip())));
    }

    private static String normalizado(String texto) {
        return texto.replaceAll("\\s+", " ").strip();
    }

    // ---------------------------------------------------------------------------------------------

    /** Importa como lo hace el indexador (tipo por contenido, troceado) sin pasar por la cola: la app del compose no lo toma. */
    private UUID importar(String nombre, byte[] contenido) {
        Documento.Tipo tipo = DetectorTipo.detectar(contenido).orElseThrow(() -> new IllegalStateException("Tipo no permitido: " + nombre));
        String texto = DetectorTipo.texto(contenido).orElseThrow();
        List<Fragmento.Nuevo> fragmentos = tipo == Documento.Tipo.CSV ? Troceador.trocearCsv(texto, DetectorTipo.separador(texto).orElse(','))
                : Troceador.trocear(List.of(new Troceador.Pagina(Optional.empty(), texto)));
        UUID id = UUID.randomUUID();
        bd.comoUsuario(persona, institucion, () -> {
            biblioteca.crear(persona, institucion, new Biblioteca.NuevoDocumento(id, nombre, tipo, "hash-" + id, contenido));
            biblioteca.indexar(persona, id, fragmentos, Optional.empty());
            return null;
        });
        return id;
    }

    private int importarYVectorizar(String nombre, byte[] contenido) {
        UUID id = importar(nombre, contenido);
        int total = 0;
        while (true) {
            List<Fragmento> lote = bd.comoUsuario(persona, institucion, () -> biblioteca.sinVector(persona, id, 8));
            if (lote.isEmpty()) {
                return total;
            }
            List<float[]> vectores = ia.incrustar(new PeticionEmbeddings(lote.stream().map(Fragmento::texto).toList(), Duration.ofSeconds(60)));
            Map<UUID, float[]> porFragmento = new LinkedHashMap<>();
            for (int i = 0; i < lote.size(); i++) {
                porFragmento.put(lote.get(i).id(), vectores.get(i));
            }
            bd.comoUsuario(persona, institucion, () -> {
                biblioteca.guardarVectores(persona, porFragmento);
                return null;
            });
            total += lote.size();
        }
    }

    private float[] incrustar(String texto) {
        try {
            return ia.incrustar(new PeticionEmbeddings(List.of(texto), Duration.ofSeconds(60))).getFirst();
        } catch (ExcepcionIa e) {
            throw new IllegalStateException("bge-m3 no responde: " + e.getMessage(), e);
        }
    }

    private static byte[] recurso(String ruta) throws IOException {
        try (InputStream in = EvaluacionBibliotecaIT.class.getResourceAsStream(ruta)) {
            return in.readAllBytes();
        }
    }

    private static JsonNode leer(String recurso) throws IOException {
        return JSON.readTree(new String(recurso(recurso), StandardCharsets.UTF_8));
    }

    /** Percentil 95 por el método del rango más cercano. */
    static long p95(List<Long> valores) {
        if (valores.isEmpty()) {
            return 0;
        }
        List<Long> orden = valores.stream().sorted().toList();
        return orden.get(Math.max(0, (int) Math.ceil(0.95 * orden.size()) - 1));
    }

}
