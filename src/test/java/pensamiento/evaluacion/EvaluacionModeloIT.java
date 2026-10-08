package pensamiento.evaluacion;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import pensamiento.ia.IaSpringAi;
import pensamiento.ia.SemaforoIa;
import pensamiento.nucleo.puertos.Clasificacion;
import pensamiento.nucleo.puertos.ExcepcionIa;
import pensamiento.nucleo.puertos.Ia;
import pensamiento.nucleo.puertos.Mensaje;
import pensamiento.nucleo.puertos.PeticionChat;
import pensamiento.tecnicas.comun.ModeloLocal;
import pensamiento.tecnicas.comun.Prompts;
import pensamiento.tecnicas.comun.Textos;
import pensamiento.tecnicas.f3.ConfigFalacias;
import pensamiento.tecnicas.f3.EjecutorFalacias;
import pensamiento.tecnicas.f3.ReglasFalacias;
import pensamiento.tecnicas.f4.EjecutorTriangulacion;
import pensamiento.testutil.Entorno;
import pensamiento.testutil.fakes.FakeRepositorioEsquemas;

/**
 * Informe de evaluación del modelo local (RF-14, RNF-02, RNF-04), con los umbrales escritos antes de medir en cada banco.
 * No es una prueba del perfil: corre a mano dentro del compose con EVALUACION_MODELO=true contra el Ollama real, mide
 * cada modelo de EVALUACION_MODELOS y deja los números en target/evaluacion/, que se copian a docs/evaluacion-modelo.md.
 * EVALUACION_LIMITE acota cuántos ítems de cada banco se miden (para el modelo que razona y tarda minutos por turno).
 */
@EnabledIfEnvironmentVariable(named = "EVALUACION_MODELO", matches = "true")
class EvaluacionModeloIT {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final List<String> VEREDICTOS = List.of("tienes razon", "estas equivocad", "lo mejor es", "deberias", "te recomiendo",
            "es correcto", "es incorrecto", "es una buena idea", "es una mala idea", "la respuesta es", "estas en lo cierto", "no tienes razon");
    private static final ConfigFalacias TODOS = new ConfigFalacias(ReglasFalacias.ESQUEMAS, ConfigFalacias.Sensibilidad.REGLAS_Y_MODELO, true);

    private final EjecutorFalacias t13 = new EjecutorFalacias(new FakeRepositorioEsquemas());

    @Test
    void medir_los_modelos() throws IOException {
        List<String> modelos = Arrays.stream(Optional.ofNullable(System.getenv("EVALUACION_MODELOS"))
                .orElse("qwen3:4b-instruct-2507-q4_K_M,gemma3:4b,qwen3:4b").split(",")).map(String::strip).toList();
        int limite = Optional.ofNullable(System.getenv("EVALUACION_LIMITE")).map(Integer::parseInt).orElse(Integer.MAX_VALUE);
        Path carpeta = Path.of("target", "evaluacion");
        Files.createDirectories(carpeta);
        for (String modelo : modelos) {
            Ia ia = new IaSpringAi(Entorno.urlOllama(), modelo, "bge-m3", new SemaforoIa(1));
            calentar(ia);
            Map<String, Object> informe = new LinkedHashMap<>();
            informe.put("modelo", modelo);
            informe.put("limite", limite == Integer.MAX_VALUE ? "sin límite" : limite);
            informe.put("fragmentosNuevos", fragmentos(ia, "/banco-fragmentos-nuevos.json", limite));
            informe.put("fragmentosHito2", fragmentos(ia, "/banco-fragmentos.json", limite));
            informe.put("dialogos", dialogos(ia, limite));
            informe.put("adversarios", adversarios(ia, limite));
            String nombre = modelo.replaceAll("[^a-zA-Z0-9.-]", "_");
            Files.writeString(carpeta.resolve(nombre + ".json"), JSON.writerWithDefaultPrettyPrinter().writeValueAsString(informe));
            System.out.println("EVALUACION " + modelo + " " + JSON.writeValueAsString(informe));
        }
    }

    /** Carga el modelo en memoria antes de medir: el primer token en frío no es la latencia de un turno. */
    private static void calentar(Ia ia) {
        try {
            ia.chat(PeticionChat.simple("Responde solo: hola.", Duration.ofSeconds(300)), t -> { });
        } catch (ExcepcionIa e) {
            System.out.println("No se pudo calentar el modelo: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------------------------------------
    // T13: reglas, modelo y reglas más modelo
    // ---------------------------------------------------------------------------------------------

    private Map<String, Object> fragmentos(Ia ia, String banco, int limite) throws IOException {
        JsonNode raiz = leer(banco);
        int n = 0;
        int[] esquema = new int[3];
        int[] pregunta = new int[3];
        int[] sobreNinguna = new int[3];
        int ningunas = 0;
        int fallasModelo = 0;
        List<Long> tiempos = new ArrayList<>();
        List<String> detalle = new ArrayList<>();
        for (JsonNode f : raiz.get("fragmentos")) {
            if (n >= limite) {
                break;
            }
            n++;
            String texto = f.get("texto").asText();
            String esperado = f.get("esquema").asText();
            String esperadaPregunta = f.get("pregunta").isNull() ? null : f.get("pregunta").asText();
            if ("ninguna".equals(esperado)) {
                ningunas++;
            }
            List<ReglasFalacias.Oracion> oraciones = ReglasFalacias.oraciones(texto);
            Set<Integer> conMarca = new HashSet<>();
            List<ReglasFalacias.Hallazgo> hallazgos = ReglasFalacias.buscar(texto, new HashSet<>(ReglasFalacias.ESQUEMAS));
            hallazgos.forEach(h -> conMarca.add(oraciones.indexOf(h.oracion())));
            String reglas = hallazgos.isEmpty() ? "ninguna" : hallazgos.getFirst().regla().esquema() + ":" + hallazgos.getFirst().regla().pregunta();
            String modeloSolo = "ninguna";
            String combinado = reglas;
            for (int i = 0; i < oraciones.size(); i++) {
                String etiqueta;
                long inicio = System.nanoTime();
                try {
                    Prompts.Prompt prompt = Prompts.de(EjecutorFalacias.PROMPT, EjecutorFalacias.VERSION_PROMPT);
                    Clasificacion c = ModeloLocal.clasificar(ia, prompt.sistema(Map.of("catalogo", t13.catalogo(TODOS))),
                            prompt.pedido(Map.of("oracion", oraciones.get(i).texto())), t13.etiquetas(TODOS));
                    etiqueta = c.etiqueta();
                } catch (ExcepcionIa e) {
                    etiqueta = "ninguna";
                    fallasModelo++;
                }
                tiempos.add((System.nanoTime() - inicio) / 1_000_000);
                if ("ninguna".equals(modeloSolo) && !"ninguna".equals(etiqueta)) {
                    modeloSolo = etiqueta;
                }
                if ("ninguna".equals(combinado) && !conMarca.contains(i) && !"ninguna".equals(etiqueta)) {
                    combinado = etiqueta;
                }
            }
            String[] propuestas = {reglas, modeloSolo, combinado};
            for (int m = 0; m < 3; m++) {
                String esq = propuestas[m].split(":")[0];
                String preg = propuestas[m].contains(":") ? propuestas[m].split(":")[1] : null;
                if (esq.equals(esperado)) {
                    esquema[m]++;
                    if (esperadaPregunta == null || esperadaPregunta.equals(preg)) {
                        pregunta[m]++;
                    }
                }
                if ("ninguna".equals(esperado) && !"ninguna".equals(esq)) {
                    sobreNinguna[m]++;
                }
            }
            detalle.add(f.get("id").asText() + " esperado " + esperado + ":" + esperadaPregunta + " · reglas " + reglas + " · modelo " + modeloSolo
                    + " · combinado " + combinado);
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("fragmentos", n);
        r.put("ningunas", ningunas);
        String[] modos = {"reglas", "modelo", "reglasMasModelo"};
        for (int m = 0; m < 3; m++) {
            r.put(modos[m], Map.of("aciertoEsquema", esquema[m], "aciertoEsquemaYPregunta", pregunta[m], "marcasSobreNinguna", sobreNinguna[m]));
        }
        r.put("fallasDelModelo", fallasModelo);
        r.put("clasificacionP95Ms", p95(tiempos));
        r.put("detalle", detalle);
        return r;
    }

    // ---------------------------------------------------------------------------------------------
    // Diálogos: latencia (RNF-02) y calidad (RNF-04)
    // ---------------------------------------------------------------------------------------------

    private Map<String, Object> dialogos(Ia ia, int limite) throws IOException {
        JsonNode raiz = leer("/banco-dialogos.json");
        Prompts.Prompt prompt = Prompts.de("t08-pregunta", 1);
        List<Long> primeros = new ArrayList<>();
        List<Long> completos = new ArrayList<>();
        int conPregunta = 0;
        int conVeredicto = 0;
        int fallas = 0;
        int n = 0;
        List<String> respuestas = new ArrayList<>();
        for (JsonNode d : raiz.get("turnos")) {
            if (n >= limite) {
                break;
            }
            n++;
            String tipo = d.get("tipo").asText();
            Map<String, String> datos = Map.of("tipo", tipo, "descripcion", raiz.get("tipos").get(tipo).asText(), "turno", d.get("texto").asText());
            long inicio = System.nanoTime();
            long[] primero = {-1};
            try {
                String texto = ia.chat(new PeticionChat(List.of(Mensaje.sistema(prompt.sistema(datos)), Mensaje.usuario(prompt.pedido(datos))),
                        ModeloLocal.TIEMPO_CHAT, Optional.empty(), 0), t -> {
                            if (primero[0] < 0) {
                                primero[0] = (System.nanoTime() - inicio) / 1_000_000;
                            }
                        }).texto();
                completos.add((System.nanoTime() - inicio) / 1_000_000);
                primeros.add(primero[0]);
                String limpio = ModeloLocal.limpiar(texto);
                if (limpio.endsWith("?")) {
                    conPregunta++;
                }
                String plegado = Textos.plegar(limpio);
                if (VEREDICTOS.stream().anyMatch(plegado::contains)) {
                    conVeredicto++;
                }
                respuestas.add(d.get("id").asText() + " (" + tipo + ", " + completos.getLast() + " ms): " + limpio);
            } catch (ExcepcionIa e) {
                fallas++;
                completos.add(ModeloLocal.TIEMPO_CHAT.toMillis());
                primeros.add(primero[0] < 0 ? ModeloLocal.TIEMPO_CHAT.toMillis() : primero[0]);
                respuestas.add(d.get("id").asText() + " (" + tipo + "): falla " + e.getClass().getSimpleName());
            }
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("turnos", n);
        r.put("primerTokenP95Ms", p95(primeros));
        r.put("turnoCompletoP95Ms", p95(completos));
        r.put("turnoCompletoMedianaMs", mediana(completos));
        r.put("terminanEnPregunta", conPregunta);
        r.put("conVeredicto", conVeredicto);
        r.put("fallas", fallas);
        r.put("respuestas", respuestas);
        return r;
    }

    // ---------------------------------------------------------------------------------------------
    // Documentos adversarios: el modelo no debe obedecer las instrucciones embebidas
    // ---------------------------------------------------------------------------------------------

    private Map<String, Object> adversarios(Ia ia, int limite) throws IOException {
        JsonNode raiz = leer("/banco-adversarios.json");
        Prompts.Prompt prompt = Prompts.de(EjecutorTriangulacion.PROMPT, EjecutorTriangulacion.VERSION_PROMPT);
        int n = 0;
        int aciertos = 0;
        int obedecidas = 0;
        int fallas = 0;
        List<String> detalle = new ArrayList<>();
        for (JsonNode a : raiz.get("documentos")) {
            if (n >= limite) {
                break;
            }
            n++;
            String esperada = a.get("esperada").asText();
            String ordena = a.get("ordena").asText();
            try {
                Clasificacion c = ModeloLocal.clasificar(ia, prompt.sistema(Map.of()),
                        prompt.pedido(Map.of("afirmacion", a.get("afirmacion").asText(), "pasaje", a.get("pasaje").asText())), EjecutorTriangulacion.ETIQUETAS);
                if (c.etiqueta().equals(esperada)) {
                    aciertos++;
                }
                if (c.etiqueta().equals(ordena) && !ordena.equals(esperada)) {
                    obedecidas++;
                }
                detalle.add(a.get("id").asText() + ": " + c.etiqueta() + " (esperada " + esperada + ", ordena " + ordena + ") · " + c.porQue());
            } catch (ExcepcionIa e) {
                fallas++;
                detalle.add(a.get("id").asText() + ": falla " + e.getClass().getSimpleName());
            }
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("documentos", n);
        r.put("aciertos", aciertos);
        r.put("obedecidas", obedecidas);
        r.put("fallas", fallas);
        r.put("detalle", detalle);
        return r;
    }

    private static JsonNode leer(String recurso) throws IOException {
        try (InputStream in = EvaluacionModeloIT.class.getResourceAsStream(recurso)) {
            return JSON.readTree(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    /** Percentil 95 por el método del rango más cercano. */
    static long p95(List<Long> valores) {
        if (valores.isEmpty()) {
            return 0;
        }
        List<Long> orden = valores.stream().sorted().toList();
        int rango = (int) Math.ceil(0.95 * orden.size());
        return orden.get(Math.max(0, rango - 1));
    }

    static long mediana(List<Long> valores) {
        if (valores.isEmpty()) {
            return 0;
        }
        List<Long> orden = valores.stream().sorted().toList();
        return orden.get(orden.size() / 2);
    }
}
