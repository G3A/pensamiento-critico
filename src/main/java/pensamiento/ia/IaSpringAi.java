package pensamiento.ia;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;
import reactor.core.publisher.Flux;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import pensamiento.nucleo.puertos.Clasificacion;
import pensamiento.nucleo.puertos.EstadoIa;
import pensamiento.nucleo.puertos.Ia;
import pensamiento.nucleo.puertos.IaNoDisponible;
import pensamiento.nucleo.puertos.IaRespuestaInvalida;
import pensamiento.nucleo.puertos.IaTiempoAgotado;
import pensamiento.nucleo.puertos.Mensaje;
import pensamiento.nucleo.puertos.PeticionChat;
import pensamiento.nucleo.puertos.PeticionClasificacion;
import pensamiento.nucleo.puertos.PeticionEmbeddings;
import pensamiento.nucleo.puertos.RespuestaChat;

/**
 * Adaptador del puerto Ia sobre Spring AI y Ollama: temperatura 0, semilla fija, razonamiento de qwen3
 * apagado, una petición a la vez (semáforo) y tiempos máximos por tarea. Cada fallo de red, de tiempo o de
 * formato se traduce a la excepción de dominio que el contrato IaContract exige.
 */
public class IaSpringAi implements Ia {

    public static final double TEMPERATURA = 0.0;
    public static final long SEMILLA = 42L;
    /** Tope de tokens generados por llamada: ninguna tarea del catálogo necesita más y evita respuestas que no terminan. */
    public static final int TOKENS_MAXIMOS = 512;
    /** Ventana de contexto: suficiente para prompt, ejemplos y texto del usuario; acota la memoria del KV cache. */
    public static final int CONTEXTO = 8192;

    private final OllamaApi api;
    private final OllamaChatModel chat;
    private final OllamaEmbeddingModel embeddings;
    private final String modeloChat;
    private final String modeloEmbeddings;
    private final SemaforoIa semaforo;
    private final JsonMapper json = JsonMapper.builder().build();

    public IaSpringAi(String baseUrl, String modeloChat, String modeloEmbeddings, SemaforoIa semaforo) {
        this.api = OllamaApi.builder().baseUrl(baseUrl).build();
        this.modeloChat = modeloChat;
        this.modeloEmbeddings = modeloEmbeddings;
        this.semaforo = semaforo;
        this.chat = OllamaChatModel.builder()
                .ollamaApi(api)
                .options(opcionesBase().build())
                .build();
        this.embeddings = OllamaEmbeddingModel.builder()
                .ollamaApi(api)
                .options(OllamaEmbeddingOptions.builder().model(modeloEmbeddings).build())
                .build();
    }

    private OllamaChatOptions.Builder opcionesBase() {
        return OllamaChatOptions.builder()
                .model(modeloChat)
                .temperature(TEMPERATURA)
                .seed((int) SEMILLA)
                .numPredict(TOKENS_MAXIMOS)
                .numCtx(CONTEXTO)
                .disableThinking();
    }

    @Override
    public EstadoIa estado() {
        try {
            List<String> modelos = api.listModels().models().stream().map(OllamaApi.Model::name).toList();
            boolean ambos = modelos.stream().anyMatch(m -> m.startsWith(modeloChat))
                    && modelos.stream().anyMatch(m -> m.startsWith(modeloEmbeddings));
            return new EstadoIa(ambos, modelos, ambos ? "qwen3:4b y bge-m3 listos" : "faltan modelos: " + modelos);
        } catch (RuntimeException e) {
            return EstadoIa.noDisponible("Ollama no responde: " + e.getClass().getSimpleName());
        }
    }

    public Optional<String> digest(String modelo) {
        try {
            return api.listModels().models().stream()
                    .filter(m -> m.name().startsWith(modelo))
                    .map(OllamaApi.Model::digest)
                    .findFirst();
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    @Override
    public RespuestaChat chat(PeticionChat peticion, Consumer<String> alRecibirToken) {
        return semaforo.conPermiso(() -> {
            List<Message> mensajes = peticion.mensajes().stream().map(IaSpringAi::aMensaje).toList();
            int intentos = 0;
            String ultimo = "";
            while (intentos <= peticion.reintentosMaximos()) {
                intentos++;
                StringBuilder acumulado = new StringBuilder();
                Flux<ChatResponse> flujo = chat.stream(new Prompt(mensajes, opcionesBase().build()));
                try {
                    flujo.timeout(peticion.tiempoMaximo())
                            .doOnNext(r -> {
                                String texto = textoDe(r);
                                if (!texto.isEmpty()) {
                                    acumulado.append(texto);
                                    alRecibirToken.accept(texto);
                                }
                            })
                            .blockLast(peticion.tiempoMaximo().plusSeconds(1));
                } catch (RuntimeException e) {
                    throw traducir(e, "chat");
                }
                ultimo = acumulado.toString().trim();
                if (peticion.validador().isEmpty() || peticion.validador().get().test(ultimo)) {
                    return new RespuestaChat(ultimo, modeloChat, digest(modeloChat).orElse(""), intentos);
                }
            }
            throw new IaRespuestaInvalida("La respuesta no pasó el validador tras " + intentos + " intentos: " + resumir(ultimo));
        });
    }

    @Override
    public Clasificacion clasificar(PeticionClasificacion peticion) {
        return semaforo.conPermiso(() -> {
            String esquema = esquemaClasificacion(peticion.etiquetas());
            String instruccion = peticion.instruccion() + "\nResponde solo con JSON con los campos etiqueta (una de: "
                    + String.join(", ", peticion.etiquetas()) + ") y por_que (una frase en español).";
            Prompt prompt = new Prompt(
                    List.of(new SystemMessage(instruccion), new UserMessage(peticion.texto())),
                    opcionesBase().outputSchema(esquema).build());
            ChatResponse respuesta;
            try {
                respuesta = Flux.defer(() -> Flux.just(chat.call(prompt)))
                        .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
                        .timeout(peticion.tiempoMaximo())
                        .blockFirst(peticion.tiempoMaximo().plusSeconds(1));
            } catch (RuntimeException e) {
                throw traducir(e, "clasificación");
            }
            String texto = respuesta == null ? "" : textoDe(respuesta);
            return interpretarClasificacion(texto, peticion.etiquetas());
        });
    }

    /** Pura: interpreta la salida estructurada; es lo que el contrato "JSON inválido" ejercita. */
    public Clasificacion interpretarClasificacion(String texto, List<String> etiquetas) {
        JsonNode nodo;
        try {
            nodo = json.readTree(texto);
        } catch (RuntimeException e) {
            throw new IaRespuestaInvalida("El modelo no devolvió JSON válido: " + resumir(texto), e);
        }
        if (nodo == null || !nodo.isObject() || !nodo.has("etiqueta")) {
            throw new IaRespuestaInvalida("El JSON no trae el campo etiqueta: " + resumir(texto));
        }
        String etiqueta = nodo.get("etiqueta").asText();
        if (!etiquetas.contains(etiqueta)) {
            throw new IaRespuestaInvalida("Etiqueta fuera del enum: " + etiqueta);
        }
        String porQue = nodo.has("por_que") ? nodo.get("por_que").asText() : "";
        return new Clasificacion(etiqueta, porQue);
    }

    @Override
    public List<float[]> incrustar(PeticionEmbeddings peticion) {
        return semaforo.conPermiso(() -> {
            try {
                return Flux.defer(() -> Flux.just(embeddings.embed(peticion.textos())))
                        .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
                        .timeout(peticion.tiempoMaximo())
                        .blockFirst(peticion.tiempoMaximo().plusSeconds(1));
            } catch (RuntimeException e) {
                throw traducir(e, "embeddings");
            }
        });
    }

    private String esquemaClasificacion(List<String> etiquetas) {
        String valores = String.join("\",\"", etiquetas.stream().map(e -> e.replace("\"", "")).toList());
        return "{\"type\":\"object\",\"properties\":{\"etiqueta\":{\"type\":\"string\",\"enum\":[\"" + valores
                + "\"]},\"por_que\":{\"type\":\"string\"}},\"required\":[\"etiqueta\",\"por_que\"]}";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> leerJson(String texto) {
        return json.readValue(texto, Map.class);
    }

    private static Message aMensaje(Mensaje m) {
        return switch (m.rol()) {
            case SISTEMA -> new SystemMessage(m.contenido());
            case USUARIO -> new UserMessage(m.contenido());
            case ASISTENTE -> new AssistantMessage(m.contenido());
        };
    }

    private static String textoDe(ChatResponse r) {
        if (r == null || r.getResult() == null || r.getResult().getOutput() == null) {
            return "";
        }
        String texto = r.getResult().getOutput().getText();
        return texto == null ? "" : texto;
    }

    private static RuntimeException traducir(RuntimeException e, String tarea) {
        Throwable causa = e;
        while (causa != null) {
            if (causa instanceof TimeoutException || causa instanceof java.net.http.HttpTimeoutException
                    || causa instanceof java.net.SocketTimeoutException) {
                return new IaTiempoAgotado("Se agotó el tiempo de la tarea " + tarea, e);
            }
            if (causa instanceof java.net.ConnectException || causa instanceof java.net.UnknownHostException
                    || causa instanceof java.nio.channels.ClosedChannelException
                    || causa instanceof java.io.IOException) {
                return new IaNoDisponible("Ollama no está disponible para " + tarea, e);
            }
            causa = causa.getCause();
        }
        if (e instanceof pensamiento.nucleo.puertos.ExcepcionIa) {
            return e;
        }
        String nombre = e.getClass().getName();
        if (nombre.contains("Timeout")) {
            return new IaTiempoAgotado("Se agotó el tiempo de la tarea " + tarea, e);
        }
        if (nombre.contains("ResourceAccess") || nombre.contains("WebClientRequest") || nombre.contains("Connect")
                || nombre.contains("NonTransientAi") || nombre.contains("TransientAi")) {
            return new IaNoDisponible("Ollama no está disponible para " + tarea + ": " + e.getMessage(), e);
        }
        return new IaNoDisponible("Fallo inesperado de Ollama en " + tarea + ": " + e.getMessage(), e);
    }

    private static String resumir(String texto) {
        return texto.length() > 120 ? texto.substring(0, 120) + "…" : texto;
    }

    static Duration oDefecto(Duration d, Duration defecto) {
        return d == null ? defecto : d;
    }
}
