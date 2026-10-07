package pensamiento.contrato.real;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import pensamiento.contrato.IaContract;
import pensamiento.ia.IaSpringAi;
import pensamiento.ia.SemaforoIa;
import pensamiento.nucleo.puertos.Ia;
import pensamiento.testutil.Entorno;

/**
 * El adaptador Spring AI contra el Ollama del compose (qwen3:4b y bge-m3 fijados por digest).
 * Corre solo en el workflow nocturno con CONTRACT_REAL=true. La dimensión "JSON inválido" no se puede
 * forzar en un modelo real con salida estructurada, así que esa única dimensión usa un servidor local que
 * imita a Ollama devolviendo basura: lo que se certifica ahí es el mapeo del adaptador, con el mismo código.
 */
@EnabledIfEnvironmentVariable(named = "CONTRACT_REAL", matches = "true")
class RealIaContractIT extends IaContract {

    private static HttpServer imitadorConJsonInvalido;

    @BeforeAll
    static void levantarImitador() throws IOException {
        imitadorConJsonInvalido = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        imitadorConJsonInvalido.createContext("/", intercambio -> {
            String cuerpo = intercambio.getRequestURI().getPath().endsWith("/api/tags")
                    ? "{\"models\":[{\"name\":\"qwen3:4b\",\"model\":\"qwen3:4b\",\"digest\":\"x\",\"size\":1},{\"name\":\"bge-m3:latest\",\"model\":\"bge-m3:latest\",\"digest\":\"y\",\"size\":1}]}"
                    : "{\"model\":\"qwen3:4b\",\"created_at\":\"2026-10-07T00:00:00Z\",\"message\":{\"role\":\"assistant\",\"content\":\"esto no es json {\"},\"done\":true,\"done_reason\":\"stop\"}";
            byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().add("Content-Type", "application/json");
            intercambio.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = intercambio.getResponseBody()) {
                out.write(bytes);
            }
        });
        imitadorConJsonInvalido.start();
    }

    @AfterAll
    static void apagarImitador() {
        imitadorConJsonInvalido.stop(0);
    }

    @Override
    protected Ia disponible() {
        return new IaSpringAi(Entorno.urlOllama(), "qwen3:4b", "bge-m3", new SemaforoIa(1));
    }

    @Override
    protected Ia noDisponible() {
        return new IaSpringAi("http://127.0.0.1:9", "qwen3:4b", "bge-m3", new SemaforoIa(1));
    }

    @Override
    protected Ia lenta() {
        return disponible();
    }

    @Override
    protected Ia conJsonInvalido() {
        return new IaSpringAi("http://127.0.0.1:" + imitadorConJsonInvalido.getAddress().getPort(), "qwen3:4b", "bge-m3", new SemaforoIa(1));
    }
}
