package pensamiento.sensores;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Sensor de voseo y español peninsular (RNF-10) sobre plantillas, catálogo, estáticos y código.
 * La lista de formas prohibidas vive en sensores/voseo-prohibido.txt y la comparte el script sensores/voseo.sh.
 */
class VoseoTest {

    private static final List<Path> RAICES = List.of(
            Path.of("src/main/jte"), Path.of("src/main/resources/catalogo"), Path.of("src/main/resources/static/app.js"),
            Path.of("src/main/resources/static/app.css"), Path.of("src/main/java"), Path.of("README.md"));

    @Test
    void ningun_texto_del_repo_usa_voseo_ni_espanol_peninsular() throws IOException {
        List<String> prohibidas = Files.readAllLines(Path.of("sensores/voseo-prohibido.txt")).stream()
                .map(String::trim).filter(l -> !l.isEmpty() && !l.startsWith("#")).toList();
        assertThat(prohibidas).isNotEmpty();
        Pattern patron = Pattern.compile("(?<![\\p{L}\\p{N}_-])(" + String.join("|", prohibidas.stream().map(Pattern::quote).toList()) + ")(?![\\p{L}\\p{N}_-])",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        List<String> hallazgos = new ArrayList<>();
        for (Path raiz : RAICES) {
            if (!Files.exists(raiz)) {
                continue;
            }
            try (Stream<Path> archivos = Files.walk(raiz)) {
                for (Path archivo : archivos.filter(Files::isRegularFile).toList()) {
                    if (archivo.toString().endsWith(".min.js")) {
                        continue;
                    }
                    List<String> lineas = Files.readAllLines(archivo);
                    for (int i = 0; i < lineas.size(); i++) {
                        Matcher m = patron.matcher(lineas.get(i));
                        while (m.find()) {
                            hallazgos.add(archivo + ":" + (i + 1) + " → \"" + m.group(1) + "\"");
                        }
                    }
                }
            }
        }
        assertThat(hallazgos).as("formas de voseo o peninsulares encontradas").isEmpty();
    }
}
