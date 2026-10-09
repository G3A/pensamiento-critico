package pensamiento.evaluacion;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import pensamiento.catalogo.BancoSocratico;
import pensamiento.flujos.Consejero;
import pensamiento.ia.IaSpringAi;
import pensamiento.ia.SemaforoIa;
import pensamiento.nucleo.puertos.ExcepcionIa;
import pensamiento.nucleo.puertos.Ia;
import pensamiento.nucleo.puertos.PeticionChat;
import pensamiento.tecnicas.comun.Prompts;
import pensamiento.tecnicas.comun.Redaccion;
import pensamiento.tecnicas.comun.ValidadorTurno;
import pensamiento.tecnicas.f1.EjecutorPaulElder.Elemento;
import pensamiento.tecnicas.f2.EjecutorPreguntasSocraticas;
import pensamiento.tecnicas.f2.EstrategiaSocratica;
import pensamiento.tecnicas.f6.EjecutorEquipoRojo;
import pensamiento.testutil.Entorno;
import pensamiento.testutil.fakes.FakeRepositorioEsquemas;

/**
 * Informe de evaluación del Consejero socrático (hito 5; RNF-02, RNF-04), con los umbrales escritos antes de medir en cada
 * banco: los 30 diálogos del hito 3, los 12 turnos de los modos escalera y sombreros y las 10 razones del equipo rojo pasan
 * por el mismo camino que el Consejero (el pedido que arma el motor, el validador del turno con dos reintentos y la caída
 * al banco). No es una prueba del perfil: corre a mano con EVALUACION_MODELO=true contra el Ollama real y deja los números
 * en target/evaluacion/consejero-{modelo}.json, que se copian a docs/evaluacion-modelo.md.
 */
@EnabledIfEnvironmentVariable(named = "EVALUACION_MODELO", matches = "true")
class EvaluacionConsejeroIT {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void medir_el_consejero() throws IOException {
        List<String> modelos = Arrays.stream(Optional.ofNullable(System.getenv("EVALUACION_MODELOS"))
                .orElse("qwen3:4b-instruct-2507-q4_K_M").split(",")).map(String::strip).toList();
        int limite = Optional.ofNullable(System.getenv("EVALUACION_LIMITE")).map(Integer::parseInt).orElse(Integer.MAX_VALUE);
        Path carpeta = Path.of("target", "evaluacion");
        Files.createDirectories(carpeta);
        for (String modelo : modelos) {
            Ia ia = new IaSpringAi(Entorno.urlOllama(), modelo, "bge-m3", new SemaforoIa(1));
            calentar(ia);
            Map<String, Object> informe = new LinkedHashMap<>();
            informe.put("modelo", modelo);
            informe.put("limite", limite == Integer.MAX_VALUE ? "sin límite" : limite);
            informe.put("dialogos", medir(ia, pedidosDeDialogos(limite)));
            informe.put("modos", medir(ia, pedidosDeModos(limite)));
            informe.put("ataques", medir(ia, pedidosDeAtaques(limite)));
            String nombre = "consejero-" + modelo.replaceAll("[^a-zA-Z0-9.-]", "_");
            Files.writeString(carpeta.resolve(nombre + ".json"), JSON.writerWithDefaultPrettyPrinter().writeValueAsString(informe));
            System.out.println("EVALUACION " + modelo + " " + JSON.writeValueAsString(informe));
        }
    }

    private static void calentar(Ia ia) {
        try {
            ia.chat(PeticionChat.simple("Responde solo: hola.", Duration.ofSeconds(300)), t -> { });
        } catch (ExcepcionIa e) {
            System.out.println("No se pudo calentar el modelo: " + e.getMessage());
        }
    }

    /** Un ítem de banco ya convertido en el pedido que arma el motor, con su pregunta del banco. */
    record Item(String id, Consejero.Pedido pedido, String banco) {
    }

    /** Los 30 diálogos: el tipo del banco elige el primer elemento de ese tipo en el orden del modo decisión. */
    private static List<Item> pedidosDeDialogos(int limite) throws IOException {
        BancoSocratico banco = BancoSocratico.delCatalogo();
        EstrategiaSocratica estrategia = new EstrategiaSocratica(banco);
        List<Item> items = new ArrayList<>();
        for (JsonNode d : leer("/banco-dialogos.json").get("turnos")) {
            if (items.size() >= limite) {
                break;
            }
            EstrategiaSocratica.TipoSocratico tipo = EstrategiaSocratica.TipoSocratico.de(d.get("tipo").asText());
            Elemento elemento = banco.orden("decision").stream().map(id -> Arrays.stream(Elemento.values()).filter(e -> e.toString().equals(id)).findFirst().orElseThrow())
                    .filter(e -> estrategia.tipoDe(e) == tipo).findFirst().orElseThrow();
            String texto = d.get("texto").asText();
            EstrategiaSocratica.Movimiento m = estrategia.movimiento(elemento, 1, texto, "");
            items.add(new Item(d.get("id").asText(), new Consejero.Pedido(EjecutorPreguntasSocraticas.PROMPT, EjecutorPreguntasSocraticas.VERSION_PROMPT,
                    EjecutorPreguntasSocraticas.datosDelPrompt(banco, m, texto), EjecutorPreguntasSocraticas.PALABRAS_MAXIMAS), m.pregunta()));
        }
        return items;
    }

    private static List<Item> pedidosDeModos(int limite) throws IOException {
        BancoSocratico banco = BancoSocratico.delCatalogo();
        List<Item> items = new ArrayList<>();
        for (JsonNode d : leer("/banco-modos.json").get("turnos")) {
            if (items.size() >= limite) {
                break;
            }
            String paso = d.get("paso").asText();
            String texto = d.get("texto").asText();
            boolean escalera = "escalera".equals(d.get("modo").asText());
            Consejero.Pedido p = escalera ? Consejero.pedidoPeldano(banco, paso, texto) : Consejero.pedidoSombrero(banco, paso, texto);
            items.add(new Item(d.get("id").asText(), p, escalera ? banco.peldano(paso).pregunta() : banco.sombrero(paso).pregunta()));
        }
        return items;
    }

    private static List<Item> pedidosDeAtaques(int limite) throws IOException {
        pensamiento.catalogo.BancoAtaques ataques = pensamiento.catalogo.BancoAtaques.delCatalogo();
        EjecutorEquipoRojo t36 = new EjecutorEquipoRojo(new FakeRepositorioEsquemas());
        List<Item> items = new ArrayList<>();
        for (JsonNode d : leer("/banco-ataques.json").get("razones")) {
            if (items.size() >= limite) {
                break;
            }
            String esquema = d.get("esquema").asText();
            int pregunta = d.get("pregunta").asInt();
            String texto = ataques.texto(esquema, pregunta);
            EjecutorEquipoRojo.AtaquePlaneado a = new EjecutorEquipoRojo.AtaquePlaneado("A1", 0, esquema, pregunta, texto);
            items.add(new Item(d.get("id").asText(), new Consejero.Pedido(EjecutorEquipoRojo.PROMPT, EjecutorEquipoRojo.VERSION_PROMPT,
                    t36.datosDelPrompt(d.get("postura").asText(), d.get("razon").asText(), a), EjecutorEquipoRojo.PALABRAS_MAXIMAS), texto));
        }
        return items;
    }

    /**
     * Pasa cada pedido por el validador del turno con dos reintentos, como el Consejero. Cuenta cuántos aprueban al primer
     * intento, cuántos necesitan reintento y cuántos caen al banco, los motivos de rechazo y la latencia.
     */
    private static Map<String, Object> medir(Ia ia, List<Item> items) {
        int primero = 0;
        int conReintento = 0;
        int alBanco = 0;
        int lleganMal = 0;
        int fallasModelo = 0;
        Map<String, Integer> motivos = new TreeMap<>();
        List<Long> primerosTokens = new ArrayList<>();
        List<Long> turnos = new ArrayList<>();
        List<String> detalle = new ArrayList<>();
        for (Item it : items) {
            Prompts.Prompt prompt = Prompts.de(it.pedido().prompt(), it.pedido().version());
            long inicio = System.nanoTime();
            long[] primerToken = {-1};
            try {
                Redaccion.Redactado r = Redaccion.redactar(ia, prompt.sistema(it.pedido().datos()), prompt.pedido(it.pedido().datos()),
                        it.pedido().palabrasMaximas(), t -> {
                            if (primerToken[0] < 0) {
                                primerToken[0] = System.nanoTime();
                            }
                        });
                long fin = System.nanoTime();
                primerosTokens.add(((primerToken[0] < 0 ? fin : primerToken[0]) - inicio) / 1_000_000);
                turnos.add((fin - inicio) / 1_000_000);
                r.intentos().stream().map(Redaccion.Intento::rechazo).flatMap(Optional::stream).forEach(m -> motivos.merge(m.name().toLowerCase(), 1, Integer::sum));
                String llega = r.texto().orElse(it.banco());
                if (r.texto().isPresent() && ValidadorTurno.rechazo(llega, it.pedido().palabrasMaximas()).isPresent()) {
                    lleganMal++;
                }
                if (r.alPrimerIntento()) {
                    primero++;
                } else if (r.texto().isPresent()) {
                    conReintento++;
                } else {
                    alBanco++;
                }
                detalle.add(it.id() + " (" + r.intentos().size() + " intento(s), " + turnos.getLast() + " ms): "
                        + r.intentos().stream().map(x -> x.texto() + x.rechazo().map(m -> " [" + m.name().toLowerCase() + "]").orElse("")).toList());
            } catch (ExcepcionIa e) {
                fallasModelo++;
                alBanco++;
                turnos.add(pensamiento.tecnicas.comun.ModeloLocal.TIEMPO_CHAT.toMillis());
                primerosTokens.add(pensamiento.tecnicas.comun.ModeloLocal.TIEMPO_CHAT.toMillis());
                detalle.add(it.id() + ": falla " + e.getClass().getSimpleName());
            }
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("items", items.size());
        r.put("aprobadosAlPrimerIntento", primero);
        r.put("conReintento", conReintento);
        r.put("caenAlBanco", alBanco);
        r.put("fallasDelModelo", fallasModelo);
        r.put("lleganALaPersonaConVoseoOVeredicto", lleganMal);
        r.put("motivosDeRechazo", motivos);
        r.put("primerTokenP95Ms", EvaluacionModeloIT.p95(primerosTokens));
        r.put("turnoValidadoP95Ms", EvaluacionModeloIT.p95(turnos));
        r.put("turnoValidadoMedianaMs", mediana(turnos));
        r.put("detalle", detalle);
        return r;
    }

    private static long mediana(List<Long> valores) {
        List<Long> orden = valores.stream().sorted().toList();
        return orden.isEmpty() ? 0 : orden.get(orden.size() / 2);
    }

    private static JsonNode leer(String recurso) throws IOException {
        try (InputStream in = EvaluacionConsejeroIT.class.getResourceAsStream(recurso)) {
            return JSON.readTree(in);
        }
    }
}
