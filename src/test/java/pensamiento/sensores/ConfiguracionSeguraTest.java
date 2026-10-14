package pensamiento.sensores;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import pensamiento.web.seguridad.ConfiguracionSeguridad;

/** Sensores de configuración de "Casa con llave": sesión de 10 minutos, CSP sin inline ni eval, htmx sin eval, app.js corto. */
class ConfiguracionSeguraTest {

    @Test
    void la_sesion_expira_a_los_10_minutos_de_inactividad() throws IOException {
        String yml = Files.readString(Path.of("src/main/resources/application.yml"));
        assertThat(yml).containsPattern("timeout:\\s*10m");
        assertThat(yml).containsPattern("http-only:\\s*true");
        assertThat(yml).containsPattern("same-site:\\s*strict");
    }

    @Test
    void la_csp_no_permite_inline_ni_eval() {
        assertThat(ConfiguracionSeguridad.CSP).contains("script-src 'self'").contains("object-src 'none'").contains("frame-ancestors 'none'");
        assertThat(ConfiguracionSeguridad.CSP).doesNotContain("unsafe-inline", "unsafe-eval");
    }

    @Test
    void htmx_arranca_con_allow_eval_en_falso_y_el_token_csrf_sale_del_body() throws IOException {
        String layout = Files.readString(Path.of("src/main/jte/layout.jte"));
        assertThat(layout).contains("\"allowEval\":false").contains("\"allowScriptTags\":false");
        assertThat(layout).containsPattern("<body[^>]*hx-headers=\"\\$\\{pagina\\.cabecerasHtmx\\(\\)\\}\"");
        assertThat(Files.readString(Path.of("src/main/java/pensamiento/web/Pagina.java"))).contains("X-CSRF-TOKEN").contains("X-Persona");
        assertThat(layout).doesNotContain("<script>");
        assertThat(layout).doesNotContain("onclick=");
    }

    @Test
    void app_js_tiene_menos_de_100_lineas_y_los_vendorizados_son_la_lista_cerrada() throws IOException {
        assertThat(Files.readAllLines(Path.of("src/main/resources/static/app.js")).size()).isLessThan(100);
        Path estaticos = Path.of("src/main/resources/static");
        assertThat(estaticos.resolve("htmx.min.js")).exists();
        assertThat(estaticos.resolve("ext/sse.js")).exists();
        assertThat(estaticos.resolve("alpine-csp.min.js")).exists();
        assertThat(estaticos.resolve("app.css")).exists();
        assertThat(estaticos.resolve("app.js")).exists();
        try (var archivos = Files.walk(estaticos)) {
            assertThat(archivos.filter(Files::isRegularFile).map(p -> estaticos.relativize(p).toString().replace('\\', '/')))
                    .containsExactlyInAnyOrder("htmx.min.js", "ext/sse.js", "alpine-csp.min.js", "app.css", "app.js", "llave.js", "borrador.js", "favicon.svg");
        }
        // Hito 9: la llave de acceso tiene su propio archivo (la CSP no admite script en línea), también corto.
        assertThat(Files.readAllLines(estaticos.resolve("llave.js")).size()).isLessThan(100);
        assertThat(Files.readAllLines(estaticos.resolve("borrador.js")).size()).isLessThan(100);
        try (var plantillas = Files.walk(Path.of("src/main/jte"))) {
            assertThat(plantillas.filter(p -> p.toString().endsWith(".jte")).map(p -> {
                try {
                    return Files.readString(p);
                } catch (IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            })).as("ninguna plantilla trae script en línea").noneMatch(t -> t.contains("<script>") || t.matches("(?s).*<script(?![^>]*src=)[^>]*>.*"));
        }
    }

    @Test
    void las_imagenes_del_compose_estan_fijadas_por_digest_y_el_rol_de_aplicacion_no_es_el_administrador() throws IOException {
        String compose = Files.readString(Path.of("docker-compose.yml"));
        assertThat(compose).containsPattern("pgvector/pgvector:pg18@sha256:[0-9a-f]{64}");
        assertThat(compose).containsPattern("ollama/ollama:[0-9.]+@sha256:[0-9a-f]{64}");
        assertThat(compose).contains("OLLAMA_NUM_PARALLEL: \"1\"").contains("mem_limit: 2g").contains("pids_limit: 256").contains("read_only: true");
        assertThat(compose).contains("SPRING_DATASOURCE_USERNAME: app");
        assertThat(compose).contains("${BIND_IP:-127.0.0.1}:8080:8080");
        String dockerfile = Files.readString(Path.of("Dockerfile"));
        assertThat(dockerfile).containsPattern("maven:3-eclipse-temurin-25@sha256:[0-9a-f]{64}");
        assertThat(dockerfile).containsPattern("eclipse-temurin:25-jre@sha256:[0-9a-f]{64}");
        assertThat(dockerfile).contains("graphviz");
        // Hito 9: las pruebas corren en la imagen oficial de Playwright fijada por digest y no descargan navegadores.
        String pruebas = Files.readString(Path.of("Dockerfile.tests"));
        assertThat(pruebas).containsPattern("mcr.microsoft.com/playwright/java:v1\\.63\\.0-noble@sha256:[0-9a-f]{64}")
                .contains("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1");
    }

    @Test
    void los_logs_no_llevan_pin_clave_texto_de_las_personas_ni_mensajes_de_excepcion() throws IOException {
        // Hito 8: logs sin PIN ni texto de las personas. Un mensaje de excepción puede traer el DOT o el SQL con lo escrito.
        String yml = Files.readString(Path.of("src/main/resources/application.yml"));
        assertThat(yml).doesNotContainPattern("(?i)level:[\\s\\S]*(debug|trace)");
        try (var fuentes = Files.walk(Path.of("src/main/java"))) {
            var llamadas = fuentes.filter(p -> p.toString().endsWith(".java")).flatMap(p -> {
                try {
                    return Files.readAllLines(p).stream().filter(l -> l.contains("LOG.")).map(l -> p.getFileName() + ": " + l.strip());
                } catch (IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            }).toList();
            assertThat(llamadas).isNotEmpty().noneMatch(l -> l.matches("(?i).*LOG\\.\\w+\\(.*(\\bpin\\b|\\bclave\\b|texto|getMessage\\(\\)|nombre\\(\\)).*"));
        }
    }

    @Test
    void la_migracion_v1_fuerza_rls_y_el_rol_de_aplicacion_no_puede_borrar_auditoria_ni_cambios_de_opinion() throws IOException {
        String v1 = Files.readString(Path.of("src/main/resources/db/migration/V1__esquema_canonico.sql"));
        assertThat(v1).contains("FORCE ROW LEVEL SECURITY").contains("uuidv7()").contains("vector(1024)").contains("USING hnsw");
        assertThat(v1).contains("GRANT SELECT, INSERT ON cambio_opinion, auditoria TO ${rol_app}");
        String rol = Files.readString(Path.of("docker/db/01-rol-app.sh"));
        assertThat(rol).contains("NOBYPASSRLS").contains("NOSUPERUSER");
    }
}
