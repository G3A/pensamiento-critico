package pensamiento.testutil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** Lo que las pruebas de integración y aceptación necesitan del compose: URL, credenciales y secretos. */
public final class Entorno {

    private Entorno() {
    }

    public static String urlBaseDeDatos() {
        return variable("SPRING_DATASOURCE_URL").orElse("jdbc:postgresql://localhost:5432/pensamiento");
    }

    public static String usuarioApp() {
        return variable("SPRING_DATASOURCE_USERNAME").orElse("app");
    }

    public static String usuarioAdministradorDb() {
        return variable("SPRING_FLYWAY_USER").orElse("pensamiento");
    }

    public static String claveDb() {
        return secreto("db_password", "SPRING_DATASOURCE_PASSWORD_FILE", "DB_PASSWORD");
    }

    public static String pinAdministrador() {
        return secreto("admin_pin", "APP_ADMIN_PIN_FILE", "APP_ADMIN_PIN");
    }

    public static String urlApp() {
        return variable("APP_URL").orElse("http://localhost:8080");
    }

    /** El modelo de chat del compose (SPRING_AI_OLLAMA_CHAT_OPTIONS_MODEL); el fijado por defecto desde el hito 3. */
    public static String modeloChat() {
        return variable("SPRING_AI_OLLAMA_CHAT_OPTIONS_MODEL").orElse("qwen3:4b-instruct-2507-q4_K_M");
    }

    public static String urlOllama() {
        return variable("SPRING_AI_OLLAMA_BASE_URL").orElse("http://localhost:11434");
    }

    public static boolean contratoReal() {
        return "true".equalsIgnoreCase(System.getenv("CONTRACT_REAL"));
    }

    private static String secreto(String nombre, String variableArchivo, String variableDirecta) {
        Optional<String> directo = variable(variableDirecta);
        if (directo.isPresent()) {
            return directo.get();
        }
        Path archivo = Path.of(variable(variableArchivo).orElse("/run/secrets/" + nombre));
        if (!Files.exists(archivo)) {
            archivo = Path.of("secrets", nombre + ".txt");
        }
        try {
            return Files.readString(archivo).trim();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer el secreto " + nombre + " en " + archivo, e);
        }
    }

    private static Optional<String> variable(String nombre) {
        return Optional.ofNullable(System.getenv(nombre)).filter(v -> !v.isBlank());
    }
}
