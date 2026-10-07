package pensamiento.sensores;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Sensor: todo Fake<X> en testutil/fakes tiene su Fake<X>ContractTest bajo la raíz contrato/.
 * Sin él, el Fake queda autocertificado y los collaboration tests que lo usan pueden mentir.
 * Clasifica solo por nombre de archivo, como el sensor fakes-sin-contract-test del método.
 */
class FakesSinContratoTest {

    private static final Path FAKES = Path.of("src/test/java/pensamiento/testutil/fakes");
    private static final Path CONTRATOS = Path.of("src/test/java/pensamiento/contrato");

    @Test
    void todo_fake_tiene_su_contract_test() throws IOException {
        List<String> fakes;
        try (Stream<Path> archivos = Files.list(FAKES)) {
            fakes = archivos.map(p -> p.getFileName().toString())
                    .filter(n -> n.startsWith("Fake") && n.endsWith(".java"))
                    .map(n -> n.substring(0, n.length() - ".java".length()))
                    .sorted().toList();
        }
        assertThat(fakes).as("debe haber al menos un Fake").isNotEmpty();
        List<String> sinContrato = fakes.stream()
                .filter(fake -> !Files.exists(CONTRATOS.resolve(fake + "ContractTest.java")))
                .toList();
        assertThat(sinContrato).as("Fakes sin Fake*ContractTest en src/test/java/pensamiento/contrato").isEmpty();
    }

    @Test
    void todo_contract_test_de_fake_tiene_su_real_contract_it_gated() throws IOException {
        List<String> puertos;
        try (Stream<Path> archivos = Files.list(CONTRATOS)) {
            puertos = archivos.map(p -> p.getFileName().toString())
                    .filter(n -> n.startsWith("Fake") && n.endsWith("ContractTest.java"))
                    .map(n -> n.substring("Fake".length(), n.length() - "ContractTest.java".length()))
                    .sorted().toList();
        }
        for (String puerto : puertos) {
            Path real = CONTRATOS.resolve("real").resolve("Real" + puerto + "ContractIT.java");
            assertThat(real).as("falta el lado Real del contrato de " + puerto).exists();
            assertThat(Files.readString(real)).as(real + " debe estar gated por CONTRACT_REAL").contains("CONTRACT_REAL");
        }
    }
}
